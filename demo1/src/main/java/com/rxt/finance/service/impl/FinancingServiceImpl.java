package com.rxt.finance.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.rxt.common.PageResult;
import com.rxt.common.ResultCode;
import com.rxt.common.constant.DictConst;
import com.rxt.common.constant.UserConst;
import com.rxt.common.exception.BizException;
import com.rxt.common.exception.ResourceNotFoundException;
import com.rxt.entity.DictItem;
import com.rxt.entity.User;
import com.rxt.finance.constant.FinancingConst;
import com.rxt.finance.dto.CreateGroupDTO;
import com.rxt.finance.dto.FinancingApplyDTO;
import com.rxt.finance.dto.JoinGroupDTO;
import com.rxt.finance.entity.BankApproval;
import com.rxt.finance.entity.FinancingApply;
import com.rxt.finance.entity.GuaranteeGroup;
import com.rxt.finance.entity.GuaranteeMember;
import com.rxt.finance.mapper.BankApprovalMapper;
import com.rxt.finance.mapper.FinancingApplyMapper;
import com.rxt.finance.mapper.GuaranteeGroupMapper;
import com.rxt.finance.mapper.GuaranteeMemberMapper;
import com.rxt.finance.service.FinancingService;
import com.rxt.finance.vo.BankApprovalVO;
import com.rxt.finance.vo.FinancingApplyDetailVO;
import com.rxt.finance.vo.FinancingApplyResultVO;
import com.rxt.finance.vo.FinancingApplyVO;
import com.rxt.finance.vo.GroupMemberVO;
import com.rxt.finance.vo.GuaranteeGroupDetailVO;
import com.rxt.finance.vo.GuaranteeGroupVO;
import com.rxt.mapper.DictItemMapper;
import com.rxt.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 融资服务实现 —— M4。
 *
 * <p>（状态机全貌见 {@link FinancingService} 的类注释。这里只讲实现上的三处关键取舍。）
 *
 * <h3>一、为什么所有"改小组"的操作都必须经过 {@link #syncGroupState}？</h3>
 * 小组的 {@code current_members} 和 {@code total_amount} 是从成员表汇总出来的冗余列。
 * 如果"加入"自己 +1、"取消"自己 -1，那么任何一次异常/并发/漏改，
 * 这两个数字就会永久偏离真值，而且<b>没有任何地方能发现</b>（因为没人会去跟成员表对账）。
 * 这里的做法是：任何成员变动之后，都<b>重新从成员表算一遍</b>再写回。
 * 多一次查询，换掉一整类静默不一致。
 *
 * <h3>二、为什么"成型"的判断放在 syncGroupState 里而不是 join 里？</h3>
 * 因为"成型"和"退回招募中"是同一个判断的两面（人数 >= 3 还是 < 3）。
 * 如果只在 join 里判断成型、在 cancel 里判断退回，两份代码迟早会分叉
 * （比如以后加了"管理员移除成员"，又得写第三份）。
 * 放在一个方法里，进和退都走同一条路，行为天然对称。
 *
 * <h3>三、为什么取消申请要<b>删掉</b>成员记录，而不是标记 join_status=2？</h3>
 * 因为 {@code join_status} 里的 2（已拒绝）语义是"被拒绝加入"，
 * 而"自己主动退出"是另一回事。混用会让银行端看到"这个人是被拒绝进来的"，产生错误印象。
 * 而且 t_guarantee_member 上有 {@code uk_gm_group_user} 唯一索引，
 * 留着旧记录会让这个人<b>再也无法重新加入同一小组</b>。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FinancingServiceImpl implements FinancingService {

    /** 每页条数上限 */
    private static final int MAX_PAGE_SIZE = 200;

    /**
     * "可加入小组"接口内部取数上限。
     *
     * <p>契约里这个接口返回的是<b>数组而不是分页对象</b>，所以它没有 size 参数。
     * 但完全不设上限是不行的 —— 万一将来有 5000 个招募中的小组，
     * 一次全查出来会把内存和响应体都撑爆。
     * 取 100 并在接口注释里说明"只返回最近 100 个"，既守住契约形状又有明确边界。
     */
    private static final long MAX_AVAILABLE_GROUPS = 100L;

    /**
     * 契约（OpenAPI）里定义的枚举值，与数据库字典编码并存。
     *
     * <p>见 {@code FinancingApplyDTO} 类注释里对"契约枚举 vs 字典编码"冲突的完整说明。
     * 这里保留两套白名单，是为了让"前端按契约写"和"前端按字典写"都能跑通。
     */
    private static final Set<String> USAGE_CONTRACT_ENUM =
            Set.of("material", "expand", "facility", "other");
    private static final Set<String> INDUSTRY_CONTRACT_ENUM =
            Set.of("planting", "breeding", "process");
    private static final Set<String> LOAN_TERM_CONTRACT_ENUM =
            Set.of("6", "12", "24", "36");

    private final FinancingApplyMapper applyMapper;
    private final GuaranteeGroupMapper groupMapper;
    private final GuaranteeMemberMapper memberMapper;
    private final BankApprovalMapper approvalMapper;
    private final DictItemMapper dictItemMapper;
    private final UserMapper userMapper;

    // ==================================================================================
    // 1. 提交融资申请
    // ==================================================================================
    @Override
    @Transactional(rollbackFor = Exception.class)
    public FinancingApplyResultVO apply(Long userId, FinancingApplyDTO dto) {
        User user = requireUser(userId);
        // 表4 权限矩阵：融资申请是农户（含买家，两者共用 farmer 角色）的功能。
        // 银行的审批入口另有 /api/bank/**，专家的助力入口在 M5，管理员不参与融资。
        // 这一条不能只靠 SecurityConfig 的 URL 规则挡住 —— 那样"谁能申请"就散落在配置里了，
        // 写在业务里才能表达"为什么"。
        if (!UserConst.ROLE_FARMER.equals(user.getRole())) {
            throw new BizException(ResultCode.FINANCING_ROLE_DENIED,
                    "当前身份不能发起融资申请，请使用农户账号");
        }

        assertNoActiveApply(userId);

        // 字典校验：把前端传的编码规范化成字典里的规范写法，同时挡掉乱填的值
        String usage = assertDictCode(DictConst.TYPE_FUND_USAGE, dto.getUsage(),
                "资金用途", USAGE_CONTRACT_ENUM);
        String industry = assertDictCode(DictConst.TYPE_INDUSTRY, dto.getIndustry(),
                "所属行业", INDUSTRY_CONTRACT_ENUM);
        String loanTerm = assertDictCode(DictConst.TYPE_LOAN_TERM, dto.getLoanTerm(),
                "贷款期限", LOAN_TERM_CONTRACT_ENUM);

        FinancingApply apply = new FinancingApply();
        apply.setUserId(userId);
        apply.setAmount(dto.getAmount());
        apply.setUsage(usage);
        apply.setIndustry(industry);
        apply.setProvince(dto.getProvince().trim());
        apply.setCity(dto.getCity().trim());
        apply.setDescription(dto.getDescription().trim());
        apply.setLoanTerm(loanTerm);
        // 初始状态由代码决定，不接受前端传 —— DTO 里根本没有 status 字段
        apply.setStatus(FinancingConst.APPLY_PENDING_MATCH);
        apply.setDeleted(0);
        applyMapper.insert(apply);

        log.info("用户 {} 提交融资申请 {}：金额 {} 元，行业 {}，地区 {}/{}",
                userId, apply.getId(), apply.getAmount(), industry, apply.getProvince(), apply.getCity());

        // 提交后立刻匹配同行业 + 同省的可加入小组，让前端能直接提示"为您匹配到 N 个小组"
        List<GuaranteeGroupVO> matched = matchGroups(industry, apply.getProvince(), userId);
        return FinancingApplyResultVO.of(apply.getId(), matched);
    }

    /**
     * 同一用户同时只能有一条进行中的申请。
     *
     * <p>为什么不允许并存？因为"进行中"到底算哪一条，在联保和审批环节都会产生歧义：
     * 加入小组时要用哪条申请？银行审批通过后按哪条放款？
     * 与其到处做"取最新一条"的隐式约定，不如从源头上限制为一条。
     * 已经终态（通过/拒绝/取消）的申请不占额度，用户可以再发起新的。
     */
    private void assertNoActiveApply(Long userId) {
        Long activeCount = applyMapper.selectCount(new LambdaQueryWrapper<FinancingApply>()
                .eq(FinancingApply::getUserId, userId)
                .in(FinancingApply::getStatus, FinancingConst.APPLY_ACTIVE_STATUSES));
        if (activeCount != null && activeCount > 0) {
            throw new BizException(ResultCode.FINANCING_EXISTS,
                    "您已有正在处理中的融资申请，请等它结束后再提交新的申请");
        }
    }

    // ==================================================================================
    // 2. 我的融资申请列表
    // ==================================================================================
    @Override
    public PageResult<FinancingApplyVO> myApplies(Long userId, Integer status, int page, int size) {
        Page<FinancingApply> pageParam = new Page<>(normalizePage(page), normalizeSize(size));
        LambdaQueryWrapper<FinancingApply> wrapper = new LambdaQueryWrapper<FinancingApply>()
                .eq(FinancingApply::getUserId, userId)
                .eq(status != null, FinancingApply::getStatus, status)
                .orderByDesc(FinancingApply::getCreateTime);

        Page<FinancingApply> result = applyMapper.selectPage(pageParam, wrapper);
        return PageResult.of(result, FinancingApplyVO::from);
    }

    // ==================================================================================
    // 3. 融资申请详情
    // ==================================================================================
    @Override
    public FinancingApplyDetailVO detail(Long userId, String role, Long applyId) {
        FinancingApply apply = requireApply(applyId);

        // 越权控制：默认只有本人能看；admin / bank 因为审批需要也放行。
        // 这里没写 @PreAuthorize，因为规则是"本人 OR 特定角色"，
        // 用表达式写会变成 "hasRole('admin') or hasRole('bank') or #userId == principal.xxx"，
        // 又长又难读，还不如在 Service 里用普通 if 表达清楚。
        boolean isOwner = Objects.equals(apply.getUserId(), userId);
        boolean isStaff = UserConst.ROLE_ADMIN.equals(role) || UserConst.ROLE_BANK.equals(role);
        if (!isOwner && !isStaff) {
            throw new BizException(ResultCode.FORBIDDEN, "只能查看自己的融资申请");
        }

        FinancingApplyDetailVO vo = FinancingApplyDetailVO.from(apply);

        // 小组摘要（不含成员名单 —— 那走 /financing/group/{id}）
        if (apply.getGroupId() != null) {
            GuaranteeGroup group = groupMapper.selectById(apply.getGroupId());
            vo.setGroup(GuaranteeGroupVO.from(group));
            // 审批结果挂在小组上（银行审的是小组），所以要用 groupId 去查审批记录。
            // 理论上一个小组只有一条记录，这里仍按时间倒序取第一条，防御历史脏数据。
            BankApproval approval = approvalMapper.selectOne(new LambdaQueryWrapper<BankApproval>()
                    .eq(BankApproval::getGroupId, apply.getGroupId())
                    .orderByDesc(BankApproval::getApprovedAt)
                    .last("LIMIT 1"));
            vo.setApproval(BankApprovalVO.from(approval));
        }
        return vo;
    }

    // ==================================================================================
    // 4. 取消申请
    // ==================================================================================
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void cancel(Long userId, Long applyId) {
        FinancingApply apply = requireApply(applyId);
        if (!Objects.equals(apply.getUserId(), userId)) {
            throw new BizException(ResultCode.FORBIDDEN, "只能取消自己的融资申请");
        }

        Long groupId = apply.getGroupId();
        GuaranteeGroup group = groupId == null ? null : groupMapper.selectById(groupId);

        // ⚠️ 顺序很关键：**先判"是不是被银行处理过了"，再判通用的"申请已结束"**。
        // 因为银行审批通过/拒绝后申请状态会变成 3/4 —— 那也是"已结束"，
        // 如果先走通用分支，用户拿到的就是 1001（"该申请已结束（已通过），无法取消"），
        // 语义上没错但不够具体。而 3003 是专门为这个场景定义的码，
        // 前端可以据此把文案说成"该申请已被银行审批，请联系客户经理"，指引性完全不同。
        // 原则：**更具体的原因优先于更笼统的原因。**
        if (group != null && FinancingConst.GROUP_HANDLED_STATUSES.contains(group.getStatus())) {
            throw new BizException(ResultCode.FINANCING_HANDLED,
                    "所在联保小组已被银行审批（" + FinancingConst.groupStatusName(group.getStatus())
                            + "），申请无法取消");
        }

        if (!FinancingConst.isApplyActive(apply.getStatus())) {
            throw new BizException(ResultCode.PARAM_ERROR, "该申请已结束（"
                    + FinancingConst.applyStatusName(apply.getStatus()) + "），无法取消");
        }

        // 先把人从小组里摘掉（物理删除，理由见类注释第三条）
        if (groupId != null) {
            memberMapper.delete(new LambdaQueryWrapper<GuaranteeMember>()
                    .eq(GuaranteeMember::getGroupId, groupId)
                    .eq(GuaranteeMember::getUserId, userId));
        }

        applyMapper.update(null, new LambdaUpdateWrapper<FinancingApply>()
                .eq(FinancingApply::getId, applyId)
                .set(FinancingApply::getStatus, FinancingConst.APPLY_CANCELLED)
                .set(FinancingApply::getGroupId, null));

        // 人少了，小组可能从"成型"退回"招募中"，组内其它人的申请状态也要跟着回退
        if (groupId != null) {
            syncGroupState(groupId);
        }

        log.info("用户 {} 取消了融资申请 {}", userId, applyId);
    }

    // ==================================================================================
    // 5. 可加入的联保小组
    // ==================================================================================
    @Override
    public List<GuaranteeGroupVO> availableGroups(Long userId, String industry, String province) {
        return matchGroups(industry, province, userId);
    }

    /**
     * 按"行业 + 省份"匹配可加入的小组 —— {@link #apply} 和 {@link #availableGroups} 共用。
     *
     * <p><b>只匹配"招募中(status=0)"的小组</b>，不匹配已成型入池的：
     * 往一个材料已提交银行的小组里加人，会让银行已核定的额度依据失效
     * （银行审的是"这 3 个人"的联保结构，凭空变成 4 个人就是另一笔业务了）。
     *
     * <p>行业和省份是<b>强匹配</b>（不匹配就不返回），因为联保的核心是
     * "同一区域、同一产业，风险同源、便于互相监督"。
     * 这也是 t_guarantee_group 要存 industry/province/city 的原因。
     */
    private List<GuaranteeGroupVO> matchGroups(String industry, String province, Long excludeUserId) {
        LambdaQueryWrapper<GuaranteeGroup> wrapper = new LambdaQueryWrapper<GuaranteeGroup>()
                .eq(GuaranteeGroup::getStatus, FinancingConst.GROUP_RECRUITING)
                // 未满员。用 lt 而不是 le：等于上限时就已经满了
                .lt(GuaranteeGroup::getCurrentMembers, FinancingConst.MAX_MEMBERS)
                .eq(StringUtils.hasText(industry), GuaranteeGroup::getIndustry, industry)
                .eq(StringUtils.hasText(province), GuaranteeGroup::getProvince, province)
                // 人数多的排前面：更容易成团，农户点进去的成功率更高
                .orderByDesc(GuaranteeGroup::getCurrentMembers)
                .orderByDesc(GuaranteeGroup::getCreateTime);

        Page<GuaranteeGroup> limit = new Page<>(1, MAX_AVAILABLE_GROUPS);
        List<GuaranteeGroup> groups = groupMapper.selectPage(limit, wrapper).getRecords();
        if (groups.isEmpty()) {
            return List.of();
        }

        // 排除"自己已经在里面"的小组：前端要用这个列表做"立即加入"的引导，
        // 把已经在的小组也列出来会让用户点进去才发现"重复加入"。
        Set<Long> joinedGroupIds = excludeUserId == null ? Set.of() : myJoinedGroupIds(excludeUserId);
        return groups.stream()
                .filter(g -> !joinedGroupIds.contains(g.getId()))
                .map(GuaranteeGroupVO::from)
                .toList();
    }

    /** 当前用户已加入（未退出）的小组 id 集合 */
    private Set<Long> myJoinedGroupIds(Long userId) {
        List<GuaranteeMember> mine = memberMapper.selectList(new LambdaQueryWrapper<GuaranteeMember>()
                .eq(GuaranteeMember::getUserId, userId)
                .eq(GuaranteeMember::getJoinStatus, FinancingConst.MEMBER_JOINED));
        return mine.stream().map(GuaranteeMember::getGroupId).collect(Collectors.toSet());
    }

    // ==================================================================================
    // 6. 联保小组详情
    // ==================================================================================
    @Override
    public GuaranteeGroupDetailVO groupDetail(Long groupId) {
        GuaranteeGroup group = requireGroup(groupId);
        List<GuaranteeMember> members = memberMapper.selectList(new LambdaQueryWrapper<GuaranteeMember>()
                .eq(GuaranteeMember::getGroupId, groupId)
                .orderByAsc(GuaranteeMember::getJoinedAt)
                .orderByAsc(GuaranteeMember::getId));
        return GuaranteeGroupDetailVO.from(group, toMemberVOs(members));
    }

    /**
     * 成员实体列表 → VO 列表，并批量回填成员姓名。
     *
     * <p>用一条 {@code selectBatchIds} 把这一批成员的用户全捞出来再做 Map，
     * 而不是在循环里逐个 {@code selectById} ——
     * 10 人小组的差别是"1 次查询" vs "10 次查询"，这就是 N+1 问题。
     */
    private List<GroupMemberVO> toMemberVOs(List<GuaranteeMember> members) {
        if (members == null || members.isEmpty()) {
            return List.of();
        }
        List<GroupMemberVO> vos = members.stream().map(GroupMemberVO::from).collect(Collectors.toList());

        Set<Long> userIds = members.stream()
                .map(GuaranteeMember::getUserId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        if (userIds.isEmpty()) {
            return vos;
        }
        Map<Long, String> nameMap = userMapper.selectBatchIds(userIds).stream()
                .collect(Collectors.toMap(User::getId, this::displayName, (a, b) -> a));
        vos.forEach(vo -> vo.setUsername(nameMap.getOrDefault(vo.getUserId(), "未知用户")));
        return vos;
    }

    /** 姓名兜底：t_user.name 允许为空，空的时候不能让前端显示空白 */
    private String displayName(User user) {
        return StringUtils.hasText(user.getName()) ? user.getName() : "未命名农户";
    }

    // ==================================================================================
    // 7. 申请加入联保小组
    // ==================================================================================
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void join(Long userId, JoinGroupDTO dto) {
        GuaranteeGroup group = requireGroup(dto.getGroupId());
        FinancialAssert.assertGroupJoinable(group);

        FinancingApply apply = requireApply(dto.getApplyId());
        // 越权：不能拿别人的申请去凑小组
        if (!Objects.equals(apply.getUserId(), userId)) {
            throw new BizException(ResultCode.FORBIDDEN, "只能用自己的融资申请加入联保小组");
        }

        // ⚠️ 顺序很关键：**先查"是不是已经在这个组里"，再查申请的通用状态**。
        // 反过来写的话，第二次点"加入"会因为申请状态已经变成"已匹配"而命中下面那条
        // 3007 通用提示（"该融资申请当前状态为「已匹配」…"）——技术上没错，
        // 但对用户来说是废话：他需要知道的是"你已经在这个组里了"，
        // 而不是申请状态的枚举名。更具体的错误信息优先，是很实际的一条易用性原则。
        Long exists = memberMapper.selectCount(new LambdaQueryWrapper<GuaranteeMember>()
                .eq(GuaranteeMember::getGroupId, group.getId())
                .eq(GuaranteeMember::getUserId, userId));
        if (exists != null && exists > 0) {
            throw new BizException(ResultCode.PARAM_ERROR, "您已在该联保小组中，无需重复加入");
        }

        // 申请必须处于"待匹配"：已加入别的小组、或已在走审批的申请都不能再挪
        if (!Objects.equals(apply.getStatus(), FinancingConst.APPLY_PENDING_MATCH)
                || apply.getGroupId() != null) {
            throw new BizException(ResultCode.APPLY_ALREADY_GROUPED,
                    "该融资申请当前状态为「" + FinancingConst.applyStatusName(apply.getStatus())
                            + "」，不能再次加入联保小组");
        }

        insertMember(group, apply, userId);
        bindApplyToGroup(apply, group.getId());
        syncGroupState(group.getId());

        log.info("用户 {} 带着申请 {} 加入了联保小组 {}（{}）", userId, apply.getId(),
                group.getId(), group.getGroupNo());
    }

    /** 插入一条"已加入"的成员记录（快照申请信息，理由见 GuaranteeMember 类注释） */
    private void insertMember(GuaranteeGroup group, FinancingApply apply, Long userId) {
        GuaranteeMember member = new GuaranteeMember();
        member.setGroupId(group.getId());
        member.setUserId(userId);
        member.setApplyId(apply.getId());
        member.setApplyAmount(apply.getAmount());
        member.setIndustry(apply.getIndustry());
        member.setProvince(apply.getProvince());
        member.setCity(apply.getCity());
        member.setJoinStatus(FinancingConst.MEMBER_JOINED);
        member.setJoinedAt(LocalDateTime.now());
        memberMapper.insert(member);
    }

    /** 把申请挂到小组上并推进为"已匹配" */
    private void bindApplyToGroup(FinancingApply apply, Long groupId) {
        applyMapper.update(null, new LambdaUpdateWrapper<FinancingApply>()
                .eq(FinancingApply::getId, apply.getId())
                .set(FinancingApply::getGroupId, groupId)
                .set(FinancingApply::getStatus, FinancingConst.APPLY_MATCHED));
    }

    // ==================================================================================
    // 8. 发起新联保小组
    // ==================================================================================
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createGroup(Long userId, CreateGroupDTO dto) {
        User user = requireUser(userId);
        if (!UserConst.ROLE_FARMER.equals(user.getRole())) {
            throw new BizException(ResultCode.FINANCING_ROLE_DENIED,
                    "当前身份不能发起联保小组，请使用农户账号");
        }

        String industry = assertDictCode(DictConst.TYPE_INDUSTRY, dto.getIndustry(),
                "所属行业", INDUSTRY_CONTRACT_ENUM);

        // 必须有一条"待匹配"的申请，且自动入组 —— 理由见接口注释
        FinancingApply apply = applyMapper.selectOne(new LambdaQueryWrapper<FinancingApply>()
                .eq(FinancingApply::getUserId, userId)
                .eq(FinancingApply::getStatus, FinancingConst.APPLY_PENDING_MATCH)
                .orderByDesc(FinancingApply::getCreateTime)
                .last("LIMIT 1"));
        if (apply == null) {
            throw new BizException(ResultCode.PARAM_ERROR,
                    "请先提交融资申请，再发起联保小组（小组的成员就是一组融资申请）");
        }

        GuaranteeGroup group = new GuaranteeGroup();
        group.setCreatorId(userId);
        group.setIndustry(industry);
        group.setProvince(dto.getProvince().trim());
        group.setCity(dto.getCity().trim());
        group.setTotalAmount(BigDecimal.ZERO);
        // 先按"还没人"插入，真正的计数由 syncGroupState 从成员表算出来
        group.setCurrentMembers(0);
        group.setStatus(FinancingConst.GROUP_RECRUITING);
        groupMapper.insert(group);

        // 编号带自增主键，所以只能插入后回填。这样保证全局唯一
        // （"日期 + 全局唯一 id" 一定不重复），不需要查当天流水号、也没有并发撞号的窗口
        String groupNo = "LB-" + LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE)
                + "-" + String.format("%06d", group.getId());
        groupMapper.update(null, new LambdaUpdateWrapper<GuaranteeGroup>()
                .eq(GuaranteeGroup::getId, group.getId())
                .set(GuaranteeGroup::getGroupNo, groupNo));
        group.setGroupNo(groupNo);

        // 发起人自动成为第 1 个成员
        insertMember(group, apply, userId);
        bindApplyToGroup(apply, group.getId());
        syncGroupState(group.getId());

        log.info("用户 {} 发起联保小组 {}（{}），行业 {}，地区 {}/{}",
                userId, group.getId(), groupNo, industry, group.getProvince(), group.getCity());
        return group.getId();
    }

    // ==================================================================================
    // 核心：小组聚合数据 + 状态机的唯一入口
    // ==================================================================================

    /**
     * 重新计算小组的"人数 / 总额 / 状态"，并联动推进组内申请的进度。
     *
     * <p><b>调用时机</b>：任何成员变动之后（加入小组、退出/取消申请）。
     * <b>别处不许改这两列。</b>
     *
     * <p>它做三件事：
     * <ol>
     *   <li><b>重算冗余列</b>：从 t_guarantee_member 重新 SUM/COUNT，而不是 ±1。
     *       ±1 看起来省一次查询，但只要有一次漏改（异常、新加的分支忘了调），
     *       数字就永久错，而且没有任何信号。重算则是幂等的：调 10 次结果一样；</li>
     *   <li><b>修正小组状态</b>：人数 >= 3 → 已匹配（成型入池）；&lt; 3 → 回到招募中。
     *       已经被银行处理过（已通过/已拒绝）的小组跳过，不能因为有人退出就改掉审批结论；</li>
     *   <li><b>联动组内申请状态</b>：小组成型时把组内申请从"已匹配"推进到"待银行审批"，
     *       退回招募中时再退回来。这样农户在"我的融资申请"页看到的进度，
     *       永远和他所在小组的真实进度一致。</li>
     * </ol>
     */
    private void syncGroupState(Long groupId) {
        GuaranteeGroup group = groupMapper.selectById(groupId);
        if (group == null) {
            return;
        }

        List<GuaranteeMember> joined = memberMapper.selectList(new LambdaQueryWrapper<GuaranteeMember>()
                .eq(GuaranteeMember::getGroupId, groupId)
                .eq(GuaranteeMember::getJoinStatus, FinancingConst.MEMBER_JOINED));

        int memberCount = joined.size();
        BigDecimal totalAmount = joined.stream()
                .map(GuaranteeMember::getApplyAmount)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        groupMapper.update(null, new LambdaUpdateWrapper<GuaranteeGroup>()
                .eq(GuaranteeGroup::getId, groupId)
                .set(GuaranteeGroup::getCurrentMembers, memberCount)
                .set(GuaranteeGroup::getTotalAmount, totalAmount));

        // 已审批的小组不再参与状态回退/推进 —— 审批结论不可撤回
        if (FinancingConst.GROUP_HANDLED_STATUSES.contains(group.getStatus())) {
            log.debug("小组 {} 已被银行处理（{}），跳过状态同步", groupId, group.getStatus());
            return;
        }

        boolean formed = memberCount >= FinancingConst.MIN_MEMBERS;
        int newGroupStatus = formed ? FinancingConst.GROUP_MATCHED : FinancingConst.GROUP_RECRUITING;
        if (!Objects.equals(group.getStatus(), newGroupStatus)) {
            groupMapper.update(null, new LambdaUpdateWrapper<GuaranteeGroup>()
                    .eq(GuaranteeGroup::getId, groupId)
                    .set(GuaranteeGroup::getStatus, newGroupStatus));
            log.info("小组 {} 状态 {} → {}（{} 人）", groupId,
                    FinancingConst.groupStatusName(group.getStatus()),
                    FinancingConst.groupStatusName(newGroupStatus), memberCount);
        }

        // 联动申请状态。两个方向都排除终态(3/4/5)：
        // 已经通过/拒绝/取消的申请不应该被小组的波动影响
        if (formed) {
            int changed = applyMapper.update(null, new LambdaUpdateWrapper<FinancingApply>()
                    .eq(FinancingApply::getGroupId, groupId)
                    .in(FinancingApply::getStatus,
                            FinancingConst.APPLY_PENDING_MATCH, FinancingConst.APPLY_MATCHED)
                    .set(FinancingApply::getStatus, FinancingConst.APPLY_PENDING_BANK));
            if (changed > 0) {
                log.info("小组 {} 已成型，{} 条申请推进为「待银行审批」", groupId, changed);
            }
        } else {
            int changed = applyMapper.update(null, new LambdaUpdateWrapper<FinancingApply>()
                    .eq(FinancingApply::getGroupId, groupId)
                    .eq(FinancingApply::getStatus, FinancingConst.APPLY_PENDING_BANK)
                    .set(FinancingApply::getStatus, FinancingConst.APPLY_MATCHED));
            if (changed > 0) {
                log.info("小组 {} 人数不足 {}，{} 条申请回退为「已匹配」", groupId,
                        FinancingConst.MIN_MEMBERS, changed);
            }
        }
    }

    // ==================================================================================
    // 校验与查表辅助
    // ==================================================================================

    private User requireUser(Long userId) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new ResourceNotFoundException("用户不存在");
        }
        return user;
    }

    private FinancingApply requireApply(Long applyId) {
        FinancingApply apply = applyMapper.selectById(applyId);
        if (apply == null) {
            throw new ResourceNotFoundException("融资申请不存在");
        }
        return apply;
    }

    private GuaranteeGroup requireGroup(Long groupId) {
        GuaranteeGroup group = groupMapper.selectById(groupId);
        if (group == null) {
            throw new ResourceNotFoundException("联保小组不存在");
        }
        return group;
    }

    /**
     * 校验某个编码是合法的字典项，并返回<b>规范写法</b>。
     *
     * <p>三级匹配，从严格到宽松：
     * <ol>
     *   <li>精确等于某个启用的字典编码（如 {@code SEED}）—— 直接用；</li>
     *   <li>忽略大小写等于某个字典编码（如传来 {@code seed}）—— 规范化为字典里的写法。
     *       这一级很必要：前端下拉框的值来自 {@code GET /api/admin/dict/{type}}，
     *       但如果哪个同学用输入框手填，大小写一定会飘；</li>
     *   <li>命中契约（OpenAPI）里定义的枚举（如 {@code material}）—— 保留原值。
     *       这一级是为了兼容"前端按契约写"的情况，见 {@link FinancingApplyDTO} 的注释。</li>
     * </ol>
     *
     * <p>都不中就抛错，并<b>把允许的值列出来</b> —— 前端同学看到"资金用途取值不合法"，
     * 还是不知道该填什么；列出候选值他就能立刻改对。这是接口设计中
     * "错误信息要可执行"的原则。
     */
    private String assertDictCode(String type, String code, String label, Set<String> contractEnum) {
        if (!StringUtils.hasText(code)) {
            throw new BizException(ResultCode.PARAM_ERROR, label + "不能为空");
        }
        String value = code.trim();

        List<DictItem> items = dictItemMapper.selectList(new LambdaQueryWrapper<DictItem>()
                .eq(DictItem::getType, type)
                .eq(DictItem::getEnabled, DictConst.ENABLED)
                .orderByAsc(DictItem::getSort));

        // 一级：精确匹配
        for (DictItem item : items) {
            if (item.getCode().equals(value)) {
                return item.getCode();
            }
        }
        // 二级：忽略大小写 → 规范化为字典里的写法
        for (DictItem item : items) {
            if (item.getCode().equalsIgnoreCase(value)) {
                return item.getCode();
            }
        }
        // 三级：契约枚举
        for (String candidate : contractEnum) {
            if (candidate.equalsIgnoreCase(value)) {
                return candidate.toLowerCase();
            }
        }

        List<String> allowed = new ArrayList<>();
        items.forEach(i -> allowed.add(i.getCode()));
        allowed.addAll(contractEnum);
        throw new BizException(ResultCode.PARAM_ERROR,
                label + "取值不合法，可选：" + String.join(" / ", allowed));
    }

    private int normalizePage(int page) {
        return page < 1 ? 1 : page;
    }

    private int normalizeSize(int size) {
        if (size < 1) {
            return 10;
        }
        return Math.min(size, MAX_PAGE_SIZE);
    }

    /**
     * 小组"可加入"的规则，集中在一个静态内部类里。
     *
     * <p>为什么不写成 {@code FinancingServiceImpl} 的私有方法？
     * 因为这些规则<b>将来会被别处复用</b>（比如银行审批时校验小组是否合格、
     * 或管理员在后台手动把某人加进小组）。放在静态内部类里，
     * 谁都能调用，又不会让主类的方法列表继续膨胀。
     */
    static final class FinancialAssert {

        private FinancialAssert() {
        }

        /**
         * 校验小组当前是否可加入。
         *
         * <p>两条规则各自对应一个不同的错误码，前端可以据此给不同的引导：
         * <ul>
         *   <li>招募中以外 → 3005（提示"该小组已在审批流程中"，引导去看别的小组）；</li>
         *   <li>已满员 → 3002（提示"换一个小组"）。</li>
         * </ul>
         */
        static void assertGroupJoinable(GuaranteeGroup group) {
            if (!Objects.equals(group.getStatus(), FinancingConst.GROUP_RECRUITING)) {
                throw new BizException(ResultCode.GROUP_NOT_OPEN,
                        "该联保小组当前状态为「" + FinancingConst.groupStatusName(group.getStatus())
                                + "」，不能再加入");
            }
            int members = group.getCurrentMembers() == null ? 0 : group.getCurrentMembers();
            if (members >= FinancingConst.MAX_MEMBERS) {
                throw new BizException(ResultCode.GROUP_FULL,
                        "该联保小组已满员（上限 " + FinancingConst.MAX_MEMBERS + " 人）");
            }
        }
    }
}
