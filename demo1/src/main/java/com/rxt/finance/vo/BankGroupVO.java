package com.rxt.finance.vo;

import com.rxt.finance.entity.GuaranteeGroup;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;

/**
 * 银行端联保小组出参 —— 对应契约里的 {@code BankGroupVO}。
 *
 * <p>和 {@link GuaranteeGroupDetailVO} 的差别<b>只在于返回成员的用途</b>：
 * 农户端看成员是为了"知道小组里有谁"，银行端看成员是为了"逐个核验身份与金额"，
 * 所以银行端<b>总是</b>带 members，而农户端的列表接口不带。
 *
 * <p>那为什么不干脆都用 {@code GuaranteeGroupDetailVO}？
 * 因为银行端将来会加专属字段（例如 approvedAmount、riskLevel），
 * 到时候农户端不该看到它们。现在分开，以后加字段就不用回头拆。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class BankGroupVO extends GuaranteeGroupVO {

    /** 成员列表，含申请金额与地域，供银行逐项核验 */
    private List<GroupMemberVO> members;

    public static BankGroupVO from(GuaranteeGroup group, List<GroupMemberVO> members) {
        if (group == null) {
            return null;
        }
        BankGroupVO vo = new BankGroupVO();
        GuaranteeGroupVO.from(group).copyBaseTo(vo);
        vo.setMembers(members == null ? List.of() : members);
        return vo;
    }
}
