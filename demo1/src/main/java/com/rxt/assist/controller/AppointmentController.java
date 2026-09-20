package com.rxt.assist.controller;

import com.rxt.assist.dto.AppointmentDTO;
import com.rxt.assist.dto.AppointmentHandleDTO;
import com.rxt.assist.service.ConsultService;
import com.rxt.assist.vo.AppointmentVO;
import com.rxt.common.Result;
import com.rxt.security.LoginUser;
import com.rxt.security.SecurityUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 专家预约接口 —— M5 专家助力（3 个接口）。
 *
 * <p>预约的"时间"字段在 DTO 里是 String，由 Service 用 {@code DateUtils.parse} 解析。
 * 这样做的好处是错误信息可控（"预约时间格式不正确，应形如 2026-09-25 09:30:00"），
 * 而不是抛一个 Jackson 的英文反序列化异常。
 */
@Tag(name = "15. M5 专家预约", description = "发起预约 + 专家接受/拒绝（3 个接口）")
@RestController
@RequestMapping("/api/appointment")
@RequiredArgsConstructor
public class AppointmentController {

    private final ConsultService consultService;

    @Operation(summary = "发起预约",
            description = "type=online（线上）必须填 meetingLink，type=offline（线下）必须填 meetingPlace。"
                    + "appointmentTime 格式 'yyyy-MM-dd HH:mm:ss'（也兼容 ISO 的带 T 格式），"
                    + "必须晚于当前时间。expertId 是 t_expert.id，不是 t_user.id。不能预约自己。")
    @PostMapping
    public Result<Map<String, Long>> create(@Valid @RequestBody AppointmentDTO dto) {
        Long id = consultService.createAppointment(SecurityUtils.currentUserId(), dto);
        return Result.ok("预约已提交，等待专家确认", Map.of("id", id));
    }

    @Operation(summary = "预约列表",
            description = "⚠️ 同一个接口按角色返回不同视角：普通用户看「我发起的预约」；"
                    + "专家看「约给我的 + 我发起的」。"
                    + "排序：待处理（PENDING）在前，然后按预约时间倒序。")
    @GetMapping("/list")
    public Result<List<AppointmentVO>> list() {
        LoginUser loginUser = SecurityUtils.currentUser();
        return Result.ok(consultService.myAppointments(loginUser.getUserId(), loginUser.getRole()));
    }

    @Operation(summary = "专家接受/拒绝预约",
            description = "只有被预约的那位专家本人能处理。"
                    + "status 传 ACCEPTED 或 REJECTED；已经是 ACCEPTED/REJECTED 的预约"
                    + "不能再处理（返回 4005，与审核结果不可撤回同一条原则）。")
    @PutMapping("/{id}/handle")
    public Result<Void> handle(@PathVariable Long id,
                              @Valid @RequestBody AppointmentHandleDTO dto) {
        consultService.handleAppointment(SecurityUtils.currentUserId(), id, dto);
        return Result.ok("ACCEPTED".equalsIgnoreCase(dto.getStatus())
                ? "已接受预约，请按约定时间提供服务" : "已拒绝该预约", null);
    }
}
