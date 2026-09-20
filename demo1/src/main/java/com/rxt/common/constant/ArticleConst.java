package com.rxt.common.constant;

import java.util.Set;

/**
 * 文章状态常量（M7 表40 文章审核）+ 文章分类白名单（P3 的 M5 专家助力用）。
 *
 * <p>取值依据 SRS 表4 权限矩阵："农户发布知识文章需审核，专家可直接发布"。
 * 所以发布时按作者角色决定初始 status：农户 → 0 待审核，专家 → 1 已发布。
 */
public final class ArticleConst {

    private ArticleConst() {
    }

    /** 0 待审核。农户发布的文章初始状态 */
    public static final int STATUS_PENDING = 0;
    /** 1 已发布。审核通过，或专家直接发布 */
    public static final int STATUS_PUBLISHED = 1;
    /** 2 已拒绝。审核未通过，拒绝理由必填 */
    public static final int STATUS_REJECTED = 2;
    /** 3 已下架。作者或管理员主动下架 */
    public static final int STATUS_OFFLINE = 3;

    // ==================================================================================
    // 文章分类（P3 新增）
    // ==================================================================================

    /**
     * 文章分类白名单 —— 采用 OpenAPI 契约里 {@code ArticleDTO.category} 的枚举。
     *
     * <p><b>⚠️ 这里有一处「契约 vs P1 种子数据」的冲突，必须说明：</b>
     * <ul>
     *   <li>契约（08-OpenAPI）给的枚举是
     *       {@code planting / breeding / market / policy / experience}；</li>
     *   <li>但 P1 的 init.sql 种子里，4 篇示例文章的 category 写的是
     *       {@code VEG / GRAIN / CASH} —— 那是 {@code product_category} 字典的值。
     *       而 P2 的商品表也用 {@code product_category}（{@code t_product.category_code}）。</li>
     * </ul>
     * 两边都"有道理"（文章讲的就是蔬菜种植，用产品分类当文章分类很自然），
     * 但它们是两套值，不能二选一而不破坏另一方（改了契约枚举 P4 按契约写的前端就废了；
     * 改了种子数据 P1 的示例文章就查不到了）。
     *
     * <p><b>处理方式：发布接口对分类做「并集校验」</b> —— 取值落在
     * {@link #CATEGORY_ENUM} 里，或者能在 {@code t_dict} 里查到该编码，都算合法
     * （见 {@code com.rxt.assist.service.impl.ArticleServiceImpl#assertCategory}）。
     * 这样 P1 的种子数据与契约枚举都能通过，两套前端下拉框也都能用。
     * <b>需要和 P1/P4 定稿最终统一到哪一套。</b>
     */
    public static final Set<String> CATEGORY_ENUM =
            Set.of("planting", "breeding", "market", "policy", "experience");

    /** 列表/详情出参里 {@code summary} 字段的截取长度 */
    public static final int SUMMARY_LENGTH = 100;

    /** 评论内容长度上限 —— 需求规定 200 字（契约里 {@code maxLength: 200}） */
    public static final int COMMENT_MAX_LENGTH = 200;

    /** 标题长度上限（契约里 {@code maxLength: 100}） */
    public static final int TITLE_MAX_LENGTH = 100;
}
