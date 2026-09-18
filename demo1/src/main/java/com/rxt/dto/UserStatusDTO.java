package com.rxt.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 启用/禁用用户入参 —— {@code PUT /api/admin/user/{id}/status}（概要设计 5.2.7）。
 *
 * <p>{@code @NotNull} 而不是 {@code @NotBlank}：因为 status 是数字。
 * 用错注解编译器不报错，但运行时校验会失效（{@code @NotBlank} 只能用在 CharSequence 上），
 * 表现是"少传 status 也不报错，然后 NPE"。
 *
 * <p><b>为什么不做成 {@code {"enabled": true}} 这种布尔值？</b>
 * 因为 t_user.status 有四个取值（0 待审 / 1 正常 / 2 拒绝 / 3 禁用），
 * 布尔值只能表达两种。用数字 status 可以将来扩展成"3 禁用"，
 * 而且和用户列表接口返回的 status 字段名保持一致，前端不用做字段名映射。
 *
 * <p>具体的合法取值（只允许 1 或 3）在 Service 里校验——DTO 层不该知道业务规则，
 * 它只负责"格式对不对"。
 */
@Data
public class UserStatusDTO {

    /** 目标状态：1 正常 / 3 已禁用 */
    @NotNull(message = "状态不能为空")
    private Integer status;
}
