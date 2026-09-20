package com.rxt.trade.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 购物车项 —— 对应 t_cart。
 *
 * <p><b>⚠️ 这张表是 P2 补建的：任务包的 17 张表里没有 t_cart，但接口契约有 4 个购物车接口。</b>
 *
 * <p><b>这里只存"谁要买什么、买几个"四件事</b>（userId / productId / skuSpec / quantity）。
 * 商品名、图片、单价、库存都<b>不存快照</b>，查询时 join t_product 实时取——
 * 因为购物车是"还没下单的意向"，跟着商品走才对。存了快照的话，
 * 卖家一改价，购物车里还是旧价，下单时金额对不上。
 * （订单明细 {@link OrderItem} 则相反，必须存快照——那是成交记录。）
 *
 * <p>{@code skuSpec} 在库里是 {@code NOT NULL DEFAULT ''} 而不是 NULL：
 * 表上有唯一索引 {@code uk_user_product_sku(user_id, product_id, sku_spec)}，
 * 而 <b>MySQL 的唯一索引不约束 NULL</b>——用 NULL 的话，同一件无规格商品
 * 能被重复加入购物车多行。
 */
@Data
@TableName("t_cart")
public class Cart {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private Long productId;

    /** 规格描述，无规格商品为空串（不是 null） */
    private String skuSpec;

    /** 数量，1 ~ 999 */
    private Integer quantity;

    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
