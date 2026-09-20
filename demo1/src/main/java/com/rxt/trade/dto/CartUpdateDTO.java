package com.rxt.trade.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 修改购物车数量入参 —— 对应契约里 PUT /cart/{id} 的 body。
 *
 * <p>只允许改数量，不给改商品或规格 —— 想换商品就删掉重加，
 * 这样能避免"同一个购物车项被改成指向另一个商品"这种脏数据。
 *
 * <p>上限 999 是任务书规定的。另外 Service 里还会再按商品库存做一次修正：
 * 传 500 但库存只有 10 时，自动降到 10 并提示，而不是直接报错 ——
 * 这样用户不会因为"别人买走了"而操作失败。
 */
@Data
@Schema(description = "修改购物车数量入参")
public class CartUpdateDTO {

    @NotNull(message = "数量不能为空")
    @Min(value = 1, message = "数量至少为 1")
    @Max(value = 999, message = "数量不能超过 999")
    private Integer quantity;
}
