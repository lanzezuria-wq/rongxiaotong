package com.rxt.assist.vo;

import com.rxt.assist.constant.AppointmentConst;
import com.rxt.assist.entity.Appointment;
import com.rxt.common.util.DateUtils;
import lombok.Data;

/**
 * 专家预约出参 —— 对应契约里的 {@code AppointmentVO}。
 *
 * <p>契约字段：{@code id / expertId / expertName / type / appointmentTime / phone /
 * description / meetingLink / meetingPlace / status / createdAt / handledAt}。
 *
 * <p>本项目额外补了 {@code typeName} 和 {@code statusName}
 * （"online"→"线上"、"PENDING"→"待处理"）。理由和融资申请一样：
 * 预约列表在"我的预约"和"专家的待办"两个页面都要显示中文，
 * 后端翻译一次比前端写两份映射可控。契约没禁止多余字段，前端忽略即可。
 *
 * <p>{@code expertName} 不在 t_appointment 里，由 Service 批量回填
 * （t_expert.id → t_user.name，两步查表后用 Map 拼，避免 N+1）。
 */
@Data
public class AppointmentVO {

    private Long id;

    private Long expertId;

    /** 专家姓名，Service 回填 */
    private String expertName;

    /** online / offline */
    private String type;

    /** 中文方式名。契约外字段 */
    private String typeName;

    private String appointmentTime;

    private String phone;

    private String description;

    private String meetingLink;

    private String meetingPlace;

    /** PENDING / ACCEPTED / REJECTED */
    private String status;

    /** 中文状态名。契约外字段 */
    private String statusName;

    private String createdAt;

    private String handledAt;

    public static AppointmentVO from(Appointment appointment) {
        if (appointment == null) {
            return null;
        }
        AppointmentVO vo = new AppointmentVO();
        vo.setId(appointment.getId());
        vo.setExpertId(appointment.getExpertId());
        vo.setType(appointment.getType());
        vo.setTypeName(AppointmentConst.typeName(appointment.getType()));
        vo.setAppointmentTime(DateUtils.format(appointment.getAppointmentTime()));
        vo.setPhone(appointment.getPhone());
        vo.setDescription(appointment.getDescription());
        vo.setMeetingLink(appointment.getMeetingLink());
        vo.setMeetingPlace(appointment.getMeetingPlace());
        vo.setStatus(appointment.getStatus());
        vo.setStatusName(AppointmentConst.statusName(appointment.getStatus()));
        vo.setCreatedAt(DateUtils.format(appointment.getCreateTime()));
        vo.setHandledAt(DateUtils.format(appointment.getHandledAt()));
        // expertName 由 Service 回填
        return vo;
    }
}
