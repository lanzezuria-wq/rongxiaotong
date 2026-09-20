package com.rxt.finance.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 银行审批记录 —— 对应 t_bank_approval（M6）。
 *
 * <p>一次审批一行，挂在 {@code groupId} 上。<b>审批结果不可撤回</b>：
 * 一个小组只会有一行记录，靠"该小组是否已有审批记录"来挡住重复审批
 * （见 {@code BankServiceImpl#approve}）。这比只看小组状态更可靠 ——
 * 万一组状态因某个 bug 没改成功，有审批记录这一条仍然挡得住第二次审批，
 * 避免一个小组批出两个相反的结论。
 *
 * <p>{@code creditScore / interestRate / decisionAmount} 在 Mock 模式下
 * 由 {@code com.rxt.finance.config.MockConfig} 自动生成，前端也可以显式传入覆盖。
 */
@Data
@TableName("t_bank_approval")
public class BankApproval {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 被审批的联保小组 */
    private Long groupId;

    /** 审批银行 t_user.id */
    private Long bankId;

    /** APPROVED 通过 / REJECTED 拒绝 */
    private String result;

    /** 信用评分 0~100 */
    private Integer creditScore;

    /** 年化利率 %，3.85 ~ 7.00 */
    private BigDecimal interestRate;

    /** 核定额度（元）。拒绝时为 null 而不是 0 —— 给 0 会让人误以为"批了 0 元" */
    private BigDecimal decisionAmount;

    private String remark;

    private LocalDateTime approvedAt;
}
