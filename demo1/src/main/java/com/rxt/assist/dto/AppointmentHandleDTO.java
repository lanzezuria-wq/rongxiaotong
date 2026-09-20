package com.rxt.assist.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 处理预约入参 —— {@code PUT /api/appointment/{id}/handle}（M5）。
 *
 * <p>专家传 {@code ACCEPTED}（接受）或 {@code REJECTED}（拒绝）。
 * 契约里这个字段的类型就是 {@code string enum [ACCEPTED, REJECTED]}，
 * 所以这里保持字符串，取值合法性由 {@code AppointmentConst.isValidHandleStatus} 校验。
 *
 * <p>没有"拒绝理由"字段 —— 契约里没有，需求文档也没有要求。
 * 如果将来要加，直接在这里加一个可选字段即可，不影响已有前端。
 */
@Data
public class AppointmentHandleDTO {

    /** ACCEPTED 接受 / REJECTED 拒绝 */
    @NotBlank(message = "处理结果不能为空")
    private String status;
}
