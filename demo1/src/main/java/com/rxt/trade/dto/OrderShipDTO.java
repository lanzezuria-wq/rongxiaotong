package com.rxt.trade.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 卖家发货入参 —— 对应契约里 PUT /order/{id}/ship 的 body。
 *
 * <p>发货要校验两件事，一件在 Controller 前面的权限层，一件在 Service 里：
 * <ol>
 *   <li><b>角色</b>：必须是 farmer —— 由 {@code SecurityConfig} 里的
 *       {@code hasRole("farmer")} 挡住；</li>
 *   <li><b>归属</b>：必须是<b>这一单的</b>卖家 —— 权限层挡不住，
 *       因为农户 A 和农户 B 角色相同。Service 里要比对
 *       {@code order.farmerId == 当前用户 id}，不等就抛 1007。</li>
 * </ol>
 * 只做第一件是典型的越权漏洞：任何农户都能发别人的货。
 */
@Data
@Schema(description = "订单发货入参")
public class OrderShipDTO {

    @NotBlank(message = "请填写快递公司")
    @Size(max = 50, message = "快递公司名称不能超过 50 字")
    @Schema(description = "快递公司，例如：顺丰速运")
    private String expressCompany;

    @NotBlank(message = "请填写快递单号")
    @Size(max = 50, message = "快递单号不能超过 50 字")
    private String expressNo;
}
