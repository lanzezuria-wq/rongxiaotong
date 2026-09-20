package com.rxt.trade.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 订单明细 —— 对应 t_order_item。
 *
 * <p><b>这个类最重要的设计是"快照"：{@code productName} 和 {@code price}
 * 不是查询时 join 出来的，而是下单那一刻从 t_product 抄过来的。</b>
 *
 * <p>为什么非要冗余存一份？因为订单是<b>历史凭证</b>：
 * <ul>
 *   <li>卖家三个月后把"五常大米"改名成"东北大米"，历史订单应该还显示当时的名字；</li>
 *   <li>卖家调价后，历史订单的成交金额不能被改（否则对账全乱）；</li>
 *   <li>商品被删除后，订单明细仍然要能展示（外键连不过去）。</li>
 * </ul>
 * 这也是电商系统的通行做法：<b>交易发生时把当时的"事实"冻结下来</b>。
 *
 * <p>{@code subtotal} 也是存下来而不是每次算 {@code price * quantity}——
 * 这样列表查询不用做算术，而且以后要加"单品折扣"时不用改查询逻辑。
 *
 * <p><b>注意里面没有 create_time：</b>明细的生命周期完全依附于订单，
 * 看订单的创建时间就够了，单独存一列只会增加同步成本。
 */
@Data
@TableName("t_order_item")
public class OrderItem {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long orderId;

    private Long productId;

    /** 商品名快照（下单时的名字） */
    private String productName;

    /** 规格快照 */
    private String skuSpec;

    /** 成交单价快照 */
    private BigDecimal price;

    private Integer quantity;

    /** 小计 = price * quantity */
    private BigDecimal subtotal;
}
