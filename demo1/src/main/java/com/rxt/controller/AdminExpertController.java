package com.rxt.controller;

import com.rxt.common.Result;
import com.rxt.common.constant.AuditConst;
import com.rxt.dto.AuditDTO;
import com.rxt.security.SecurityUtils;
import com.rxt.service.AdminService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 专家账号审核接口 —— M7（SRS 表39 / 表59），1 个接口。
 *
 * <p><b>路径里的 {@code {id}} 是 t_user.id，不是 t_expert.id。</b>
 * 这一点必须说清楚，否则和 P4 同学联调时一定会错位：
 * 待审核的专家在 t_user 里有账号（status=0），但 t_expert 里<b>还没有行</b>
 * ——因为 t_expert 是"审核通过后才写入"的（表39 原文）。
 * 既然待审核状态下拿不到 t_expert.id，这里只能是用户 id。
 *
 * <p>前端审核页的"待审核列表"数据来自
 * {@code GET /api/admin/user/list?role=expert&status=0}，
 * 那一页返回的每条记录的 {@code id} 正是这里要传的值。
 */
@Tag(name = "03. M7 专家审核", description = "管理员审核专家注册申请（1 个接口）")
@RestController
@RequestMapping("/api/admin/expert")
@RequiredArgsConstructor
@PreAuthorize("hasRole('admin')")
public class AdminExpertController {

    private final AdminService adminService;

    @Operation(summary = "审核专家账号",
            description = "status 传 APPROVED（通过，t_user.status→1 并写入 t_expert）"
                    + "或 REJECTED（拒绝，t_user.status→2）。拒绝时 remark 必填。"
                    + "审核结果不可撤回，只能审核 status=0 的账号。")
    @PutMapping("/{id}/audit")
    public Result<Void> audit(@PathVariable Long id,
                             @Valid @RequestBody AuditDTO dto) {
        adminService.auditExpert(SecurityUtils.currentUserId(), id, dto);
        return Result.ok(AuditConst.isApproved(dto.getStatus())
                ? "审核通过，该专家账号已激活"
                : "已拒绝该专家的申请", null);
    }
}
