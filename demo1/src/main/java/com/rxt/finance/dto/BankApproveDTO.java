package com.rxt.finance.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 银行审批入参 —— {@code PUT /api/bank/approve}（M6）。
 *
 * <p>{@code creditScore / interestRate / decisionAmount} <b>三个字段都是可选的</b>，
 * 契约里明确写了"可选，Mock 模式可自动生成"。这条设计让演示时只需要传
 * {@code groupId + result} 就能完成审批，不用手工编数字；
 * 而真实场景下银行审批员填了具体值就会覆盖自动生成的结果。
 *
 * <p>三个数值字段都带了范围约束，这是<b>必要</b>的：自动生成时服务端自己能保证范围，
 * 但一旦允许前端传入，就必须挡住 {@code interestRate = -1} 或
 * {@code creditScore = 99999} 这种会直接污染审批台账的值。
 */
@Data
public class BankApproveDTO {

    @NotNull(message = "联保小组 id 不能为空")
    private Long groupId;

    /** APPROVED 通过 / REJECTED 拒绝。取值合法性由 Service 校验（复用 AuditConst 的判定） */
    @NotBlank(message = "审批结果不能为空")
    private String result;

    /** 信用评分 0~100。不传则由 Mock 模式生成 */
    @Min(value = 0, message = "信用评分不能小于 0")
    @Max(value = 100, message = "信用评分不能大于 100")
    private Integer creditScore;

    /** 年化利率 %，3.85 ~ 7.00。不传则由 Mock 模式生成 */
    @DecimalMin(value = "3.85", message = "利率不能低于 3.85%")
    @DecimalMax(value = "7.00", message = "利率不能高于 7.00%")
    private BigDecimal interestRate;

    /** 核定额度（元）。不传则由 Mock 模式按申请总额的 80%~100% 计算 */
    @DecimalMin(value = "0", message = "核定额度不能为负数")
    private BigDecimal decisionAmount;

    /** 审批意见。拒绝时必填（跨字段规则，所以在 Service 里校验） */
    @Size(max = 500, message = "审批意见不能超过 500 个字符")
    private String remark;
}
