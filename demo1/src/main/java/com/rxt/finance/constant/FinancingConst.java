package com.rxt.finance.constant;

import java.math.BigDecimal;
import java.util.Set;

/**
 * 融资模块常量 —— M4 融资申请 + M6 银行审批共用。
 *
 * <p><b>为什么把两个模块的状态值放一个类里？</b>因为它们是<b>同一条状态机上的两段</b>：
 * 农户侧看到的"申请状态"和银行侧看到的"小组状态"是联动的 ——
 * 银行审批通过时，小组和组内每条申请要一起置为"已通过"。
 * 分开放会出现"小组那边改了、申请那边忘了改"的不一致，所以值定义在一处。
 *
 * <p><b>状态值的来源</b>：{@code init_p3.sql} 的列注释。
 * 用 {@code int} 而不是 enum，理由同 P1 的 {@code UserConst}：
 * 要和数据库的 TINYINT 直接对应；而 {@link #applyStatusName} /
 * {@link #groupStatusName} 负责把数字翻译成前端要显示的中文。
 */
public final class FinancingConst {

    private FinancingConst() {
    }

    // ==================================================================================
    // 一、融资申请状态（t_financing_apply.status）
    // ==================================================================================

    /** 0 待匹配 —— 申请已提交，还没加入任何联保小组 */
    public static final int APPLY_PENDING_MATCH = 0;
    /** 1 已匹配 —— 已加入联保小组，但小组还没成型 */
    public static final int APPLY_MATCHED = 1;
    /** 2 待银行审批 —— 所在小组已成型并进入银行审批池 */
    public static final int APPLY_PENDING_BANK = 2;
    /** 3 已通过 —— 银行审批通过 */
    public static final int APPLY_APPROVED = 3;
    /** 4 已拒绝 —— 银行审批拒绝（拒绝的是整个小组，组内所有申请一起被拒） */
    public static final int APPLY_REJECTED = 4;
    /** 5 已取消 —— 申请人主动取消 */
    public static final int APPLY_CANCELLED = 5;

    /**
     * "进行中"的状态集合。用途有两个，都是防重复提交的关键：
     * <ol>
     *   <li>提交新申请时判断"是否已有进行中的申请"（已有则报 3001）；</li>
     *   <li>取消申请时判断"这条申请还能不能取消"。</li>
     * </ol>
     * 0/1/2 是进行中，3/4/5 是终态（终态可以再发起新申请）。
     */
    public static final Set<Integer> APPLY_ACTIVE_STATUSES =
            Set.of(APPLY_PENDING_MATCH, APPLY_MATCHED, APPLY_PENDING_BANK);

    /**
     * 申请状态 → 中文展示名。
     *
     * <p>为什么不放前端映射？因为审批结果、后台列表、导出报表都要用，
     * 放前端会出现"三个页面三份文案，改一处漏两处"。后端翻译成 statusName 一次给全。
     */
    public static String applyStatusName(Integer status) {
        if (status == null) {
            return "未知";
        }
        return switch (status) {
            case APPLY_PENDING_MATCH -> "待匹配";
            case APPLY_MATCHED -> "已匹配";
            case APPLY_PENDING_BANK -> "待银行审批";
            case APPLY_APPROVED -> "已通过";
            case APPLY_REJECTED -> "已拒绝";
            case APPLY_CANCELLED -> "已取消";
            default -> "未知";
        };
    }

    /** 申请是否处于"进行中"（0/1/2） */
    public static boolean isApplyActive(Integer status) {
        return status != null && APPLY_ACTIVE_STATUSES.contains(status);
    }

    // ==================================================================================
    // 二、联保小组状态（t_guarantee_group.status）
    // ==================================================================================

    /** 0 招募中 —— 成员数还没到 MIN_MEMBERS，小组未成型，银行看不到 */
    public static final int GROUP_RECRUITING = 0;
    /** 1 已匹配 —— 成员数达到 MIN_MEMBERS，小组已成型，进入银行待审池 */
    public static final int GROUP_MATCHED = 1;
    /**
     * 2 待银行审批 —— <b>预留位</b>。
     *
     * <p>本项目是"小组成型即入池"，不需要农户或平台再点一次"提交银行"，
     * 所以正常业务流程不会停留在该值。保留它是为了两件事：
     * <ul>
     *   <li>与文档的状态定义保持一致（文档 06 的列注释里就是这 5 个值）；</li>
     *   <li>将来若要加"线下材料齐了再提交"的流程，不用改表结构和前端字典。</li>
     * </ul>
     * 银行端筛选"待审批"时同时认 1 和 2，就是为了兼容这两种语义。
     */
    public static final int GROUP_PENDING_BANK = 2;
    /** 3 已通过 */
    public static final int GROUP_APPROVED = 3;
    /** 4 已拒绝 */
    public static final int GROUP_REJECTED = 4;

    /**
     * 银行端"待审批"筛选要认的状态。
     * 用它而不是写死 {@code status = 1}，改天真要启用"2 待银行审批"时只需改这一处。
     */
    public static final Set<Integer> GROUP_PENDING_STATUSES =
            Set.of(GROUP_MATCHED, GROUP_PENDING_BANK);

    /** 已经被银行处理过（终态）的状态集合。审批结果不可撤回，靠它挡住二次审批 */
    public static final Set<Integer> GROUP_HANDLED_STATUSES =
            Set.of(GROUP_APPROVED, GROUP_REJECTED);

    /** 小组状态 → 中文展示名 */
    public static String groupStatusName(Integer status) {
        if (status == null) {
            return "未知";
        }
        return switch (status) {
            case GROUP_RECRUITING -> "招募中";
            case GROUP_MATCHED -> "已匹配";
            case GROUP_PENDING_BANK -> "待银行审批";
            case GROUP_APPROVED -> "已通过";
            case GROUP_REJECTED -> "已拒绝";
            default -> "未知";
        };
    }

    // ==================================================================================
    // 三、联保小组成员状态（t_guarantee_member.join_status）
    // ==================================================================================

    /** 0 申请中 —— 预留值，本项目"申请加入"直接生效为 1 */
    public static final int MEMBER_APPLYING = 0;
    /** 1 已加入 —— 成员身份生效 */
    public static final int MEMBER_JOINED = 1;
    /** 2 已拒绝 —— 预留值（本项目的联保小组是农户自治、没有组长审批动作） */
    public static final int MEMBER_REJECTED = 2;

    // ==================================================================================
    // 四、业务规则阈值
    // ==================================================================================

    /**
     * 联保小组成型所需的最少成员数。
     *
     * <p>为什么是 3？单个农户违约时剩下两人分摊代偿，风险才真正被分散；
     * 2 人组等于互相担保的对赌，和单人贷款差别不大。这是"联保"模式的通行门槛。
     */
    public static final int MIN_MEMBERS = 3;

    /**
     * 联保小组成员数上限。
     *
     * <p>上限来自契约（OpenAPI 的 GuaranteeGroupVO 里 {@code maxMembers} 示例值 10）。
     * 人数再多，单个成员的违约影响被稀释的同时"搭便车"问题也会变严重，
     * 而且银行审批要逐个核验，10 人是可操作性上的折中。
     */
    public static final int MAX_MEMBERS = 10;

    /** 融资申请金额下限（元）—— 契约里 {@code minimum: 10000} */
    public static final BigDecimal MIN_AMOUNT = new BigDecimal("10000");
    /** 融资申请金额上限（元）—— 契约里 {@code maximum: 500000} */
    public static final BigDecimal MAX_AMOUNT = new BigDecimal("500000");
}
