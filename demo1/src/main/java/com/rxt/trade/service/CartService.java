package com.rxt.trade.service;

import com.rxt.trade.dto.CartAddDTO;
import com.rxt.trade.vo.CartVO;

import java.util.List;

/**
 * 购物车服务 —— M3 购物车 4 个接口。
 *
 * <p>购物车接口<b>不做分页</b>：一个用户的购物车规模天然很小（通常几条到十几条），
 * 分页只会让前端的"全选结算"逻辑变复杂。
 *
 * <p>所有方法都带 {@code userId} 参数并<b>在内部校验归属</b>：
 * 购物车项 id 是自增的，如果不校验，用户改一下 id 就能操作别人的购物车。
 */
public interface CartService {

    /** 购物车列表。只返回当前用户的，未登录时 Controller 层已经被 Security 挡住了 */
    List<CartVO> list(Long userId);

    /**
     * 加入购物车。
     *
     * <p>同一个商品 + 同一个规格<b>重复加购走数量累加</b>，不插新行
     * （表上有唯一索引 {@code uk_user_product_sku} 兜底）。
     *
     * <p>校验：商品必须存在且<b>在售</b>（下架商品不能加购），
     * 数量必须在 1~999 且不超过库存。
     *
     * @return 购物车项 id
     */
    Long add(Long userId, CartAddDTO dto);

    /**
     * 修改数量。
     *
     * <p>超过库存时<b>自动修正为库存上限</b>而不是报错——
     * 用户想买 500 件但只剩 10 件，直接告诉他"最多 10 件"比让操作失败友好。
     * 但库存为 0 时会抛 2001（库存不足），因为那意味着这个商品已经买不了。
     */
    void updateQuantity(Long userId, Long cartId, Integer quantity);

    /** 从购物车移除一项。校验归属 */
    void delete(Long userId, Long cartId);
}
