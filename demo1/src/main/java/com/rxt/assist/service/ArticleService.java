package com.rxt.assist.service;

import com.rxt.assist.dto.ArticleCommentDTO;
import com.rxt.assist.dto.ArticlePublishDTO;
import com.rxt.assist.vo.ArticleDetailVO;
import com.rxt.assist.vo.LikeResultVO;

/**
 * 文章服务 —— M5 专家助力（文章部分）。
 *
 * <p><b>与 P1 的分工要说清楚：</b>P1 已经实现了文章模块的两个接口
 * （{@code GET /api/article/list} 和 {@code PUT /api/article/{id}/audit}，
 * 在 {@code com.rxt.controller.ArticleController} + {@code AdminService} 里），
 * 因为管理员的"文章审核"页需要数据来源；
 * P2 的 M2 首页也读了 t_article（{@code HomeServiceImpl} 的文章入口）。
 * <b>本类补齐剩下 4 个真正属于 M5 的接口</b>（发布 / 详情 / 点赞 / 评论），
 * 不重复实现已有的那两个。
 *
 * <p>这里有个关键的业务规则，也是 M5 里唯一"按角色分叉"的地方：
 * <b>农户发布文章要审核（status=0），专家发布直接生效（status=1）</b> ——
 * 依据 SRS 表4 权限矩阵。它决定了"谁能立刻看到这篇文章"，
 * 所以必须在服务端按 Token 里的角色判定，而不是让前端传 status
 * （DTO 里刻意没有这个字段，见 {@link ArticlePublishDTO}）。
 */
public interface ArticleService {

    /**
     * 发布文章 —— {@code POST /api/article}。
     *
     * <p>角色决定初始状态：{@code farmer} → 0 待审核；{@code expert} → 1 已发布。
     * 其他角色（bank / admin）不能发布，报 1007。
     *
     * @return 新文章 id
     */
    Long publish(Long userId, String role, ArticlePublishDTO dto);

    /**
     * 文章详情 —— {@code GET /api/article/{id}}。
     *
     * <p>三件事：
     * <ol>
     *   <li><b>可见性</b>：已发布（status=1）对所有人可见；未发布/被拒/已下架的文章
     *       只有作者本人和管理员能看 —— 否则"审核中"的文章会被随便谁猜到 id 就看走；</li>
     *   <li><b>浏览量 +1</b>：只对已发布的文章计数；</li>
     *   <li><b>组装 isLiked 与评论列表</b>：这两项依赖当前登录身份，
     *       所以放在这里而不是 VO 的静态工厂里。</li>
     * </ol>
     *
     * @param currentUserId 当前登录用户 id，<b>可能为 null</b>（该接口对游客开放）
     * @param role          当前登录用户角色，可能为 null
     */
    ArticleDetailVO detail(Long articleId, Long currentUserId, String role);

    /**
     * 点赞 / 取消点赞 —— {@code PUT /api/article/{id}/like}（可切换）。
     *
     * <p>要"可切换"就必须记录谁赞过，所以除了给 {@code like_count} 加减，
     * 还要写 {@code t_article_like}（唯一索引保证一人一篇只能赞一次）。
     *
     * <p>返回操作后的最终状态与计数，前端直接覆盖本地值即可，不用自己 ±1
     * （自己算在重试、多端操作时会错位）。理由见 {@code LikeResultVO}。
     */
    LikeResultVO toggleLike(Long userId, Long articleId);

    /**
     * 发表评论 —— {@code POST /api/article/{id}/comment}。
     *
     * <p>只有已发布的文章能评论：给一篇还没过审的文章留言，
     * 等于让评论去"预支"一个可能被拒的结论。
     */
    void comment(Long userId, Long articleId, ArticleCommentDTO dto);
}
