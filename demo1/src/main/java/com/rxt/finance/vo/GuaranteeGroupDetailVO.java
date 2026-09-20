package com.rxt.finance.vo;

import com.rxt.finance.entity.GuaranteeGroup;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;

/**
 * 联保小组详情出参 —— 对应契约里的 {@code GuaranteeGroupDetailVO}。
 *
 * <p>在摘要基础上多一个 {@code members}。契约原文是
 * {@code allOf: [GuaranteeGroupVO, {members}]}，所以用继承实现。
 * （继承必须带 {@code callSuper = true}，理由见 {@code FinancingApplyDetailVO}。）
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class GuaranteeGroupDetailVO extends GuaranteeGroupVO {

    /** 组内成员列表。含"申请中"和"已加入"两种 joinStatus 的记录 */
    private List<GroupMemberVO> members;

    public static GuaranteeGroupDetailVO from(GuaranteeGroup group, List<GroupMemberVO> members) {
        if (group == null) {
            return null;
        }
        GuaranteeGroupDetailVO vo = new GuaranteeGroupDetailVO();
        // 复用父类的字段拷贝（父类里一处维护，见 GuaranteeGroupVO#copyBaseTo）
        GuaranteeGroupVO.from(group).copyBaseTo(vo);
        vo.setMembers(members == null ? List.of() : members);
        return vo;
    }
}
