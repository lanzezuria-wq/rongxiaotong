package com.rxt.controller;

import com.rxt.assist.dto.ArticleCommentDTO;
import com.rxt.assist.dto.ArticlePublishDTO;
import com.rxt.assist.service.ArticleService;
import com.rxt.assist.vo.ArticleDetailVO;
import com.rxt.assist.vo.LikeResultVO;
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
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 文章接口 —— M5 专家助力（文章部分）。
 *
 * <p><b>⚠️ 这个类有两个作者，改动前请先看清哪一半是谁的。</b>
 *
 * <table border="1">
 *   <tr><th>接口</th><th>谁实现</th><th>为什么</th></tr>
 *   <tr>
 *     <td>{@code GET /api/article/list}<br>{@code PUT /api/article/{id}/audit}</td>
 *     <td>P1（{@link AdminService}）</td>
 *     <td>管理员的"文章审核"页需要数据来源与审核动作。这两个接口虽然按 URL
 *         归属专家模块（SRS 表58），但职能是系统管理。</td>
 *   </tr>
 *   <tr>
 *     <td>{@code GET /api/article/{id}}<br>{@code POST /api/article}<br>
 *         {@code PUT /api/article/{id}/like}<br>{@code POST /api/article/{id}/comment}</td>
 *     <td>P3（{@link ArticleService}）</td>
 *     <td>真正属于 M5 的 4 个接口，由 P3 补齐。</td>
 *   </tr>
 * </table>
 *
 * <p><b>为什么不再新建一个 Controller 而要改这个类？</b>
 * 因为同一个 {@code @RequestMapping("/api/article")} 分散到两个类里，
 * 排查"这个 URL 到底谁在处理"时要满项目搜；而且两边的 Swagger 分组会打架。
 * 放在一个类里、用注释标清归属，比"分成两个文件"更容易维护。
 *
 * <p><b>P1 原本的一条历史遗留说明（仍然成立）：</b>
 * {@code PUT /api/article/{id}/audit} 按 URL 归属在专家模块下、
 * 但不在 {@code /api/admin/**} 里，所以 {@code SecurityConfig} 必须为它<b>单独</b>
 * 写一条管理员规则 —— {@code /api/admin/**} 那条覆盖不到它，漏了的话任何登录用户都能审核文章。
 *
 * <p><b>权限配置一览（{@code SecurityConfig} 里，改一处要连带改另一处）：</b>
 * <pre>
 *   PUT    /api/article/{id}/audit   → hasRole("admin")   ← 在 /api/admin/** 之外，必须单独配
 *   GET    /api/article/**           → permitAll          ← 列表与详情都对游客开放
 *   其余（POST 发布、PUT 点赞、POST 评论）→ authenticated  ← 由 anyRequest() 兜住
 * </pre>
 */
@Tag(name = "05. M5 文章 / M7 文章审核",
        description = "列表与审核（P1，管理员用）+ 发布/详情/点赞/评论（P3，M5 专家助力）")
@RestController
@RequestMapping("/api/article")
@RequiredArgsConstructor
public class ArticleController {

    /** P1 的实现：文章列表与审核 */
    private final AdminService adminService;
    /** P3 的实现：发布、详情、点赞、评论 */
    private final ArticleService articleService;

    // ==================================================================================
    // P1 部分：文章列表 + 审核（保持原样，一行未动）
    // ==================================================================================

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

    // ==================================================================================
    // P3 部分：M5 专家助力的 4 个接口
    // ==================================================================================

    @Operation(summary = "文章详情",
            description = "游客可读（不用带 Token）。返回正文、标签数组、当前用户是否已点赞（isLiked）、"
                    + "评论列表（最近 50 条）与作者姓名。每次访问浏览量 +1。"
                    + "⚠️ 未发布/被拒/已下架的文章只有作者本人和管理员能看，其他人返回 4002 —— "
                    + "否则猜个 id 就能看到还在审核中的稿子。")
    @GetMapping("/{id}")
    public Result<ArticleDetailVO> detail(@PathVariable Long id) {
        // 详情对游客开放，所以这里不能用 SecurityUtils.currentUserId()
        // —— 那会在未登录时抛异常。用 OrNull 版本拿到"可能为 null"的身份，
        // 由 Service 处理游客分支（isLiked 恒为 0）。
        return Result.ok(articleService.detail(id,
                SecurityUtils.currentUserIdOrNull(),
                SecurityUtils.currentRoleOrNull()));
    }

    @Operation(summary = "发布文章",
            description = "农户和专家可发布，状态由角色决定且前端无法覆盖："
                    + "农户 → 0 待审核（需管理员审核）；专家 → 1 已发布（直接生效）。"
                    + "分类接受契约枚举（planting/breeding/market/policy/experience）"
                    + "或数据字典里已启用的分类编码。标签最多 6 个。")
    @PostMapping
    public Result<Map<String, Long>> publish(@Valid @RequestBody ArticlePublishDTO dto) {
        var loginUser = SecurityUtils.currentUser();
        Long id = articleService.publish(loginUser.getUserId(), loginUser.getRole(), dto);
        return Result.ok("expert".equals(loginUser.getRole())
                ? "发布成功，文章已生效" : "发布成功，等待管理员审核后展示", Map.of("id", id));
    }

    @Operation(summary = "点赞 / 取消点赞（可切换）",
            description = "同一个人对同一篇文章再点一次就是取消点赞。"
                    + "返回本次操作后的最终状态与点赞总数（isLiked / likeCount），"
                    + "前端直接覆盖本地显示即可，不要自己 ±1（重试或多端操作时会错位）。"
                    + "注意：契约里这个接口的 data 未定义，本项目多返回了这两个字段，前端可忽略。")
    @PutMapping("/{id}/like")
    public Result<LikeResultVO> like(@PathVariable Long id) {
        LikeResultVO vo = articleService.toggleLike(SecurityUtils.currentUserId(), id);
        return Result.ok(vo.getIsLiked() == 1 ? "点赞成功" : "已取消点赞", vo);
    }

    @Operation(summary = "发表评论",
            description = "需要登录。内容上限 200 字。只有已发布的文章能评论"
                    + "（给还没过审的文章留言，等于让评论预支一个可能被拒的结论）。")
    @PostMapping("/{id}/comment")
    public Result<Void> comment(@PathVariable Long id,
                               @Valid @RequestBody ArticleCommentDTO dto) {
        articleService.comment(SecurityUtils.currentUserId(), id, dto);
        return Result.ok("评论发表成功", null);
    }
}
