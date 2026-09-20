package com.rxt.finance.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 联保小组 —— 对应 t_guarantee_group（M4）。
 *
 * <p>这是整个融资模型的枢纽：银行审的不是个人而是小组，所以
 * {@code t_bank_approval} 挂在 {@code groupId} 上而不是 {@code applyId} 上。
 * 一个小组被审批通过，组内所有成员的申请一起变成"已通过"。
 *
 * <p><b>{@code totalAmount} 和 {@code currentMembers} 是"冗余计数列"</b>：
 * 它们都能通过 {@code SUM/COUNT(t_guarantee_member)} 算出来，但仍然冗余存储。
 * 原因是银行端列表要展示"总申请额 / 已凑人数"，如果每行都去 count 子查询，
 * 一页 10 行就是 20 次额外查询。
 * 代价是每次成员变动必须同步更新这两列 —— 这由
 * {@code FinancingServiceImpl#syncGroupState} <b>统一负责</b>，
 * 而且它是"重新 SUM/COUNT"而不是"±1"，所以是幂等的。
 * <b>不允许其它地方直接改这两列</b>，否则就真的不一致了。
 *
 * <p>{@code groupNo} 是给人看/念的编号（客服在电话里报它），{@code id} 是给程序用的主键。
 * 编号里带自增主键，所以是"先 insert 拿 id、再回填编号"，字段因此允许为 NULL。
 */
@Data
@TableName("t_guarantee_group")
public class GuaranteeGroup {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 小组编号，形如 LB-20260920-000123。插入时为空，拿到 id 后回填 */
    private String groupNo;

    /** 发起人 t_user.id */
    private Long creatorId;

    /** 所属行业，同行业才能互保（风险同源、便于互相监督） */
    private String industry;

    private String province;
    private String city;

    /** 组内申请金额合计（冗余，由成员表汇总刷新） */
    private BigDecimal totalAmount;

    /** 当前成员数（冗余，由成员表汇总刷新）。业务范围 3~10 */
    private Integer currentMembers;

    /** 审批银行 t_user.id */
    private Long bankId;

    /** 0 招募中 / 1 已匹配 / 2 待银行审批（预留）/ 3 已通过 / 4 已拒绝 */
    private Integer status;

    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
