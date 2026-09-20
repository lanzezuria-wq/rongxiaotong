package com.rxt.trade.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

/**
 * 创建订单入参 —— 对应契约里 POST /order/create 的 body。
 *
 * <p><b>注意"从购物车下单"这个设计。</b>契约里传的是 {@code cartIds} 而不是
 * 商品 id 数组，也就是说下单动作的起点是购物车行。
 * 这样做的好处是购物车里的规格、数量直接复用，不用前端再拼一遍；
 * 代价是"立即购买"（不经过购物车）这种流程需要后端额外支持 ——
 * 本项目先不做，只保留购物车下单这一条主链路。
 *
 * <p>{@code remark} 契约里有但数据库 t_order 没有对应列，所以这里<b>不接收</b>。
 * Spring Boot 默认忽略请求体里的未知字段（{@code FAIL_ON_UNKNOWN_PROPERTIES}
 * 默认关闭），前端多传一个 remark 不会报错，只是被丢掉。
 */
@Data
@Schema(description = "创建订单入参")
public class OrderCreateDTO {

    @NotEmpty(message = "请选择要购买的商品")
    @Schema(description = "购物车项 id 列表，支持一次结算多件")
    private List<Long> cartIds;

    @NotNull(message = "请选择收货地址")
    private Long addressId;
}
