package com.rxt.trade.vo;

import com.rxt.trade.entity.OrderItem;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 订单明细项出参。
 *
 * <p>全部字段都直接来自 {@link OrderItem} 实体，没有 join——
 * 因为明细表里存的就是<b>下单那一刻的快照</b>（商品名、规格、成交价），
 * 不需要也不应该再去查 t_product。商品后来改名、调价、被删，都不影响这里。
 */
@Data
@Schema(description = "订单明细项")
public class OrderItemVO {

    private Long id;

    private Long productId;

    /** 商品名（下单时的快照） */
    private String productName;

    private String skuSpec;

    /** 成交单价（下单时的快照） */
    private BigDecimal price;

    private Integer quantity;

    private BigDecimal subtotal;

    public static OrderItemVO from(OrderItem item) {
        if (item == null) {
            return null;
        }
        OrderItemVO vo = new OrderItemVO();
        vo.setId(item.getId());
        vo.setProductId(item.getProductId());
        vo.setProductName(item.getProductName());
        vo.setSkuSpec(item.getSkuSpec());
        vo.setPrice(item.getPrice());
        vo.setQuantity(item.getQuantity());
        vo.setSubtotal(item.getSubtotal());
        return vo;
    }
}
