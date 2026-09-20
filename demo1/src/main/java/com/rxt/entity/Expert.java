package com.rxt.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 专家扩展信息 —— 对应 t_expert。
 *
 * <p><b>为什么专家信息不直接加在 t_user 上？</b>
 * 因为 t_user 是五类角色共用的主表。如果把"职称、所属机构、专业领域、资质证明"
 * 都加成列，那么农户、买家、银行、管理员的记录里这 6 列永远是 NULL——
 * 这叫"稀疏列"，是表设计的浪费，而且每加一种角色就要改主表结构。
 *
 * <p>拆出来之后：<b>只有在管理员审核通过专家申请时才插入一行</b>
 * （SRS 表39："专家审核通过后，写入 t_expert 表"）。
 * 所以判断"这个用户是不是已通过的专家"，看 t_expert 里有没有他的行就够了。
 *
 * <p><b>注意这个实体没有 {@code deleted} 字段</b>，而 t_expert 表也确实没有逻辑删除列。
 * 这是有意为之：MyBatis-Plus 的全局逻辑删除配置是按"实体里有没有叫 deleted 的属性"
 * 来决定要不要往 SQL 里拼 {@code AND deleted=0} 的，没有这个属性就不会拼，
 * 所以不会出现"表里没这列却去查这列"的 SQL 错误。
 */
@Data
@TableName("t_expert")
public class Expert {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 关联 t_user.id。逻辑外键，不建数据库约束 */
    private Long userId;

    private String realName;
    /** 职称，如"高级农艺师" */
    private String title;
    /** 所属机构 */
    private String org;
    /** 专业领域 */
    private String domain;
    /** 个人简介 */
    private String intro;
    /** 资质证明文件地址 */
    private String certUrl;

    /**
     * 评分 0.00~5.00（P3 补列，见 init_p3.sql）。
     *
     * <p>初始给 5.00 而不是 0：新专家还没被评价过时，按评分排序会永远沉在最后，
     * 等于被隐藏。给满分是"中性偏好"的处理，等真有评价数据后再由评价流程覆盖。
     */
    private java.math.BigDecimal rating;

    /**
     * 已回答问题数（P3 补列）。
     *
     * <p>专家列表页要显示"已回答 N 个问题" —— 这是农户判断专家是否活跃的主要依据，
     * 比职称更直观。由 {@code com.rxt.assist.service.impl.ConsultServiceImpl#answer} 在回答时 +1。
     */
    private Integer answerCount;

    /** 审核意见 */
    private String auditRemark;
    /** 审核人 t_user.id */
    private Long auditBy;
    private LocalDateTime auditTime;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
