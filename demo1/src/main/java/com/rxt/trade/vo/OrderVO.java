package com.rxt.trade.vo;

import com.rxt.common.util.DateUtils;
import com.rxt.trade.constant.OrderConst;
import com.rxt.trade.entity.Order;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 订单出参（列表项）—— 对应契约里的 OrderVO。
 *
 * <p><b>日期字段全部是 String 而不是 LocalDateTime</b>，理由是沿用 P1 的做法：
 * Spring Boot 4 的 Web 层换成了 Jackson 3（{@code tools.jackson} 包），
 * {@code @JsonFormat} 的包名和行为都变了。手动格式化成字符串，
 * 输出格式（{@code yyyy-MM-dd HH:mm:ss}）100% 可控，也不会因为 Jackson 升级而变样。
 *
 * <p><b>{@code statusName} 由后端翻译</b>（"待付款"/"待发货"…）而不是交给前端映射：
 * 订单状态是本项目最核心的业务概念，加状态时只改后端一处，不用等前端发版。
 *
 * <p><b>{@code itemCount} 不在本类的 {@code from()} 里填</b> —— 它要查 t_order_item 的条数，
 * 属于跨表统计，由 Service 批量查询后统一填（避免 N+1）。
 *
 * <p>{@code buyerName} / {@code farmerName} 同理，来自 t_user，Service 填。
 */
@Data
@Schema(description = "订单列表项")
public class OrderVO {

    private Long id;

    /** 订单号：RXT + yyyyMMddHHmmss + 6 位随机 */
    private String orderNo;

    private Long buyerId;

    /** 买家姓名，由 Service 填充 */
    private String buyerName;

    private Long farmerId;

    /** 卖家姓名，由 Service 填充 */
    private String farmerName;

    private BigDecimal totalAmount;

    /** 0 待付款 / 1 待发货 / 2 已发货 / 3 已完成 / 4 已取消 */
    private Integer status;

    /** 状态中文名 */
    private String statusName;

    /** 订单包含几件商品，由 Service 填充 */
    private Integer itemCount;

    /** 下单时间，格式 yyyy-MM-dd HH:mm:ss */
    private String createdAt;

    /** 待付款超时时间，只有待付款状态才有值 */
    private String expireAt;

    private String paidAt;

    private String shippedAt;

    private String receivedAt;

    private String cancelledAt;

    private String expressCompany;

    private String expressNo;

    public static OrderVO from(Order order) {
        if (order == null) {
            return null;
        }
        OrderVO vo = new OrderVO();
        vo.setId(order.getId());
        vo.setOrderNo(order.getOrderNo());
        vo.setBuyerId(order.getBuyerId());
        vo.setFarmerId(order.getFarmerId());
        vo.setTotalAmount(order.getTotalAmount());
        vo.setStatus(order.getStatus());
        vo.setStatusName(OrderConst.nameOf(order.getStatus()));
        vo.setCreatedAt(DateUtils.format(order.getCreateTime()));
        vo.setExpireAt(DateUtils.format(order.getExpireAt()));
        vo.setPaidAt(DateUtils.format(order.getPaidAt()));
        vo.setShippedAt(DateUtils.format(order.getShippedAt()));
        vo.setReceivedAt(DateUtils.format(order.getReceivedAt()));
        vo.setCancelledAt(DateUtils.format(order.getCancelledAt()));
        vo.setExpressCompany(order.getExpressCompany());
        vo.setExpressNo(order.getExpressNo());
        return vo;
    }
}
