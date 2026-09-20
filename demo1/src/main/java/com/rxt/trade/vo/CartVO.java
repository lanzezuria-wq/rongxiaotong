package com.rxt.trade.vo;

import com.rxt.trade.entity.Cart;
import com.rxt.trade.entity.Product;
import com.rxt.trade.util.TradeJsonUtil;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * 购物车项出参 —— 对应契约里的 CartVO。
 *
 * <p><b>它的 {@code from} 需要两个实体：Cart 和 Product。</b>
 * 这是本模块唯一一个"跨表拼装"的 VO，原因是前面在 {@link com.rxt.trade.entity.Cart}
 * 里解释过的设计取舍：购物车只存"谁买什么买几个"，商品名/图/价格/库存
 * <b>不存快照</b>，所以必须 join 出来。
 *
 * <p>{@code price} 有两条来源，优先级是：
 * <ol>
 *   <li>购物车项带了规格（{@code skuSpec} 非空）→ 从商品的 skuList 里找<b>该规格的价</b>；</li>
 *   <li>没带规格，或者商品里找不到匹配规格 → 退回商品的<b>基础售价</b>。</li>
 * </ol>
 * 找不到规格时静默退回基础价（而不是报错）是有意的：商品改过规格的话，
 * 旧购物车项不该直接失效，让用户看到价格变了、自己决定要不要重新选。
 */
@Data
@Schema(description = "购物车项")
public class CartVO {

    /** 购物车项 id，删除/改数量都用它 */
    private Long id;

    private Long productId;

    private String productName;

    /** 商品主图（商品 images 的第一张） */
    private String productImage;

    private String skuSpec;

    /** 单价（有规格取规格价） */
    private BigDecimal price;

    private Integer quantity;

    /** 小计 = price × quantity */
    private BigDecimal subtotal;

    /** 商品当前库存，前端据此限制数量输入框的最大值 */
    private Integer stock;

    public static CartVO from(Cart cart, Product product) {
        if (cart == null) {
            return null;
        }
        CartVO vo = new CartVO();
        vo.setId(cart.getId());
        vo.setProductId(cart.getProductId());
        // skuSpec 库里存的是空串（不是 null），回给前端时统一成 null 更符合直觉
        vo.setSkuSpec(cart.getSkuSpec() == null || cart.getSkuSpec().isBlank() ? null : cart.getSkuSpec());
        vo.setQuantity(cart.getQuantity());

        if (product != null) {
            vo.setProductName(product.getName());
            vo.setProductImage(TradeJsonUtil.firstOrNull(TradeJsonUtil.parseStringList(product.getImages())));
            BigDecimal price = resolvePrice(product, cart.getSkuSpec());
            vo.setPrice(price);
            vo.setStock(product.getStock());
            if (price != null && cart.getQuantity() != null) {
                vo.setSubtotal(price.multiply(BigDecimal.valueOf(cart.getQuantity())));
            }
        }
        return vo;
    }

    /**
     * 决定这一项按什么单价算。
     *
     * <p>规格价比基础价优先——用户选"5 斤装 25 元"就不能按"基础价 30 元"结算。
     */
    private static BigDecimal resolvePrice(Product product, String skuSpec) {
        if (skuSpec != null && !skuSpec.isBlank()) {
            List<ProductDetailVO.SkuVO> skus =
                    TradeJsonUtil.parseList(product.getSkuList(), ProductDetailVO.SkuVO.class);
            for (ProductDetailVO.SkuVO sku : skus) {
                if (skuSpec.equals(sku.getSpec()) && sku.getPrice() != null) {
                    return sku.getPrice();
                }
            }
        }
        return product.getPrice();
    }
}
