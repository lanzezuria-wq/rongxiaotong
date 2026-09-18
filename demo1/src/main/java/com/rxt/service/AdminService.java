package com.rxt.service;

import com.rxt.common.PageResult;
import com.rxt.dto.AuditDTO;
import com.rxt.vo.ArticleVO;
import com.rxt.vo.UserVO;

/**
 * 系统管理服务 —— M7 模块。
 *
 * <p>把 M7 的四类操作收在一个接口里，是因为它们<b>共享一套权限模型</b>
 * （全部只有管理员能调用）和一套审核语义。拆成四个 Service 会让每个都很薄，
 * 但它们之间其实有共同的规则（例如"审核不可撤回"），放一起更内聚。
 *
 * <p>字典的增删改查单独拆到 {@link DictService}，因为它的规则体系完全不同
 * （类型白名单、同类型编码唯一、被引用不可删），没有共享逻辑。
 */
public interface AdminService {

    /**
     * 用户列表（分页 + 筛选）—— {@code GET /api/admin/user/list}（表38）。
     *
     * <p>专家审核页也复用它来拿"待审核专家列表"：传 {@code role=expert&status=0} 即可。
     * 这正是表59 里没有单独的"专家列表"接口的原因——表38 的筛选能力已经覆盖了。
     *
     * @param page    页码，从 1 开始。非法值会被纠正
     * @param size    每页条数。上限 200
     * @param role    角色筛选，可空
     * @param status  状态筛选，可空
     * @param keyword 关键词，模糊匹配手机号 / 姓名 / 邮箱，可空
     */
    PageResult<UserVO> pageUsers(int page, int size, String role, Integer status, String keyword);

    /**
     * 启用 / 禁用用户 —— {@code PUT /api/admin/user/{id}/status}（表38）。
     *
     * <p>规则：只能设为 1（正常）或 3（已禁用）；<b>管理员不能禁用自己的账号</b>。
     *
     * @param operatorId   操作人（当前登录管理员）的 id
     * @param targetUserId 被操作用户的 id
     */
    void changeUserStatus(Long operatorId, Long targetUserId, Integer status);

    /**
     * 审核专家账号 —— {@code PUT /api/admin/expert/{id}/audit}（表39）。
     *
     * <p>通过 → t_user.status=1 并写入 t_expert；拒绝 → t_user.status=2。
     * <b>审核结果不可撤回</b>：只有 status=0（待审核）的专家能被审核。
     *
     * @param auditorId 审核人（当前登录管理员）的 id
     * @param userId    被审核专家的 t_user.id
     */
    void auditExpert(Long auditorId, Long userId, AuditDTO dto);

    /**
     * 审核文章 —— {@code PUT /api/article/{id}/audit}（表40）。
     *
     * <p>⚠️ 注意这个接口<b>不在 {@code /api/admin/**} 下</b>，它在 SRS 表58（专家助力模块）
     * 里定义。权限配置和跨模块联调时最容易漏掉它。
     *
     * @param auditorId 审核人 id
     * @param articleId 文章 id
     */
    void auditArticle(Long auditorId, Long articleId, AuditDTO dto);

    /**
     * 文章列表（分页 + 筛选）—— 支撑 M7 文章审核页。
     *
     * <p>对应 SRS 表58 的 {@code GET /api/article/list}（该接口标注认证为"×"，游客可读）。
     * <b>它属于专家模块（P4）的范围</b>，P1 这里实现是因为审核页必须有数据来源。
     * 需要和 P4 确认归属，避免两边各写一版。
     *
     * @param status  状态筛选，可空。审核页传 0（待审核）
     * @param keyword 标题关键词，可空
     */
    PageResult<ArticleVO> pageArticles(int page, int size, Integer status, String keyword);
}
