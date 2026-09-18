package com.rxt.service.impl;

import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.rxt.common.PageResult;
import com.rxt.common.ResultCode;
import com.rxt.common.constant.ArticleConst;
import com.rxt.common.constant.AuditConst;
import com.rxt.common.constant.UserConst;
import com.rxt.common.exception.BizException;
import com.rxt.common.exception.ResourceNotFoundException;
import com.rxt.dto.AuditDTO;
import com.rxt.entity.Article;
import com.rxt.entity.Expert;
import com.rxt.entity.User;
import com.rxt.mapper.ArticleMapper;
import com.rxt.mapper.ExpertMapper;
import com.rxt.mapper.UserMapper;
import com.rxt.service.AdminService;
import com.rxt.vo.ArticleVO;
import com.rxt.vo.UserVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 系统管理服务实现 —— M7。
 *
 * <p>这个类里有三处"看起来多余、其实是必要防线"的代码，值得逐个说明：
 * <ol>
 *   <li>{@link #changeUserStatus} 里的"不能禁用自己的账号"——
 *       如果不拦，管理员一键把自己禁用，之后就再也没有人能启用他（因为没人能登录后台了），
 *       系统直接锁死；</li>
 *   <li>{@link #auditExpert} 里的"只有待审核状态才能被审核"——
 *       表39 明确"审核结果不可撤回"。如果允许重复审核，管理员就能把已被拒的专家
 *       改成通过，等于绕过了审核流程；</li>
 *   <li>{@link #auditArticle} 里的"拒绝必须填理由"——表40 的要求，
 *       而且这条<b>不能只靠前端拦</b>：前端能拦住的输入，直接构造请求就绕过去了。</li>
 * </ol>
 *
 * <p>这些校验的共同点是：<b>它们保护的是"状态机"不被非法跳转</b>，
 * 而不只是"数据格式对不对"。所以必须写在服务端。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminServiceImpl implements AdminService {

    /** 每页条数上限，防止前端传 size=999999 */
    private static final int MAX_PAGE_SIZE = 200;

    private final UserMapper userMapper;
    private final ExpertMapper expertMapper;
    private final ArticleMapper articleMapper;

    // ==================================================================================
    // 1. 用户列表
    // ==================================================================================
    @Override
    public PageResult<UserVO> pageUsers(int page, int size, String role, Integer status, String keyword) {
        Page<User> pageParam = new Page<>(normalizePage(page), normalizeSize(size));

        String kw = StringUtils.hasText(keyword) ? keyword.trim() : null;
        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<User>()
                .eq(StringUtils.hasText(role), User::getRole, role)
                .eq(status != null, User::getStatus, status)
                // and(...) 会把里面的条件整体加上括号，生成
                // AND (phone LIKE ? OR name LIKE ? OR email LIKE ?)
                // 如果不加这层括号，会变成 "role=? AND phone LIKE ? OR name LIKE ?"，
                // 因为 OR 的优先级低于 AND，后面的条件会把前面的 role 筛选顶掉——
                // 这是写多条件模糊搜索最经典的 bug。
                .and(kw != null, w -> w
                        .like(User::getPhone, kw).or()
                        .like(User::getName, kw).or()
                        .like(User::getEmail, kw))
                .orderByDesc(User::getCreateTime);

        Page<User> result = userMapper.selectPage(pageParam, wrapper);
        // PageResult.of 会顺便把每条记录转成 VO，从结构上保证密码不会泄露
        return PageResult.of(result, UserVO::from);
    }

    // ==================================================================================
    // 2. 启用 / 禁用用户
    // ==================================================================================
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void changeUserStatus(Long operatorId, Long targetUserId, Integer status) {
        if (status == null
                || (status != UserConst.STATUS_NORMAL && status != UserConst.STATUS_DISABLED)) {
            throw new BizException(ResultCode.PARAM_ERROR, "状态只能设为 1（正常）或 3（已禁用）");
        }

        User target = userMapper.selectById(targetUserId);
        if (target == null) {
            throw new ResourceNotFoundException("用户不存在");
        }

        // 表38 异常处理："禁用自己 → 提示不能禁用自己的账号"
        // 只在"禁用"时判断：启用自己是无意义操作（能登录就说明没被禁用）
        if (status == UserConst.STATUS_DISABLED && operatorId.equals(targetUserId)) {
            throw new BizException(ResultCode.PARAM_ERROR, "不能禁用自己的账号");
        }

        // 幂等：已经是目标状态就不重复写库。
        // 好处是前端重复点击不会产生多余的 UPDATE，也不会把 update_time 刷成新值
        if (Objects.equals(target.getStatus(), status)) {
            log.debug("用户 {} 已经是状态 {}，跳过更新", targetUserId, status);
            return;
        }

        // 用 LambdaUpdateWrapper 而不是 updateById(整个实体)：
        // 后者会把查出来的所有字段（含密码）一起写回，一旦并发修改就会互相覆盖
        userMapper.update(null, new LambdaUpdateWrapper<User>()
                .eq(User::getId, targetUserId)
                .set(User::getStatus, status));

        log.info("管理员 {} 将用户 {}（{}）状态改为 {}", operatorId, targetUserId, target.getPhone(), status);
    }

    // ==================================================================================
    // 3. 审核专家账号
    // ==================================================================================
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void auditExpert(Long auditorId, Long userId, AuditDTO dto) {
        assertAuditParam(dto);

        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new ResourceNotFoundException("用户不存在");
        }
        if (!UserConst.ROLE_EXPERT.equals(user.getRole())) {
            throw new BizException(ResultCode.PARAM_ERROR, "该账号不是专家角色，无需审核");
        }
        // 表39 业务规则："审核结果不可撤回" —— 只有待审核(0)的专家能被审核。
        // 这一条同时挡住了三种非法操作：重复审核、把已拒绝改成通过、审核一个已禁用的账号
        if (!Objects.equals(user.getStatus(), UserConst.STATUS_PENDING)) {
            throw new BizException(ResultCode.PARAM_ERROR, "该专家已审核，审核结果不可撤回");
        }

        boolean approved = AuditConst.isApproved(dto.getStatus());
        int newStatus = approved ? UserConst.STATUS_NORMAL : UserConst.STATUS_REJECTED;

        userMapper.update(null, new LambdaUpdateWrapper<User>()
                .eq(User::getId, userId)
                .set(User::getStatus, newStatus)
                .set(User::getAuditRemark, dto.getRemark()));

        // 表39："专家审核通过后，写入 t_expert 表"
        if (approved) {
            upsertExpert(user, auditorId, dto.getRemark());
        }

        log.info("管理员 {} 审核专家 {}（{}）：{}", auditorId, userId, user.getPhone(),
                approved ? "通过" : "拒绝");
    }

    /**
     * 写入 / 更新专家扩展信息。
     *
     * <p>资质字段（职称、机构、领域、简介、资质证明）在注册时是前端塞进
     * {@code t_user.extend_json} 的。这里把它们解析出来落到 t_expert——
     * 这样主表保持精简，专家资料又有正式的表结构可查、可索引。
     *
     * <p>做成"有则更新、无则插入"（upsert）而不是"直接 insert"，
     * 是为了防止极端情况下的重复插入撞上 {@code uk_expert_user} 唯一索引。
     */
    private void upsertExpert(User user, Long auditorId, String remark) {
        Expert expert = new Expert();
        expert.setUserId(user.getId());
        expert.setRealName(user.getName());
        expert.setAuditRemark(remark);
        expert.setAuditBy(auditorId);
        expert.setAuditTime(LocalDateTime.now());

        if (StringUtils.hasText(user.getExtendJson())) {
            try {
                JSONObject json = JSONUtil.parseObj(user.getExtendJson());
                expert.setTitle(json.getStr("title"));
                expert.setOrg(json.getStr("org"));
                expert.setDomain(json.getStr("domain"));
                expert.setIntro(json.getStr("intro"));
                expert.setCertUrl(json.getStr("certUrl"));
            } catch (Exception e) {
                // extend_json 是数据库里的 JSON 列，理论上一定合法；
                // 但这是外部输入，解析失败不能让审核流程整体失败——
                // 资质字段缺失只影响展示，审核结果本身必须生效
                log.warn("专家 {} 的 extend_json 解析失败，跳过资质字段： {}", user.getId(), e.getMessage());
            }
        }

        Expert exist = expertMapper.selectOne(
                new LambdaQueryWrapper<Expert>().eq(Expert::getUserId, user.getId()));
        if (exist == null) {
            expertMapper.insert(expert);
        } else {
            expert.setId(exist.getId());
            expertMapper.updateById(expert);
        }
    }

    // ==================================================================================
    // 4. 审核文章
    // ==================================================================================
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void auditArticle(Long auditorId, Long articleId, AuditDTO dto) {
        assertAuditParam(dto);

        Article article = articleMapper.selectById(articleId);
        if (article == null) {
            throw new ResourceNotFoundException("文章不存在");
        }
        if (!Objects.equals(article.getStatus(), ArticleConst.STATUS_PENDING)) {
            throw new BizException(ResultCode.PARAM_ERROR, "该文章已审核，不可重复操作");
        }

        boolean approved = AuditConst.isApproved(dto.getStatus());

        articleMapper.update(null, new LambdaUpdateWrapper<Article>()
                .eq(Article::getId, articleId)
                .set(Article::getStatus, approved ? ArticleConst.STATUS_PUBLISHED : ArticleConst.STATUS_REJECTED)
                .set(Article::getAuditRemark, dto.getRemark())
                .set(Article::getAuditBy, auditorId)
                .set(Article::getAuditTime, LocalDateTime.now()));

        log.info("管理员 {} 审核文章 {}（{}）：{}", auditorId, articleId, article.getTitle(),
                approved ? "通过" : "拒绝");
    }

    // ==================================================================================
    // 5. 文章列表
    // ==================================================================================
    @Override
    public PageResult<ArticleVO> pageArticles(int page, int size, Integer status, String keyword) {
        Page<Article> pageParam = new Page<>(normalizePage(page), normalizeSize(size));

        String kw = StringUtils.hasText(keyword) ? keyword.trim() : null;
        LambdaQueryWrapper<Article> wrapper = new LambdaQueryWrapper<Article>()
                .eq(status != null, Article::getStatus, status)
                .like(kw != null, Article::getTitle, kw)
                // 先按状态升序：0 待审核排最前，审核页一进来就看到该处理的
                .orderByAsc(Article::getStatus)
                .orderByDesc(Article::getCreateTime);

        Page<Article> result = articleMapper.selectPage(pageParam, wrapper);
        PageResult<ArticleVO> voPage = PageResult.of(result, ArticleVO::from);
        fillAuthorName(voPage.getRecords());
        return voPage;
    }

    /**
     * 批量回填作者姓名，避免 N+1 查询。
     *
     * <p>错误做法是遍历每条文章各查一次用户：10 条数据 = 1 次文章查询 + 10 次用户查询。
     * 正确做法是收集这一页所有 authorId，用<b>一条</b> {@code selectBatchIds} 捞回来，
     * 做成 Map 再回填，总共 2 次查询。
     */
    private void fillAuthorName(List<ArticleVO> articles) {
        if (articles == null || articles.isEmpty()) {
            return;
        }
        Set<Long> authorIds = articles.stream()
                .map(ArticleVO::getAuthorId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        if (authorIds.isEmpty()) {
            return;
        }
        List<User> authors = userMapper.selectBatchIds(authorIds);
        Map<Long, String> nameMap = authors.stream()
                .collect(Collectors.toMap(
                        User::getId,
                        u -> StringUtils.hasText(u.getName()) ? u.getName() : "未命名用户",
                        // 理论上 id 唯一不会重复，但 toMap 遇到重复键会抛异常，给个合并函数兜底
                        (a, b) -> a));
        articles.forEach(article -> article.setAuthorName(
                nameMap.getOrDefault(article.getAuthorId(), "未知用户")));
    }

    // ==================================================================================
    // 私有辅助
    // ==================================================================================

    /**
     * 审核入参的业务校验。
     *
     * <p>为什么不在 DTO 上用注解完成？
     * 因为"拒绝时必须填理由"是<b>跨字段的条件校验</b>
     * （取决于 status 的值才知道 remark 要不要必填），
     * Bean Validation 表达不了这种依赖关系，得不偿失。
     */
    private void assertAuditParam(AuditDTO dto) {
        if (!AuditConst.isValid(dto.getStatus())) {
            throw new BizException(ResultCode.PARAM_ERROR, "审核结果只能是 APPROVED（通过）或 REJECTED（拒绝）");
        }
        if (!AuditConst.isApproved(dto.getStatus()) && !StringUtils.hasText(dto.getRemark())) {
            throw new BizException(ResultCode.PARAM_ERROR, "拒绝时必须填写理由");
        }
    }

    /** 页码纠正：小于 1 一律当第一页 */
    private int normalizePage(int page) {
        return page < 1 ? 1 : page;
    }

    /** 每页条数纠正：非法值给 10，超过上限截断到 200 */
    private int normalizeSize(int size) {
        if (size < 1) {
            return 10;
        }
        return Math.min(size, MAX_PAGE_SIZE);
    }
}
