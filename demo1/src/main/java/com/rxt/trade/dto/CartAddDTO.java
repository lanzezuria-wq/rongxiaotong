package com.rxt.trade.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 加入购物车入参 —— 对应契约里 POST /cart 的 body。
 *
 * <p>契约中 {@code productId} 和 {@code quantity} 是必填，{@code skuSpec} 可选
 * （无规格商品不传）。这里 {@code skuSpec} 保持可为 null，<b>由 Service 统一转成空串</b>
 * 再入库 —— 因为表上的唯一索引 {@code uk_user_product_sku} 不约束 NULL，
 * 见 {@link com.rxt.trade.entity.Cart} 的说明。
 */
@Data
@Schema(description = "加入购物车入参")
public class CartAddDTO {

    @NotNull(message = "商品 id 不能为空")
    private Long productId;

    /** 规格描述，无规格商品不传 */
    @Schema(description = "规格，无规格商品不传")
    private String skuSpec;

    @NotNull(message = "数量不能为空")
    @Min(value = 1, message = "数量至少为 1")
    @Max(value = 999, message = "数量不能超过 999")
    private Integer quantity;
}
