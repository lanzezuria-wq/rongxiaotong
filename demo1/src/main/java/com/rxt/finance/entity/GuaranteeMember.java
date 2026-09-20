package com.rxt.finance.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 联保小组成员 —— 对应 t_guarantee_member（M4）。
 *
 * <p>它是一张<b>关联表 + 快照表</b>：{@code (groupId, userId)} 是关联键
 * （上面有唯一索引，防止同一人重复加入同一小组），而
 * {@code applyAmount / industry / province / city} 是从融资申请复制过来的<b>快照</b>。
 *
 * <p><b>为什么要冗余这些字段，不直接连 t_financing_apply 查？</b>
 * 一是性能：银行审批页要把整个小组一次性摊开看，连表或 N+1 查询在 10 人小组上
 * 就是 10 次子查询。二是更重要的<b>审批快照语义</b>：
 * 银行看到的是"提交审批那一刻的申请信息"，农户事后改了申请金额，
 * 已提交的审批材料不应该跟着变 —— 这是金融材料留痕的基本要求。
 *
 * <p>没有 {@code deleted} 属性 → MyBatis-Plus 不会往查询里拼逻辑删除条件
 * （表里也没这一列，拼了会直接报 Unknown column）。
 * 退出小组是物理删除，理由见 init_p3.sql 的列注释。
 */
@Data
@TableName("t_guarantee_member")
public class GuaranteeMember {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 联保小组 t_guarantee_group.id */
    private Long groupId;

    /** 成员 t_user.id */
    private Long userId;

    /** 对应的融资申请 t_financing_apply.id */
    private Long applyId;

    /** 该成员的申请金额（快照） */
    private BigDecimal applyAmount;

    /** 冗余的行业/地域（快照），便于银行端直接展示而不用回查申请 */
    private String industry;
    private String province;
    private String city;

    /** 0 申请中（预留）/ 1 已加入 / 2 已拒绝（预留） */
    private Integer joinStatus;

    private LocalDateTime joinedAt;

    private LocalDateTime createTime;
}
