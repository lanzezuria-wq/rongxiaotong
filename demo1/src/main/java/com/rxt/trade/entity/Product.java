package com.rxt.trade.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 商品 —— 对应 t_product。
 *
 * <p><b>价格为什么用 {@link BigDecimal} 而不是 {@code double}？</b>
 * {@code double} 是二进制浮点数，存不下 0.1 这种十进制小数——
 * {@code 0.1 + 0.2} 会得到 {@code 0.30000000000000004}。
 * 金额一旦参与加减，误差会被放大到"差一分钱"，对账时很致命。
 * BigDecimal 是十进制精确表示，做金额的唯一正确选择。
 *
 * <p>{@code images} 和 {@code skuList} 在库里是 VARCHAR，存的是 JSON 字符串
 * （不是 MySQL 的 json 类型，也不是一对多表）。这样结构简单、不用连表；
 * 代价是数据库层面校验不了格式，只能靠 DTO 层的 {@code @Pattern} 把关。
 *
 * <p>{@code status} 见 {@link com.rxt.trade.constant.ProductConst}。
 */
@Data
@TableName("t_product")
public class Product {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 发布者（农户）用户 id，逻辑外键 → t_user.id */
    private Long farmerId;

    private String name;

    /** 分类编码，取值 t_dict 里 type=product_category 的 code */
    private String categoryCode;

    /** 单价（元） */
    private BigDecimal price;

    /** 总库存 */
    private Integer stock;

    /** 计量单位：斤 / 箱 / 盒 / 袋 */
    private String unit;

    private String description;

    /** 图片 URL 的 JSON 数组字符串，1~6 张 */
    private String images;

    /** 规格 JSON 字符串：[{spec, price, stock}] */
    private String skuList;

    /** 浏览量，查详情时 +1 */
    private Integer viewCount;

    /** 销量，订单完成时累加 */
    private Integer salesCount;

    /** 0 下架 / 1 在售 / 2 已删除 */
    private Integer status;

    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
