package com.rxt.finance.vo;

import com.rxt.common.util.DateUtils;
import com.rxt.finance.entity.BankApproval;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 银行审批结果出参 —— 对应契约里的 {@code BankApprovalVO}。
 *
 * <p>两个用途：融资申请详情里让农户看到自己小组的审批结论；银行审批后回显。
 *
 * <p>{@code decisionAmount} 在拒绝时为 null —— 契约里也标了 {@code nullable: true}。
 * 这是有意为之：拒绝时给 0 会让人误以为"批了 0 元"，null 才是"没有这个结论"。
 */
@Data
public class BankApprovalVO {

    /** APPROVED / REJECTED */
    private String result;

    private Integer creditScore;

    private BigDecimal interestRate;

    private BigDecimal decisionAmount;

    private String remark;

    private String approvedAt;

    public static BankApprovalVO from(BankApproval approval) {
        if (approval == null) {
            return null;
        }
        BankApprovalVO vo = new BankApprovalVO();
        vo.setResult(approval.getResult());
        vo.setCreditScore(approval.getCreditScore());
        vo.setInterestRate(approval.getInterestRate());
        vo.setDecisionAmount(approval.getDecisionAmount());
        vo.setRemark(approval.getRemark());
        vo.setApprovedAt(DateUtils.format(approval.getApprovedAt()));
        return vo;
    }
}
