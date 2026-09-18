package com.rxt.common.constant;

import java.util.Set;

/**
 * 审核相关的常量 —— 专家审核（M7 表39）和文章审核（M7 表40）共用同一套结果值。
 *
 * <p><b>为什么用字符串 {@code APPROVED / REJECTED} 而不是 0/1？</b>
 * 因为这是<b>接口契约</b>的一部分：概要设计 5.2.7 的
 * {@code PUT /api/admin/expert/{id}/audit} 请求体写的就是
 * {@code status（APPROVED/REJECTED）}。前端按这个传，后端按这个收，
 * 数据库里再映射成数字状态（0 待审 / 1 通过 / 2 拒绝）。
 *
 * <p>这样做的好处是语义自解释：请求体里出现 {@code "status":"APPROVED"}
 * 比 {@code "status":1} 一眼就懂；而且"待审核"这个状态<b>不能</b>由审核接口设置，
 * 用字符串枚举就从取值上天然排除了（数字 0/1/2 很容易传错成 0）。
 */
public final class AuditConst {

    private AuditConst() {
    }

    /** 审核通过 */
    public static final String APPROVED = "APPROVED";
    /** 审核拒绝 */
    public static final String REJECTED = "REJECTED";

    private static final Set<String> ALL = Set.of(APPROVED, REJECTED);

    /** 判断审核结果取值是否合法 */
    public static boolean isValid(String status) {
        return status != null && ALL.contains(status.toUpperCase());
    }

    /** 是否"通过" */
    public static boolean isApproved(String status) {
        return APPROVED.equalsIgnoreCase(status);
    }
}
