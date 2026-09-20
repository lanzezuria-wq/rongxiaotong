package com.rxt.trade.vo;

import com.rxt.trade.entity.Order;
import com.rxt.trade.entity.OrderItem;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 订单详情出参 —— 对应契约里的 OrderDetailVO（继承 OrderVO）。
 *
 * <p>列表不返回 {@code items}（一次查 10 个订单就要连 10 次明细表），
 * 只有详情接口才带上，这是典型的"列表轻、详情重"切分。
 *
 * <p>{@code address} 用的是<b>下单时拼好的字符串</b>而不是地址 id：
 * 用户之后修改或删除收货地址，历史订单上应该仍然显示当时的收货信息，
 * 和订单明细存商品名快照是同一个道理。
 * （本项目为简化没在 t_order 里单独存地址快照列，而是在查询时读 t_address；
 * 更严谨的做法是下单时把地址文本冗余存进 t_order。）
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "订单详情")
public class OrderDetailVO extends OrderVO {

    /** 商品明细，至少一条 */
    private List<OrderItemVO> items;

    /** 收货信息（收货人 + 电话 + 完整地址），由 Service 拼装 */
    private String address;

    public static OrderDetailVO from(Order order, List<OrderItem> itemList) {
        if (order == null) {
            return null;
        }
        OrderDetailVO vo = new OrderDetailVO();
        // 复用父类字段填充
        OrderVO base = OrderVO.from(order);
        copyBase(base, vo);
        vo.setItems(itemList == null ? Collections.emptyList()
                : itemList.stream().map(OrderItemVO::from).collect(Collectors.toList()));
        return vo;
    }

    /** 把父类已经填好的字段搬到子类实例上（继承体系里复用赋值逻辑的标准做法） */
    private static void copyBase(OrderVO b, OrderDetailVO t) {
        t.setId(b.getId());
        t.setOrderNo(b.getOrderNo());
        t.setBuyerId(b.getBuyerId());
        t.setBuyerName(b.getBuyerName());
        t.setFarmerId(b.getFarmerId());
        t.setFarmerName(b.getFarmerName());
        t.setTotalAmount(b.getTotalAmount());
        t.setStatus(b.getStatus());
        t.setStatusName(b.getStatusName());
        t.setItemCount(b.getItemCount());
        t.setCreatedAt(b.getCreatedAt());
        t.setExpireAt(b.getExpireAt());
        t.setPaidAt(b.getPaidAt());
        t.setShippedAt(b.getShippedAt());
        t.setReceivedAt(b.getReceivedAt());
        t.setCancelledAt(b.getCancelledAt());
        t.setExpressCompany(b.getExpressCompany());
        t.setExpressNo(b.getExpressNo());
    }
}
