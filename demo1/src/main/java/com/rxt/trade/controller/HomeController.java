package com.rxt.trade.controller;

import com.rxt.common.Result;
import com.rxt.trade.service.HomeService;
import com.rxt.trade.vo.ProductVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 首页接口 —— M2 首页的 4 个接口（契约路径 {@code /api/home/*}）。
 *
 * <p><b>这 4 个接口全部对游客开放</b>（{@code SecurityConfig} 里放行了
 * {@code GET /api/home/**}），所以它们<b>不能有任何 userId 相关的逻辑</b>，
 * 也不能返回敏感信息。这样未登录用户打开首页就能看到内容，
 * 只有下单、加购物车这些动作才需要登录。
 *
 * <p>没有 {@code @Valid} 也没有 {@code @RequestBody} ——
 * 首页接口都是纯 GET 且无参数，前端进页面直接拉数据。
 */
@Tag(name = "02. M2 首页", description = "轮播图/推荐商品/热门文章/统计数据（4 个接口，游客可访问）")
@RestController
@RequestMapping("/api/home")
@RequiredArgsConstructor
public class HomeController {

    private final HomeService homeService;

    @Operation(summary = "轮播图",
            description = "数据来自字典表 type=banner 的项（remark 字段存图片 URL）。"
                    + "字典里没有配置时返回空数组，前端应自行兜底显示默认图。")
    @GetMapping("/banners")
    public Result<List<Map<String, Object>>> banners() {
        return Result.ok(homeService.banners());
    }

    @Operation(summary = "推荐商品",
            description = "在售商品里随机取 12 个。返回完整的 ProductVO，"
                    + "前端可以直接复用商品卡片组件。")
    @GetMapping("/recommended")
    public Result<List<ProductVO>> recommended() {
        return Result.ok(homeService.recommended());
    }

    @Operation(summary = "热门文章",
            description = "已发布文章中按浏览量倒序取 Top 5。"
                    + "只返回 status=1 的，待审核和已拒绝的文章不会露出。")
    @GetMapping("/articles")
    public Result<List<Map<String, Object>>> articles() {
        return Result.ok(homeService.articles());
    }

    @Operation(summary = "首页统计数字",
            description = "商品总数 / 融资成功数 / 用户数。⚠️ 需求规定这是 Mock 数据，"
                    + "返回固定数字，不是真实的数据库统计。")
    @GetMapping("/stats")
    public Result<Map<String, Object>> stats() {
        return Result.ok(homeService.stats());
    }
}
