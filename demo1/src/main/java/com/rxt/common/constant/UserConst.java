package com.rxt.common.constant;

/**
 * 用户相关的枚举值常量。
 *
 * <p><b>为什么不用 Java 枚举（enum）？</b>因为要和数据库里存的 {@code VARCHAR} / {@code TINYINT}
 * 直接对应。用 enum 就得到处写 {@code Status.of(user.getStatus())} 做转换，
 * 在 P1 这个体量上是纯粹的负担。用 {@code static final} 常量表达"这几个值是合法的"，
 * 校验时写 {@code Set.of(...).contains(role)} 就够清楚了。
 *
 * <p><b>值的来源</b>：init.sql 的列注释 + 概要设计 2.3.2 用户角色与权限。
 *
 * <p>M7 的专家审核接口也会 import 这里的 {@link #STATUS_PENDING} / {@link #STATUS_REJECTED}，
 * 所以状态值只在这里定义一份，避免两边写岔。
 */
public final class UserConst {

    private UserConst() {
        // 常量类不允许实例化
    }

    // ==================== 角色 ====================
    /** 农户（与买家共用此角色，见下方待确认事项） */
    public static final String ROLE_FARMER = "farmer";
    /** 农业专家。注册后 status=0，需管理员审核 */
    public static final String ROLE_EXPERT = "expert";
    /** 银行人员。不能自助注册，由管理员在 M7 创建 */
    public static final String ROLE_BANK = "bank";
    /** 平台管理员 */
    public static final String ROLE_ADMIN = "admin";

    /**
     * 开放自助注册的角色白名单。
     *
     * <p>依据 SRS 表11："用户通过手机号注册账号，选择角色（农户/买家/专家）"——
     * 里面没有 bank 和 admin，说明这两类账号只能由管理员创建。
     * <b>Service 里必须校验</b>，否则任何人都能 POST 一个 {@code "role":"admin"} 把自己变成管理员。
     *
     * <p>⚠️ 待与组内确认：文档说"农户与买家共用 farmer"，但 2.3.2 权限矩阵又把买家单列。
     * 若买家要独立角色，这里要加一个值（例如 {@code buyer}），并且 init.sql 的列注释要同步改。
     */
    public static final java.util.Set<String> REGISTER_ROLES = java.util.Set.of(ROLE_FARMER, ROLE_EXPERT);

    // ==================== 状态 ====================
    /** 0 待审核。专家注册后的初始状态 */
    public static final int STATUS_PENDING = 0;
    /** 1 正常。农户/买家注册后即为正常；专家审核通过后置为 1 */
    public static final int STATUS_NORMAL = 1;
    /** 2 已拒绝。专家审核被拒（M7 表39：拒绝 → status=2） */
    public static final int STATUS_REJECTED = 2;
    /** 3 已禁用。管理员拉黑账号 */
    public static final int STATUS_DISABLED = 3;
}
