package com.rxt.finance.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.rxt.common.ResultCode;
import com.rxt.common.constant.AuditConst;
import com.rxt.common.constant.UserConst;
import com.rxt.common.exception.BizException;
import com.rxt.common.exception.ResourceNotFoundException;
import com.rxt.entity.User;
import com.rxt.finance.config.MockConfig;
import com.rxt.finance.constant.FinancingConst;
import com.rxt.finance.dto.BankApproveDTO;
import com.rxt.finance.entity.BankApproval;
import com.rxt.finance.entity.FinancingApply;
import com.rxt.finance.entity.GuaranteeGroup;
import com.rxt.finance.entity.GuaranteeMember;
import com.rxt.finance.mapper.BankApprovalMapper;
import com.rxt.finance.mapper.FinancingApplyMapper;
import com.rxt.finance.mapper.GuaranteeGroupMapper;
import com.rxt.finance.mapper.GuaranteeMemberMapper;
import com.rxt.finance.service.BankService;
import com.rxt.finance.vo.BankGroupVO;
import com.rxt.finance.vo.BankStatsVO;
import com.rxt.finance.vo.GroupMemberVO;
import com.rxt.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 融资管理服务实现 —— M6。
 *
 * <p>这个类最需要说清楚的是 {@link #approve} 里的<b>一次审批改三处状态</b>：
 * <pre>
 *   t_bank_approval   插入一行审批记录
 *   t_guarantee_group status → 3 已通过 / 4 已拒绝，回填 bank_id
 *   t_financing_apply 组内所有申请 status → 3 / 4，回填 bank_id
 * </pre>
 * 三者必须同时成立。如果只改了小组、漏了申请，农户在"我的融资申请"里会一直显示
 * "待银行审批" —— 而他所在的小组明明已经批了。所以整个方法包在一个事务里，
 * 任何一步失败全部回滚。
 *
 * <p>另一个关键是<b>审批不能重复</b>（审批结果不可撤回，与专家审核、文章审核同一条原则）。
 * 这里用两层判断：小组状态是不是终态、以及 t_bank_approval 里有没有这个 group 的记录。
 * 前者是业务判断，后者是数据兜底 —— 万一小组状态因为某个 bug 没改成功，
 * 有审批记录这一条仍能挡住第二次审批，避免一个小组批出两个相反的结论。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BankServiceImpl implements BankService {

    private final GuaranteeGroupMapper groupMapper;
    private final GuaranteeMemberMapper memberMapper;
    private final FinancingApplyMapper applyMapper;
    private final BankApprovalMapper approvalMapper;
    private final UserMapper userMapper;
    private final MockConfig mockConfig;

    // ==================================================================================
    // 1. 联保小组列表
    // ==================================================================================
    @Override
    public List<BankGroupVO> groupList(Integer status) {
        LambdaQueryWrapper<GuaranteeGroup> wrapper = new LambdaQueryWrapper<GuaranteeGroup>()
                // status 不传时用 ge(1) 而不是 in(1,2,3,4)：
                // 语义是"所有已成型的"，将来若新增状态值（比如 5 已放款）也能自动包含进来，
                // 不用回来改这里。这是"按不变量筛选"而不是"按枚举筛选"的好处。
                .ge(status == null, GuaranteeGroup::getStatus, FinancingConst.GROUP_MATCHED)
                .eq(status != null, GuaranteeGroup::getStatus, status)
                // 待处理排前面，其次新的在前 —— 审批员的动线是先处理完老的
                .orderByAsc(GuaranteeGroup::getStatus)
                .orderByDesc(GuaranteeGroup::getCreateTime);

        List<GuaranteeGroup> groups = groupMapper.selectList(wrapper);
        if (groups.isEmpty()) {
            return List.of();
        }

        // 一次性把这一批小组的成员全查出来，再按 groupId 分组回填。
        // 若在循环里对每个小组各查一次成员，就是典型的 N+1（10 个小组 = 11 次查询）。
        List<Long> groupIds = groups.stream().map(GuaranteeGroup::getId).toList();
        List<GuaranteeMember> allMembers = memberMapper.selectList(new LambdaQueryWrapper<GuaranteeMember>()
                .in(GuaranteeMember::getGroupId, groupIds)
                .orderByAsc(GuaranteeMember::getJoinedAt));

        Set<Long> memberUserIds = allMembers.stream()
                .map(GuaranteeMember::getUserId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<Long, String> nameMap = memberUserIds.isEmpty()
                ? Map.of()
                : userMapper.selectBatchIds(memberUserIds).stream()
                .collect(Collectors.toMap(User::getId, this::displayName, (a, b) -> a));

        Map<Long, List<GroupMemberVO>> membersByGroup = new LinkedHashMap<>();
        for (GuaranteeMember member : allMembers) {
            GroupMemberVO vo = GroupMemberVO.from(member);
            vo.setUsername(nameMap.getOrDefault(member.getUserId(), "未知用户"));
            membersByGroup.computeIfAbsent(member.getGroupId(), k -> new ArrayList<>()).add(vo);
        }

        return groups.stream()
                .map(g -> BankGroupVO.from(g, membersByGroup.getOrDefault(g.getId(), List.of())))
                .toList();
    }

    private String displayName(User user) {
        return StringUtils.hasText(user.getName()) ? user.getName() : "未命名农户";
    }

    // ==================================================================================
    // 2. 审批
    // ==================================================================================
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void approve(Long bankUserId, BankApproveDTO dto) {
        requireBankUser(bankUserId);

        GuaranteeGroup group = groupMapper.selectById(dto.getGroupId());
        if (group == null) {
            throw new ResourceNotFoundException("联保小组不存在");
        }

        // ---- 校验审批结果取值。复用 AuditConst：APPROVED / REJECTED 与专家审核、
        //      文章审核是同一套值，没必要为银行再造一套（多一套就多一处不一致的可能）
        if (!AuditConst.isValid(dto.getResult())) {
            throw new BizException(ResultCode.PARAM_ERROR,
                    "审批结果只能是 APPROVED（通过）或 REJECTED（拒绝）");
        }
        boolean approved = AuditConst.isApproved(dto.getResult());

        // ---- 状态前置条件 ----
        if (FinancingConst.GROUP_HANDLED_STATUSES.contains(group.getStatus())) {
            throw new BizException(ResultCode.GROUP_HANDLED,
                    "该小组已于此前审批为「" + FinancingConst.groupStatusName(group.getStatus())
                            + "」，审批结果不可撤回");
        }
        if (!FinancingConst.GROUP_PENDING_STATUSES.contains(group.getStatus())) {
            // 走到这里说明小组还在"招募中"（人数不足 3 人没成型）
            throw new BizException(ResultCode.PARAM_ERROR,
                    "该小组当前为「" + FinancingConst.groupStatusName(group.getStatus())
                            + "」，人数不足 " + FinancingConst.MIN_MEMBERS + " 人，尚不能审批");
        }

        // ---- 数据兜底：一个小组只能有一条审批记录 ----
        Long already = approvalMapper.selectCount(new LambdaQueryWrapper<BankApproval>()
                .eq(BankApproval::getGroupId, group.getId()));
        if (already != null && already > 0) {
            throw new BizException(ResultCode.GROUP_HANDLED, "该小组已有审批记录，不能重复审批");
        }

        // ---- 跨字段规则：拒绝必须给理由 ----
        // 和专家审核、文章审核同一个道理：理由要留给农户看，
        // 否则农户只知道被拒，不知道该改什么。
        if (!approved && !StringUtils.hasText(dto.getRemark())) {
            throw new BizException(ResultCode.PARAM_ERROR, "拒绝时必须填写审批意见");
        }

        // ---- 信用分 / 利率 / 核定额度：Mock 补全 or 必须传全 ----
        Integer creditScore = dto.getCreditScore();
        BigDecimal interestRate = dto.getInterestRate();
        BigDecimal decisionAmount = dto.getDecisionAmount();

        if (mockConfig.isEnabled()) {
            // 演示模式：缺什么补什么。审批员只填"通过/拒绝"就能走完流程
            if (creditScore == null) {
                creditScore = mockConfig.randomCreditScore();
            }
            if (interestRate == null) {
                interestRate = mockConfig.randomInterestRate();
            }
            if (decisionAmount == null) {
                decisionAmount = mockConfig.decideAmount(group.getTotalAmount());
            }
            log.info("Mock 模式：审批小组 {} 自动生成 信用分={} 利率={}% 核定额度={}",
                    group.getId(), creditScore, interestRate, decisionAmount);
        } else {
            if (creditScore == null || interestRate == null) {
                throw new BizException(ResultCode.PARAM_ERROR,
                        "非 Mock 模式下必须填写信用评分与利率");
            }
            if (approved && decisionAmount == null) {
                throw new BizException(ResultCode.PARAM_ERROR, "审批通过时必须填写核定额度");
            }
        }

        // 拒绝时不留核定额度：给 0 会让人误以为"批了 0 元"，null 才是"没有这个结论"
        if (!approved) {
            decisionAmount = null;
        }

        // ---- 1/3：写审批记录 ----
        BankApproval approval = new BankApproval();
        approval.setGroupId(group.getId());
        approval.setBankId(bankUserId);
        approval.setResult(approved ? AuditConst.APPROVED : AuditConst.REJECTED);
        approval.setCreditScore(creditScore);
        approval.setInterestRate(interestRate);
        approval.setDecisionAmount(decisionAmount);
        approval.setRemark(dto.getRemark());
        approval.setApprovedAt(LocalDateTime.now());
        approvalMapper.insert(approval);

        int groupStatus = approved ? FinancingConst.GROUP_APPROVED : FinancingConst.GROUP_REJECTED;
        int applyStatus = approved ? FinancingConst.APPLY_APPROVED : FinancingConst.APPLY_REJECTED;

        // ---- 2/3：改小组状态 ----
        groupMapper.update(null, new LambdaUpdateWrapper<GuaranteeGroup>()
                .eq(GuaranteeGroup::getId, group.getId())
                .set(GuaranteeGroup::getStatus, groupStatus)
                .set(GuaranteeGroup::getBankId, bankUserId));

        // ---- 3/3：改组内所有申请的状态 ----
        // 只改非终态的申请：万一组里有人已经取消了（status=5），
        // 那条申请不应该被银行审批"复活"成已通过。
        int changed = applyMapper.update(null, new LambdaUpdateWrapper<FinancingApply>()
                .eq(FinancingApply::getGroupId, group.getId())
                .in(FinancingApply::getStatus,
                        FinancingConst.APPLY_PENDING_MATCH,
                        FinancingConst.APPLY_MATCHED,
                        FinancingConst.APPLY_PENDING_BANK)
                .set(FinancingApply::getStatus, applyStatus)
                .set(FinancingApply::getBankId, bankUserId));

        log.info("银行 {} 审批小组 {}（{}）：{}，影响 {} 条融资申请",
                bankUserId, group.getId(), group.getGroupNo(),
                approved ? "通过" : "拒绝", changed);
    }

    // ==================================================================================
    // 3. 统计
    // ==================================================================================
    @Override
    public BankStatsVO stats() {
        BankStatsVO vo = new BankStatsVO();

        vo.setTotalGroups(countByStatus(null));
        // 待审批 = 已匹配 + 待银行审批。用 IN 而不是 "= 1"，
        // 这样将来真的启用"2 待银行审批"这个预留状态时不用改这里
        vo.setPendingGroups(groupMapper.selectCount(new LambdaQueryWrapper<GuaranteeGroup>()
                .in(GuaranteeGroup::getStatus, FinancingConst.GROUP_PENDING_STATUSES)));
        vo.setApprovedGroups(countByStatus(FinancingConst.GROUP_APPROVED));
        vo.setRejectedGroups(countByStatus(FinancingConst.GROUP_REJECTED));

        // ⚠️ SUM() 在没有任何匹配行时返回的是 NULL 而不是 0！
        // 直接把结果当 BigDecimal 用会得到 null，前端显示"null 元"。
        // 这里在 SQL 里用 COALESCE 兜一层，再在 Java 侧判空 —— 双保险，
        // 因为"聚合函数返回 null"是 MyBatis-Plus 下最常见的取值坑之一。
        List<Object> sumResult = approvalMapper.selectObjs(
                new QueryWrapper<BankApproval>()
                        .select("COALESCE(SUM(decision_amount), 0) AS totalApprovedAmount")
                        .eq("result", AuditConst.APPROVED));
        BigDecimal total = BigDecimal.ZERO;
        if (!sumResult.isEmpty() && sumResult.get(0) != null) {
            total = new BigDecimal(sumResult.get(0).toString());
        }
        vo.setTotalApprovedAmount(total);

        return vo;
    }

    private Long countByStatus(Integer status) {
        return groupMapper.selectCount(new LambdaQueryWrapper<GuaranteeGroup>()
                .eq(status != null, GuaranteeGroup::getStatus, status));
    }

    // ==================================================================================
    // 私有辅助
    // ==================================================================================

    /**
     * 确认操作人真的是银行角色。
     *
     * <p>{@code SecurityConfig} 里已经有 {@code /api/bank/** → hasRole("bank")}，
     * 为什么还要再查一次库？两个原因：
     * <ol>
     *   <li>JWT 里的 role 是<b>签发那一刻的快照</b>。如果管理员在他登录期间把这个账号的
     *       角色改成了 farmer，Token 仍然带着 bank、以过期的角色放行。查一次库就能挡住
     *       这种"权限已经收回但会话还在"的情况；</li>
     *   <li>审批要往台账里写操作人。写之前确认这个人真实存在且角色正确，
     *       比写完之后发现是个已删除账号要好。</li>
     * </ol>
     * 代价是每次审批多一次主键查询 —— 审批是低频操作，值得。
     */
    private void requireBankUser(Long bankUserId) {
        User user = userMapper.selectById(bankUserId);
        if (user == null) {
            throw new ResourceNotFoundException("操作人不存在");
        }
        if (!UserConst.ROLE_BANK.equals(user.getRole())) {
            throw new BizException(ResultCode.FORBIDDEN, "只有银行角色可以执行审批操作");
        }
        if (!Objects.equals(user.getStatus(), UserConst.STATUS_NORMAL)) {
            throw new BizException(ResultCode.ACCOUNT_DISABLED, "账号状态异常，无法执行审批");
        }
    }
}
