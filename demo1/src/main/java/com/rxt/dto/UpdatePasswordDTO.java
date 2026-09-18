package com.rxt.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 修改密码入参 —— 对应 PUT /api/user/password（概要设计 5.2.3）。
 *
 * <p>SRS 表13 的三条业务规则，分别落在不同层次：
 * <table border="1">
 *   <tr><th>规则</th><th>落在哪里</th></tr>
 *   <tr><td>新密码长度 6-20 位</td><td><b>DTO 层</b>（@Size）——格式问题不需要查库就能判</td></tr>
 *   <tr><td>原密码必须正确</td><td>Service 层——必须查库做 BCrypt 比对</td></tr>
 *   <tr><td>新密码不能与原密码相同</td><td>Service 层——要拿原密码比对，DTO 里判不了</td></tr>
 * </table>
 *
 * <p>"新密码两次不一致"是前端的事，和注册一样，不进接口契约。
 *
 * <p>⚠️ SRS 表13 还要求"修改成功，强制下线，重新登录"。当前实现只能靠前端清掉
 * localStorage 里的 Token；JWT 是无状态的，服务端主动作废必须引入黑名单（Redis / 表），
 * 不在 P1 范围内。这一点要在联调时和前端讲清。
 */
@Data
public class UpdatePasswordDTO {

    @NotBlank(message = "原密码不能为空")
    private String oldPassword;

    @NotBlank(message = "新密码不能为空")
    @Size(min = 6, max = 20, message = "新密码长度需为 6-20 位")
    private String newPassword;
}
