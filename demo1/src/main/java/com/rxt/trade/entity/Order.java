package com.rxt.trade.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 订单 —— 对应 t_order。P2 里业务最重的实体。
 *
 * <p>状态机见 {@link com.rxt.trade.constant.OrderConst}，这里说三个字段的设计意图：
 *
 * <p><b>1. {@code buyerId} 和 {@code farmerId} 都放在订单主表上。</b>
 * 而不是靠 {@code order_item -> product -> farmer_id} 反查。原因有两个：
 * 一是"我的订单（卖家视角）"要按 farmer_id 过滤，放主表能直接走索引；
 * 二是商品可能被删或换主，反查路径会断，而订单是历史凭证，必须自包含。
 *
 * <p><b>2. 四个业务时间戳（paidAt / shippedAt / receivedAt / cancelledAt）各自独立。</b>
 * 没合并成一个 {@code updateTime}，因为演示和答辩时要展示"下单到收货各阶段耗时"，
 * 合并后就查不出来了。它们为 NULL 表示"还没走到那一步"，本身就是状态机的旁证。
 *
 * <p><b>3. {@code expireAt} 是下单时算好的绝对时间</b>（而不是"创建 15 分钟后"这种相对描述）。
 * 存绝对时间的好处是：判断超时只需 {@code expireAt < now()}，一条 SQL 能批量扫出来，
 * 不用在应用层为每行做时间加法。
 *
 * <p><b>订单号为什么单独一列并加唯一索引，不用主键代替？</b>
 * 主键是自增数字，会暴露"平台总共有多少订单"，而且不便于人工核对。
 * {@code RXT + yyyyMMddHHmmss + 6 位随机} 自带时间信息和业务前缀，
 * 客户报单号时客服能一眼看出下单时间。唯一索引是防重复的兜底——
 * 同秒并发下单时随机数仍可能撞车，靠数据库挡住比应用层判断可靠。
 */
@Data
@TableName("t_order")
public class Order {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 订单号：RXT + yyyyMMddHHmmss + 6 位随机，全局唯一 */
    private String orderNo;

    /** 买家用户 id */
    private Long buyerId;

    /** 卖家（农户）用户 id —— 发货权限只属于这个人 */
    private Long farmerId;

    private BigDecimal totalAmount;

    /** 0 待付款 / 1 待发货 / 2 已发货 / 3 已完成 / 4 已取消 */
    private Integer status;

    /** 收货地址 id，下单时的引用 */
    private Long addressId;

    private String expressCompany;
    private String expressNo;

    private LocalDateTime paidAt;
    private LocalDateTime shippedAt;
    private LocalDateTime receivedAt;
    private LocalDateTime cancelledAt;

    /** 待付款超时时间，下单时置为 now + 15 分钟 */
    private LocalDateTime expireAt;

    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
