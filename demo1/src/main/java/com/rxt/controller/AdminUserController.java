package com.rxt.controller;

import com.rxt.common.PageResult;
import com.rxt.common.Result;
import com.rxt.dto.UserStatusDTO;
import com.rxt.security.SecurityUtils;
import com.rxt.service.AdminService;
import com.rxt.vo.UserVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 用户管理接口 —— M7（SRS 表38 / 表59），2 个接口。
 *
 * <p><b>权限做了两层，是刻意的"双保险"：</b>
 * <ol>
 *   <li>URL 层：{@code SecurityConfig} 里 {@code .requestMatchers("/api/admin/**").hasRole("admin")}；</li>
 *   <li>方法层：类上的 {@code @PreAuthorize("hasRole('admin')")}。</li>
 * </ol>
 * 看起来重复，但防的是不同问题：URL 规则靠字符串匹配路径，
 * 哪天有人加了 {@code @RequestMapping} 前缀或改了包里的映射，
 * 路径规则可能悄悄不生效；方法级注解跟着代码走，改不丢。
 * 这类"授权失败会导致越权"的地方，冗余一层是值得的。
 */
@Tag(name = "02. M7 用户管理", description = "管理员查看/筛选用户，启用或禁用账号（2 个接口）")
@RestController
@RequestMapping("/api/admin/user")
@RequiredArgsConstructor
@PreAuthorize("hasRole('admin')")
public class AdminUserController {

    private final AdminService adminService;

    @Operation(summary = "用户列表（分页+筛选）",
            description = "专家审核页复用它取待审核列表：传 role=expert&status=0。"
                    + "keyword 模糊匹配手机号/姓名/邮箱。")
    @GetMapping("/list")
    public Result<PageResult<UserVO>> list(@RequestParam(defaultValue = "1") int page,
                                           @RequestParam(defaultValue = "10") int size,
                                           @RequestParam(required = false) String role,
                                           @RequestParam(required = false) Integer status,
                                           @RequestParam(required = false) String keyword) {
        return Result.ok(adminService.pageUsers(page, size, role, status, keyword));
    }

    @Operation(summary = "启用/禁用用户",
            description = "status 只能传 1（正常）或 3（已禁用）。管理员不能禁用自己的账号。")
    @PutMapping("/{id}/status")
    public Result<Void> changeStatus(@PathVariable Long id,
                                     @Valid @RequestBody UserStatusDTO dto) {
        // 操作人从 Token 里取，而不是从请求体里读 —— 否则前端可以伪造成别人的 id
        adminService.changeUserStatus(SecurityUtils.currentUserId(), id, dto.getStatus());
        return Result.ok(dto.getStatus() == 3 ? "已禁用该账号" : "已启用该账号", null);
    }
}
