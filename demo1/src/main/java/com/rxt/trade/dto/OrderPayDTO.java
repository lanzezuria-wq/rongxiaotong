package com.rxt.trade.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * Mock 支付入参 —— 对应契约里 {@code POST /order/pay} 的 body。
 *
 * <p>契约把订单 id 放在请求体里而不是路径上（路径是 {@code /order/pay} 而不是
 * {@code /order/{id}/pay}），所以单独建这个 DTO。
 *
 * <p>演示环境没有真实支付渠道，"支付"就是把状态从待付款推到待发货。
 * 正式项目这里应该接支付网关，并由网关的<b>异步回调</b>来改状态
 * —— 不能信任前端调这个接口就代表付款成功，否则用户能白嫖商品。
 * 这一点在答辩如果被问到，要明确说清楚这是 Mock。
 */
@Data
@Schema(description = "Mock 支付入参")
public class OrderPayDTO {

    @NotNull(message = "订单 id 不能为空")
    @Schema(description = "要支付的订单 id")
    private Long orderId;
}
