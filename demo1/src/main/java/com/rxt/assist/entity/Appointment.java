package com.rxt.assist.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 专家预约 —— 对应 t_appointment（M5）。
 *
 * <p>{@code type=online} 必须填 {@code meetingLink}，{@code type=offline}
 * 必须填 {@code meetingPlace}。这是一条<b>跨字段的条件必填</b>规则，
 * 和"审核拒绝时必须填理由"是同一类问题：Bean Validation 的注解表达不了
 * "取决于另一个字段的值才知道本字段要不要必填"，所以校验写在 Service 里。
 *
 * <p>{@code status} 直接存 {@code PENDING / ACCEPTED / REJECTED} 字符串
 * （见 {@code AppointmentConst} 里对"为什么用字符串不用数字"的说明）。
 */
@Data
@TableName("t_appointment")
public class Appointment {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 预约人 t_user.id */
    private Long userId;

    /** 被预约专家 t_expert.id（注意不是 t_user.id） */
    private Long expertId;

    /** online 线上 / offline 线下 */
    private String type;

    /** 预约时间，必须晚于当前时间 */
    private LocalDateTime appointmentTime;

    private String phone;

    private String description;

    /** 线上会议链接，type=online 时必填 */
    private String meetingLink;

    /** 线下见面地点，type=offline 时必填 */
    private String meetingPlace;

    /** PENDING 待处理 / ACCEPTED 已接受 / REJECTED 已拒绝 */
    private String status;

    private LocalDateTime handledAt;

    private LocalDateTime createTime;
}
