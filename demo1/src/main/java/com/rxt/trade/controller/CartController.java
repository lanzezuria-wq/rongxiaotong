package com.rxt.trade.controller;

import com.rxt.common.Result;
import com.rxt.security.SecurityUtils;
import com.rxt.trade.dto.CartAddDTO;
import com.rxt.trade.dto.CartUpdateDTO;
import com.rxt.trade.service.CartService;
import com.rxt.trade.vo.CartVO;
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
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 购物车接口 —— M3 的 4 个购物车接口（契约路径 {@code /api/cart/*}）。
 *
 * <p><b>这 4 个接口全部要求登录</b>（{@code SecurityConfig} 里没放行，
 * 走 {@code anyRequest().authenticated()}）。所以可以直接调
 * {@link SecurityUtils#currentUserId()}，不必担心匿名用户把 principal 变成字符串。
 *
 * <p><b>路径设计上有个小坑：</b>{@code POST /api/cart}（不带 id）用来"加入"，
 * {@code PUT/DELETE /api/cart/{id}} 用来改和删。加入不用带 id 是因为
 * id 还不存在；如果写成 {@code POST /api/cart/add} 虽然更直白，
 * 但和契约里的路径对不上，前端会 404。
 */
@Tag(name = "04. M3 购物车", description = "列表/加入/改数量/删除（4 个接口）")
@RestController
@RequestMapping("/api/cart")
@RequiredArgsConstructor
public class CartController {

    private final CartService cartService;

    @Operation(summary = "购物车列表",
            description = "返回当前用户的购物车。每项含商品名、主图、实时单价、库存和小计。"
                    + "商品被删除或下架时该项的商品信息为 null，前端应提示用户移除。")
    @GetMapping("/list")
    public Result<List<CartVO>> list() {
        return Result.ok(cartService.list(SecurityUtils.currentUserId()));
    }

    @Operation(summary = "加入购物车",
            description = "同一商品 + 同一规格重复加购会累加数量，不会新增一行。"
                    + "商品必须存在且在售，数量不能超过库存。")
    @PostMapping
    public Result<Long> add(@Valid @RequestBody CartAddDTO dto) {
        return Result.ok("已加入购物车", cartService.add(SecurityUtils.currentUserId(), dto));
    }

    @Operation(summary = "修改购物车数量",
            description = "数量超过库存时会自动降到库存上限，不会报错。"
                    + "商品已下架/售罄则返回对应错误码。")
    @PutMapping("/{id}")
    public Result<Void> updateQuantity(@PathVariable Long id,
                                       @Valid @RequestBody CartUpdateDTO dto) {
        cartService.updateQuantity(SecurityUtils.currentUserId(), id, dto.getQuantity());
        return Result.ok("已更新", null);
    }

    @Operation(summary = "删除购物车项",
            description = "只能删自己的。id 不属于当前用户时返回 1007。")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        cartService.delete(SecurityUtils.currentUserId(), id);
        return Result.ok("已从购物车移除", null);
    }
}
