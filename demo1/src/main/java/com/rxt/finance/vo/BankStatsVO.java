package com.rxt.finance.vo;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 银行端统计出参 —— 对应契约里的 {@code BankStatsVO}。
 *
 * <p>五个数字全部由 {@code SELECT COUNT/SUM} 实时算出来，<b>不做缓存也不落表</b>。
 * 原因：这是给审批员看的"当前待办"，一旦缓存就会显示过期数字
 * （刚审完一个还显示 5 个待审，审批员会以为没生效）。
 * 数据量在演示与试运行阶段完全撑得住，真到百万级再考虑加汇总表。
 *
 * <p>{@code totalApprovedAmount} 用 {@code BigDecimal} 且聚合前会判空 ——
 * {@code SUM()} 在没有任何匹配行时返回的是 {@code NULL} 而不是 0，
 * 直接赋值给 BigDecimal 会得到 null，前端显示"null 元"。
 * 见 {@code BankServiceImpl#stats} 的处理。
 */
@Data
public class BankStatsVO {

    /** 小组总数 */
    private Long totalGroups;

    /** 待审批数（status 为"已匹配"或"待银行审批"） */
    private Long pendingGroups;

    /** 已通过数 */
    private Long approvedGroups;

    /** 已拒绝数 */
    private Long rejectedGroups;

    /** 已通过审批的核定额度合计（元）。无数据时为 0 而不是 null */
    private BigDecimal totalApprovedAmount;
}
