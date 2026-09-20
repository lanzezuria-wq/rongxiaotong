package com.rxt.finance.vo;

import com.rxt.common.util.DateUtils;
import com.rxt.finance.entity.GuaranteeMember;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 联保小组成员出参 —— 契约里 {@code GuaranteeGroupDetailVO.members} 的元素。
 *
 * <p>字段名按契约来：{@code userId / username / applyAmount / industry / joinStatus}。
 *
 * <p><b>一处契约自身的不一致：</b>农户端那个成员对象没有 {@code province}，
 * 而银行端的 {@code BankGroupVO.members} <b>多一个 province</b> ——
 * 同一个"成员"在契约里定义了两遍、字段还不一样。
 * 本项目的处理：<b>只定义这一个 VO（带 province）</b>，
 * {@code BankGroupVO} 直接复用它。多返回一个 province 不会破坏农户端前端
 * （它不读这个字段就是了），却省掉了一个几乎重复的类。<b>已登记在契约偏差清单里。</b>
 *
 * <p>{@code username} 不是数据库字段 —— t_guarantee_member 里只有 {@code user_id}。
 * 由 Service 批量查用户后回填，<b>一次 {@code selectBatchIds} 而不是每人查一次</b>，
 * 避免 10 人小组产生 10 次查询（N+1）。
 */
@Data
public class GroupMemberVO {

    private Long userId;

    /** 成员姓名，由 Service 批量回填 */
    private String username;

    private BigDecimal applyAmount;

    private String industry;

    /** 契约里农户端的成员对象没有这个字段，银行端有。本项目统一带上，见类注释 */
    private String province;

    private String city;

    /** 0 申请中 / 1 已加入 / 2 已拒绝 */
    private Integer joinStatus;

    private String joinedAt;

    public static GroupMemberVO from(GuaranteeMember member) {
        if (member == null) {
            return null;
        }
        GroupMemberVO vo = new GroupMemberVO();
        vo.setUserId(member.getUserId());
        vo.setApplyAmount(member.getApplyAmount());
        vo.setIndustry(member.getIndustry());
        vo.setProvince(member.getProvince());
        vo.setCity(member.getCity());
        vo.setJoinStatus(member.getJoinStatus());
        vo.setJoinedAt(DateUtils.format(member.getJoinedAt()));
        // username 由 Service 回填
        return vo;
    }
}
