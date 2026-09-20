package com.rxt.finance.controller;

import com.rxt.common.Result;
import com.rxt.finance.dto.BankApproveDTO;
import com.rxt.finance.service.BankService;
import com.rxt.finance.vo.BankGroupVO;
import com.rxt.finance.vo.BankStatsVO;
import com.rxt.security.SecurityUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 融资管理接口（银行端）—— M6（3 个接口）。
 *
 * <p><b>权限是三层冗余的</b>，挡的是不同的事：
 * <ul>
 *   <li>{@code SecurityConfig} 里的 {@code /api/bank/** → hasRole("bank")} ——
 *       请求进不来（最早、最省资源的拦截）；</li>
 *   <li>类上的 {@code @PreAuthorize} —— 方法级兜底。万一将来有人为了放行某个新接口
 *       而把 {@code /api/bank/**} 改成 {@code authenticated()}，注解仍然拦得住。
 *       这是"防御性冗余"而不是重复劳动；</li>
 *   <li>{@code BankServiceImpl#requireBankUser} 里查库 —— 挡住"Token 里的角色是签发时的
 *       快照、而现在角色已经被管理员改掉"这种过期权限的情况。</li>
 * </ul>
 */
@Tag(name = "12. M6 融资管理(银行)", description = "银行端联保小组审批与统计（3 个接口，仅 bank 角色）")
@RestController
@RequestMapping("/api/bank")
@RequiredArgsConstructor
@PreAuthorize("hasRole('bank')")
public class BankController {

    private final BankService bankService;

    @Operation(summary = "银行端联保小组列表",
            description = "默认返回所有「已成型」的小组（不含还在招募、人数不足 3 人的）。"
                    + "status 传了就精确筛选：1 已匹配（待审批）/ 2 待银行审批 / 3 已通过 / 4 已拒绝。"
                    + "每条记录都带成员名单（含申请金额与地域），供审批时逐项核验。"
                    + "排序：待处理的排前面。")
    @GetMapping("/group/list")
    public Result<List<BankGroupVO>> groupList(@RequestParam(required = false) Integer status) {
        return Result.ok(bankService.groupList(status));
    }

    @Operation(summary = "审批联保小组",
            description = "result 传 APPROVED（通过）或 REJECTED（拒绝），拒绝时 remark 必填。"
                    + "creditScore / interestRate / decisionAmount 三项可选 —— "
                    + "Mock 模式开启时缺什么自动生成（信用分 60~100、利率 3.85~7.00、"
                    + "核定额度按申请总额的 80%~100%），关闭时必须传全。"
                    + "审批会同时更新小组与组内所有融资申请的状态，且不可撤回（重复审批返回 3006）。")
    @PutMapping("/approve")
    public Result<Void> approve(@Valid @RequestBody BankApproveDTO dto) {
        bankService.approve(SecurityUtils.currentUserId(), dto);
        return Result.ok("REJECTED".equalsIgnoreCase(dto.getResult())
                ? "已拒绝该联保小组的融资申请" : "审批通过，已通知组内全部农户", null);
    }

    @Operation(summary = "银行端统计",
            description = "审批员的工作看板：小组总数、待审批数、已通过数、已拒绝数、已通过核定额度合计。"
                    + "实时统计（不缓存），保证刚审完的数字立刻生效。无数据时合计为 0 而不是 null。")
    @GetMapping("/stats")
    public Result<BankStatsVO> stats() {
        return Result.ok(bankService.stats());
    }
}
