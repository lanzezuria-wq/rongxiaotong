package com.rxt.trade.controller;

import com.rxt.common.PageResult;
import com.rxt.common.Result;
import com.rxt.security.SecurityUtils;
import com.rxt.trade.constant.ProductConst;
import com.rxt.trade.dto.ProductDTO;
import com.rxt.trade.dto.ProductStatusDTO;
import com.rxt.trade.service.ProductService;
import com.rxt.trade.vo.ProductDetailVO;
import com.rxt.trade.vo.ProductVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 商品接口 —— M3 的 6 个商品接口（契约路径 {@code /api/product/*}）。
 *
 * <p><b>这个类应该很薄。</b>它只做三件事，和 P1 的 UserController 一个套路：
 * <ol>
 *   <li>用 {@code @Valid} 触发 DTO 上的参数校验；</li>
 *   <li>用 {@link SecurityUtils#currentUserId()} 取出当前用户，显式传给 Service；</li>
 *   <li>把返回值包成 {@link Result}，成功文案写在这里。</li>
 * </ol>
 * 如果发现自己在 Controller 里写了业务 {@code if}，那说明代码放错层了。
 *
 * <p><b>权限在这里「只做粗筛」</b>：{@code SecurityConfig} 已经把
 * POST/PUT/DELETE 限成了 {@code hasRole("farmer")}，但"是不是这件商品的农户"
 * 必须由 Service 校验——角色挡不住同角色之间的越权。
 *
 * <p>⚠️ 注意 {@code GET /list} 和 {@code GET /{id}} 的路径顺序：
 * 两者都在 {@code /api/product} 下，Spring 会优先匹配更具体的字面量路径
 * （{@code /list}），所以不会出现"list 被当成 id"的问题。
 */
@Tag(name = "03. M3 商品", description = "商品列表/详情/发布/编辑/上下架/删除（6 个接口）")
@RestController
@RequestMapping("/api/product")
@RequiredArgsConstructor
public class ProductController {

    private final ProductService productService;

    @Operation(summary = "商品列表",
            description = "分页查询。支持关键词模糊匹配商品名、按分类筛选、四种排序"
                    + "（default 综合 / price_asc / price_desc / sales / newest）。"
                    + "游客可访问，只返回在售商品。")
    @GetMapping("/list")
    public Result<PageResult<ProductVO>> list(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String categoryCode,
            @RequestParam(required = false) String sort,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size) {
        return Result.ok(productService.list(keyword, categoryCode, sort, page, size));
    }

    @Operation(summary = "商品详情",
            description = "返回详情含描述、库存、规格列表。浏览量 +1。"
                    + "游客可访问。已下架商品仍可看详情（用户可能从收藏进来）。")
    @GetMapping("/{id}")
    public Result<ProductDetailVO> detail(@PathVariable Long id) {
        return Result.ok(productService.detail(id));
    }

    @Operation(summary = "发布商品",
            description = "仅农户角色可调。分类编码必须在字典 product_category 里存在。"
                    + "图片 1~6 张。发布后默认上架。")
    @PostMapping
    public Result<Long> create(@Valid @RequestBody ProductDTO dto) {
        return Result.ok("发布成功", productService.create(SecurityUtils.currentUserId(), dto));
    }

    @Operation(summary = "编辑商品",
            description = "整体覆盖语义，前端传完整表单。只能改自己发布的商品，"
                    + "改别人的会返回 1007。")
    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id, @Valid @RequestBody ProductDTO dto) {
        productService.update(SecurityUtils.currentUserId(), id, dto);
        return Result.ok("保存成功", null);
    }

    @Operation(summary = "商品上下架",
            description = "status 只能是 0（下架）或 1（上架）。删除请调 DELETE 接口，"
                    + "不能通过改状态删商品。")
    @PutMapping("/{id}/status")
    public Result<Void> updateStatus(@PathVariable Long id,
                                     @Valid @RequestBody ProductStatusDTO dto) {
        productService.updateStatus(SecurityUtils.currentUserId(), id, dto.getStatus());
        String message = dto.getStatus() == ProductConst.STATUS_ON_SALE ? "商品已上架" : "商品已下架";
        return Result.ok(message, null);
    }

    @Operation(summary = "删除商品",
            description = "软删除（status 置 2），不是物理删除。重复删除会静默成功（幂等）。")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        productService.delete(SecurityUtils.currentUserId(), id);
        return Result.ok("删除成功", null);
    }
}
