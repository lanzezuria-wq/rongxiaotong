package com.rxt.controller;

import com.rxt.common.PageResult;
import com.rxt.common.Result;
import com.rxt.common.constant.AuditConst;
import com.rxt.dto.AuditDTO;
import com.rxt.security.SecurityUtils;
import com.rxt.service.AdminService;
import com.rxt.vo.ArticleVO;
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
 * 文章接口 —— 这里只放 P1 需要的两个。
 *
 * <p><b>⚠️ 这个类为什么值得单独说明？因为它涉及一次"跨模块的接口归属问题"。</b>
 *
 * <p>P1 的 M7 要交付 8 个接口，但翻开 SRS 表59（系统管理模块 API），
 * 里面<b>只有 7 个</b>：用户列表、用户启停、专家审核、字典查询、字典增删改。
 * 那第 8 个在哪？
 *
 * <p>答案是它藏在 <b>表58（专家助力模块 API）</b>里：
 * {@code PUT /api/article/{id}/audit 审核文章（管理员）}。
 * 也就是说，这个接口虽然由管理员使用、属于系统管理职能，
 * 但按 URL 归属被划在了专家模块下——<b>不在 {@code /api/admin/**} 里</b>。
 *
 * <p>这带来两个实际影响：
 * <ol>
 *   <li><b>权限配置容易漏</b>：{@code /api/admin/**} 那条管理员规则覆盖不到它，
 *       必须在 SecurityConfig 里像现在这样单独写一条针对
 *       {@code PUT /api/article/{id}/audit} 的管理员规则。
 *       漏了的话，任何登录用户都能审核文章；</li>
 *   <li><b>会和 P4 撞车</b>：表58 整体是专家模块的交付范围，
 *       连同 {@code GET /api/article/list} 一起。P1 实现这两个只是为了
 *       让"文章审核"这个页面有数据可看。<b>需要和 P4 同学确认谁来实现、谁拥有。</b></li>
 * </ol>
 */
@Tag(name = "05. 文章审核", description = "文章列表（公开）+ 文章审核（管理员）。表58 定义的接口，P1 只实现这两个")
@RestController
@RequestMapping("/api/article")
@RequiredArgsConstructor
public class ArticleController {

    private final AdminService adminService;

    @Operation(summary = "文章列表（分页+筛选）",
            description = "表58 定义该接口认证为「×」，游客可读。"
                    + "审核页传 status=0 取待审核列表。返回体含正文，方便审核弹窗直接展示。")
    @GetMapping("/list")
    public Result<PageResult<ArticleVO>> list(@RequestParam(defaultValue = "1") int page,
                                              @RequestParam(defaultValue = "10") int size,
                                              @RequestParam(required = false) Integer status,
                                              @RequestParam(required = false) String keyword) {
        return Result.ok(adminService.pageArticles(page, size, status, keyword));
    }

    @Operation(summary = "审核文章",
            description = "仅管理员。status 传 APPROVED 或 REJECTED；拒绝时 remark 必填。"
                    + "注意：本接口路径不在 /api/admin/** 下，而是 /api/article/{id}/audit")
    @PutMapping("/{id}/audit")
    @PreAuthorize("hasRole('admin')")
    public Result<Void> audit(@PathVariable Long id,
                             @Valid @RequestBody AuditDTO dto) {
        adminService.auditArticle(SecurityUtils.currentUserId(), id, dto);
        return Result.ok(AuditConst.isApproved(dto.getStatus()) ? "文章已通过审核并发布" : "已拒绝该文章", null);
    }
}
