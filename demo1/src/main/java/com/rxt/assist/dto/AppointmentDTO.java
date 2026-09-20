package com.rxt.assist.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 发起预约入参 —— {@code POST /api/appointment}（M5）。
 *
 * <p><b>{@code appointmentTime} 用 {@code String} 接收而不是 {@code LocalDateTime}，
 * 这是刻意的：</b>项目里"输入用字符串 + Service 里显式解析"是统一做法
 * （见 {@code DateUtils.parse} 的注释）。如果直接用 {@code LocalDateTime}，
 * 解析完全交给 Jackson —— 而 Boot 4 的 Web 层换成了 Jackson 3，对时间格式的默认行为
 * 与 Jackson 2 不同，且报错是英文的 {@code Cannot deserialize value of type
 * java.time.LocalDateTime}，前端看不懂。自己解析就能返回
 * "预约时间格式不正确，应形如 2026-09-25 09:30:00"。
 *
 * <p>{@code meetingLink} / {@code meetingPlace} 都<b>不带 @NotBlank</b>，
 * 因为它们是"取决于 type 二选一必填"，Bean Validation 表达不了这种依赖，
 * 校验放在 {@code ConsultServiceImpl#createAppointment} 里。
 */
@Data
public class AppointmentDTO {

    /** 被预约专家的 t_expert.id（不是 t_user.id） */
    @NotNull(message = "专家 id 不能为空")
    private Long expertId;

    /** online 线上 / offline 线下 */
    @NotBlank(message = "预约方式不能为空")
    private String type;

    /** 预约时间，形如 2026-09-25 09:30:00，必须晚于当前时间 */
    @NotBlank(message = "预约时间不能为空")
    private String appointmentTime;

    @NotBlank(message = "联系电话不能为空")
    @Size(max = 20, message = "联系电话不能超过 20 个字符")
    private String phone;

    @NotBlank(message = "问题描述不能为空")
    @Size(max = 500, message = "问题描述不能超过 500 个字符")
    private String description;

    /** 线上会议链接。type=online 时必填 */
    @Size(max = 255, message = "会议链接不能超过 255 个字符")
    private String meetingLink;

    /** 线下见面地点。type=offline 时必填 */
    @Size(max = 255, message = "见面地点不能超过 255 个字符")
    private String meetingPlace;
}
