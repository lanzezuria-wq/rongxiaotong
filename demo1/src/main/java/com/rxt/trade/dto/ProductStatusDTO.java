package com.rxt.trade.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 商品上下架入参 —— 对应契约里 PUT /product/{id}/status 的 body。
 *
 * <p>契约把这个字段限定为枚举 {@code [0, 1]}，所以这里用 {@code @Min(0) @Max(1)}
 * 而不是只校验非空。注意<b>不接收 2</b> —— 2 是"已删除"，
 * 删除走 {@code DELETE /product/{id}} 单独一个接口，不能通过改状态偷偷删商品。
 */
@Data
@Schema(description = "商品上下架入参")
public class ProductStatusDTO {

    @NotNull(message = "状态不能为空")
    @Min(value = 0, message = "状态只能是 0（下架）或 1（上架）")
    @Max(value = 1, message = "状态只能是 0（下架）或 1（上架）")
    @Schema(description = "0 下架 / 1 上架")
    private Integer status;
}
