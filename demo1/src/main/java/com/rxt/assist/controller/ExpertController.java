package com.rxt.assist.controller;

import com.rxt.assist.service.ExpertService;
import com.rxt.assist.vo.ExpertCardVO;
import com.rxt.assist.vo.ExpertProfileVO;
import com.rxt.common.PageResult;
import com.rxt.common.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 专家列表与详情接口 —— M5 专家助力（2 个接口）。
 *
 * <p><b>两个接口都对游客开放</b>（{@code SecurityConfig} 里
 * {@code GET /api/expert/**} 是 permitAll）。依据是 SRS 表58：
 * 专家列表与文章列表的"认证"列都标了"×" —— 农户在注册登录之前
 * 应该能看到"这个平台上有哪些专家"，这是产品获客路径的一部分。
 *
 * <p>但这两个接口<b>不需要身份</b>：返回的都是公开信息
 * （{@code certUrl} 这种审核材料已经刻意不返回了，见 {@code ExpertCardVO} 的注释）。
 * 一个不需要身份的接口就不应该去读 SecurityContext ——
 * 那样万一以后有人改了放行规则，游客调用会 NPE 而不是正常工作。
 *
 * <p><b>路径里的 {@code {id}} 是 t_expert.id</b>，与 P1 的
 * {@code /api/admin/expert/{id}/audit} 不同（那个是 t_user.id）。
 * 这是全项目最容易混的一处，联调时务必核对：
 * 列表接口返回的 {@code id} 就是要传给详情/提问/预约接口的值。
 */
@Tag(name = "13. M5 专家列表", description = "已审核专家列表与详情（2 个接口，游客可访问）")
@RestController
@RequestMapping("/api/expert")
@RequiredArgsConstructor
public class ExpertController {

    private final ExpertService expertService;

    @Operation(summary = "已审核专家列表",
            description = "只返回「t_expert 有资质记录 且 账号状态正常」的专家 —— "
                    + "还没通过审核的专家不会出现在这里。"
                    + "specialty 是专业领域关键词，模糊匹配 t_expert.domain。"
                    + "排序：评分高的在前。返回的 id 是 t_expert.id，后续接口都用它。")
    @GetMapping("/list")
    public Result<PageResult<ExpertCardVO>> list(@RequestParam(defaultValue = "1") int page,
                                                @RequestParam(defaultValue = "10") int size,
                                                @RequestParam(required = false) String specialty) {
        return Result.ok(expertService.pageExperts(specialty, page, size));
    }

    @Operation(summary = "专家详情",
            description = "比列表多「个人简介」和「最近发布的文章（最多 5 篇）」。"
                    + "账号被禁用的专家详情返回 4001。注意：这里的 id 是 t_expert.id。")
    @GetMapping("/{id}")
    public Result<ExpertProfileVO> detail(@PathVariable Long id) {
        return Result.ok(expertService.expertDetail(id));
    }
}
