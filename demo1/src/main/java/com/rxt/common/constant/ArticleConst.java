package com.rxt.common.constant;

/**
 * 文章状态常量（M7 表40 文章审核）。
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
}
