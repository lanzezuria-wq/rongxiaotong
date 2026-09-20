package com.rxt.trade.vo;

import com.rxt.trade.entity.Product;
import com.rxt.trade.util.TradeJsonUtil;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.util.List;

/**
 * 商品详情出参 —— 对应契约里的 ProductDetailVO（allOf 继承 ProductVO）。
 *
 * <p><b>为什么用继承而不是组合？</b>因为契约就是继承语义，而且序列化成 JSON 后
 * 前端拿到的是<b>一层平铺的字段</b>（详情页不用写 {@code detail.base.name} 这种路径）。
 * 代价是继承的经典问题：基类字段改了要留意子类。
 * 这里用 {@code @EqualsAndHashCode(callSuper = true)} 消除 Lombok 的告警。
 *
 * <p>详情比列表多的字段都是有道理的：
 * <ul>
 *   <li>{@code description} —— 太长了，列表页不返回，省带宽；</li>
 *   <li>{@code stock} —— 列表页用不到（不显示库存），详情页要显示"仅剩 N 件"；</li>
 *   <li>{@code viewCount} —— 详情页才显示浏览量；</li>
 *   <li>{@code skuList} —— 规格选择器只在详情页出现。</li>
 * </ul>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "商品详情")
public class ProductDetailVO extends ProductVO {

    private String description;

    /** 总库存，详情页显示"仅剩 N 件" */
    private Integer stock;

    private Integer viewCount;

    /** 规格列表，无规格商品为空数组 */
    private List<SkuVO> skuList;

    public static ProductDetailVO from(Product product) {
        if (product == null) {
            return null;
        }
        ProductDetailVO vo = new ProductDetailVO();
        // 复用父类的字段填充，避免继承体系里两处赋值逻辑走偏
        fillBase(vo, product);
        vo.setDescription(product.getDescription());
        vo.setStock(product.getStock());
        vo.setViewCount(product.getViewCount());
        vo.setSkuList(TradeJsonUtil.parseList(product.getSkuList(), SkuVO.class));
        return vo;
    }

    /**
     * 单个规格项。
     *
     * <p>字段名与 {@code ProductDTO.SkuItem} 一致（spec / price / stock），
     * 这样前端的表单数据结构不用做转换就能直接回传。
     */
    @Data
    @Schema(description = "商品规格项")
    public static class SkuVO {

        private String spec;

        private BigDecimal price;

        private Integer stock;
    }
}
