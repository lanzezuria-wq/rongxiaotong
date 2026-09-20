package com.rxt.trade.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 求购需求 —— 对应 t_product_demand。
 *
 * <p>这是 M3 里的附属功能（买家发"我想买什么"，等农户来对接），
 * 任务包标注为"可延后"。表和实体先建好，接口可以最后再补。
 *
 * <p>它和 t_product 是<b>反向</b>的：商品是"我有货要卖"，
 * 求购是"我要货想买"，所以价格是区间（minPrice ~ maxPrice）而不是单一单价。
 */
@Data
@TableName("t_product_demand")
public class ProductDemand {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 发布求购的用户 id */
    private Long userId;

    private String productName;

    /** 期望分类，取值 t_dict 里 type=product_category 的 code */
    private String categoryCode;

    /** 可接受的最低单价 */
    private BigDecimal minPrice;

    /** 可接受的最高单价 */
    private BigDecimal maxPrice;

    private Integer quantity;

    private String unit;

    private String description;

    /** 有效期，过期后不再展示 */
    private LocalDateTime expireAt;

    /** 0 已关闭 / 1 有效 */
    private Integer status;

    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
