package com.rxt.trade.vo;

import com.rxt.trade.constant.ProductConst;
import com.rxt.trade.entity.Product;
import com.rxt.trade.util.TradeJsonUtil;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * 商品出参（列表项）—— 对应契约里的 ProductVO。
 *
 * <p>字段比契约多了三个（{@code coverImage} / {@code status} / {@code statusName}）：
 * <ul>
 *   <li>{@code coverImage}：列表页只显示一张图，让前端不必自己从 images 里取第一张；</li>
 *   <li>{@code status} / {@code statusName}：农户管理自己商品时要看到"在售/已下架"，没有这个只能靠前端硬编码映射。</li>
 * </ul>
 *
 * <p>{@code farmerName} 是在 Service 里补的，不在 {@link #from} 里 ——
 * 因为它是另一个模块（t_user）的数据，静态转换方法不该去查库。
 */
@Data
@Schema(description = "商品列表项")
public class ProductVO {

    private Long id;

    private String name;

    /** 分类编码，前端可拿它去查字典拿中文名 */
    private String categoryCode;

    private BigDecimal price;

    private String unit;

    /** 图片 URL 列表 */
    private List<String> images;

    /** 封面图（images 的第一张），方便列表页直接用 */
    private String coverImage;

    private Integer salesCount;

    private Long farmerId;

    /** 发布者姓名，由 Service 填充 */
    private String farmerName;

    /** 0 下架 / 1 在售 / 2 已删除 */
    private Integer status;

    /** 状态中文名 */
    private String statusName;

    public static ProductVO from(Product product) {
        if (product == null) {
            return null;
        }
        ProductVO vo = new ProductVO();
        fillBase(vo, product);
        return vo;
    }

    /**
     * 把 Product 的基础字段填进任意一个 ProductVO 子类。
     *
     * <p>抽出来的原因是 {@link ProductDetailVO} 继承本类，它的 {@code from()} 需要
     * 复用同一套赋值逻辑。如果各写一遍，以后加字段时必然漏掉其中一处
     * —— 这是继承体系里最典型的坑。
     *
     * @param vo      目标对象，可以是 ProductVO 也可以是它的子类
     * @param product 源实体
     */
    protected static void fillBase(ProductVO vo, Product product) {
        vo.setId(product.getId());
        vo.setName(product.getName());
        vo.setCategoryCode(product.getCategoryCode());
        vo.setPrice(product.getPrice());
        vo.setUnit(product.getUnit());
        // 库里是 JSON 字符串，这里转成真正的数组返回给前端
        List<String> images = TradeJsonUtil.parseStringList(product.getImages());
        vo.setImages(images);
        vo.setCoverImage(TradeJsonUtil.firstOrNull(images));
        vo.setSalesCount(product.getSalesCount());
        vo.setFarmerId(product.getFarmerId());
        vo.setStatus(product.getStatus());
        vo.setStatusName(ProductConst.nameOf(product.getStatus()));
    }
}
