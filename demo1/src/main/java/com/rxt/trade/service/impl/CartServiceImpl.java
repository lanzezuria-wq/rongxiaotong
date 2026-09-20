package com.rxt.trade.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.rxt.common.ResultCode;
import com.rxt.common.exception.BizException;
import com.rxt.trade.constant.ProductConst;
import com.rxt.trade.dto.CartAddDTO;
import com.rxt.trade.entity.Cart;
import com.rxt.trade.entity.Product;
import com.rxt.trade.mapper.CartMapper;
import com.rxt.trade.mapper.ProductMapper;
import com.rxt.trade.service.CartService;
import com.rxt.trade.vo.CartVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 购物车服务实现。
 *
 * <p><b>这里所有方法都显式比对 {@code userId}</b>，没有一个例外。
 * 购物车项 id 是自增数字，不校验的话用户把 id 从 5 改成 6 就能改别人的购物车
 * —— 这是最常见的越权漏洞形态（IDOR，Insecure Direct Object Reference）。
 */
@Service
@RequiredArgsConstructor
public class CartServiceImpl implements CartService {

    private final CartMapper cartMapper;
    private final ProductMapper productMapper;

    @Override
    public List<CartVO> list(Long userId) {
        List<Cart> carts = cartMapper.selectList(
                new LambdaQueryWrapper<Cart>()
                        .eq(Cart::getUserId, userId)
                        .orderByDesc(Cart::getCreateTime));
        if (carts.isEmpty()) {
            return Collections.emptyList();
        }

        // 批量查商品，而不是循环里一条条查。
        // 循环查商品是典型的 N+1 问题：购物车有 10 项就打 10 次库，
        // 而且是在一个 HTTP 请求里串行执行，接口耗时线性增长。
        List<Long> productIds = carts.stream()
                .map(Cart::getProductId)
                .distinct()
                .collect(Collectors.toList());
        Map<Long, Product> productMap = productMapper.selectByIds(productIds).stream()
                .collect(Collectors.toMap(Product::getId, Function.identity(), (a, b) -> a));

        return carts.stream()
                .map(cart -> CartVO.from(cart, productMap.get(cart.getProductId())))
                .collect(Collectors.toList());
    }

    @Override
    public Long add(Long userId, CartAddDTO dto) {
        Product product = productMapper.selectById(dto.getProductId());
        // 商品不存在 / 已删除 → 统一报"商品不存在"，不区分。
        // 区分了等于告诉调用方"这个 id 曾经存在过"。
        if (product == null || isDeleted(product)) {
            throw new BizException(ResultCode.RESOURCE_NOT_FOUND, "商品不存在");
        }
        if (!isOnSale(product)) {
            throw new BizException(ResultCode.PRODUCT_OFF_SHELF);
        }
        if (product.getStock() == null || product.getStock() <= 0) {
            throw new BizException(ResultCode.STOCK_NOT_ENOUGH, "该商品已售罄");
        }
        if (dto.getQuantity() > product.getStock()) {
            throw new BizException(ResultCode.STOCK_NOT_ENOUGH,
                    "库存不足，当前仅剩 " + product.getStock() + " 件");
        }

        // 空串而不是 null：表上唯一索引 uk_user_product_sku 不约束 NULL，
        // 用 null 的话同一件无规格商品能被重复插入多行。
        String skuSpec = dto.getSkuSpec() == null ? "" : dto.getSkuSpec();

        Cart existing = cartMapper.selectOne(
                new LambdaQueryWrapper<Cart>()
                        .eq(Cart::getUserId, userId)
                        .eq(Cart::getProductId, dto.getProductId())
                        .eq(Cart::getSkuSpec, skuSpec));

        if (existing != null) {
            // 已在购物车里 → 数量累加，同时被"999 上限"和"库存上限"两层夹住
            int newQuantity = existing.getQuantity() + dto.getQuantity();
            newQuantity = Math.min(newQuantity, ProductConst.MAX_CART_QUANTITY);
            newQuantity = Math.min(newQuantity, product.getStock());
            existing.setQuantity(newQuantity);
            cartMapper.updateById(existing);
            return existing.getId();
        }

        Cart cart = new Cart();
        cart.setUserId(userId);
        cart.setProductId(dto.getProductId());
        cart.setSkuSpec(skuSpec);
        cart.setQuantity(dto.getQuantity());
        cartMapper.insert(cart);
        return cart.getId();
    }

    @Override
    public void updateQuantity(Long userId, Long cartId, Integer quantity) {
        Cart cart = requireOwned(userId, cartId);

        Product product = productMapper.selectById(cart.getProductId());
        if (product == null || isDeleted(product)) {
            throw new BizException(ResultCode.RESOURCE_NOT_FOUND, "商品已不存在，请从购物车移除");
        }
        if (product.getStock() == null || product.getStock() <= 0) {
            throw new BizException(ResultCode.STOCK_NOT_ENOUGH, "该商品已售罄");
        }

        // 超过库存时自动降到库存上限，而不是报错。
        // 理由是"别人买走了导致库存下降"不是用户的错，直接拒绝会让他以为操作失败；
        // 悄悄改成最大可买数量并让前端刷新显示，体验更好。
        int finalQuantity = Math.min(quantity, product.getStock());

        cart.setQuantity(finalQuantity);
        cartMapper.updateById(cart);
    }

    @Override
    public void delete(Long userId, Long cartId) {
        // 先校验归属再删，不能直接 deleteById —— 那样会删掉别人的数据
        requireOwned(userId, cartId);
        cartMapper.deleteById(cartId);
    }

    // ==================== 私有辅助 ====================

    private Cart requireOwned(Long userId, Long cartId) {
        Cart cart = cartMapper.selectById(cartId);
        if (cart == null || !userId.equals(cart.getUserId())) {
            // 不区分"不存在"和"不是你的"，防止靠错误码枚举他人资源
            throw new BizException(ResultCode.FORBIDDEN, "购物车项不存在或无权操作");
        }
        return cart;
    }

    private boolean isDeleted(Product product) {
        return product.getStatus() != null && product.getStatus() == ProductConst.STATUS_DELETED;
    }

    private boolean isOnSale(Product product) {
        return product.getStatus() != null && product.getStatus() == ProductConst.STATUS_ON_SALE;
    }
}
