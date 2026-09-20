package com.rxt.trade.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * 商品发布 / 编辑入参 —— 对应契约里的 ProductDTO（POST /product、PUT /product/{id} 共用）。
 *
 * <p>发布和编辑共用一个 DTO，因为字段完全一样。如果以后编辑允许"部分更新"
 * （只传要改的字段），就要拆成两个 DTO —— 现在编辑是整体覆盖语义，
 * 前端会把完整表单传回来。
 *
 * <p><b>校验注解的边界值都是从数据库列定义倒推的</b>，不是拍脑袋定的：
 * {@code @Size(max = 50)} 对应 {@code name varchar(50)}，
 * {@code @Max(6)} 对应任务书"最多 6 张图"。
 * <b>校验必须比数据库约束更严</b>，否则会变成"能通过校验但插入时报 DataTooLong"。
 *
 * <p>这些注解由 Controller 上的 {@code @Valid} 触发，失败会被全局异常处理器
 * 统一翻成错误码 {@code 1001}，不用在 Service 里写 if 判断。
 */
@Data
@Schema(description = "商品发布/编辑入参")
public class ProductDTO {

    @NotBlank(message = "商品名称不能为空")
    @Size(max = 50, message = "商品名称不能超过 50 字")
    private String name;

    /** 分类编码，须是 t_dict 里 type=product_category 的某个 code */
    @NotBlank(message = "请选择商品分类")
    private String categoryCode;

    @NotNull(message = "单价不能为空")
    @DecimalMin(value = "0.01", message = "单价必须大于 0")
    @Digits(integer = 8, fraction = 2, message = "单价最多 8 位整数、2 位小数")
    private BigDecimal price;

    @NotNull(message = "库存不能为空")
    @Min(value = 0, message = "库存不能为负数")
    private Integer stock;

    @NotBlank(message = "请填写计量单位")
    @Size(max = 10, message = "单位不能超过 10 字")
    private String unit;

    private String description;

    /** 图片 URL 列表，1~6 张 */
    @NotNull(message = "请至少上传 1 张商品图")
    @Size(min = 1, max = 6, message = "商品图需要 1~6 张")
    private List<String> images;

    /** SKU 规格列表，无规格商品可以不传 */
    private List<SkuItem> skuList;

    /**
     * 单个规格。内嵌为静态内部类而不是独立文件，因为它只在这里用。
     *
     * <p>注意这里<b>没有</b> {@code @NotNull} 或者 {@code @Valid} 级联校验 ——
     * 也就是说 skuList 里的元素字段目前不做校验。如果需要，要在字段上加
     * {@code @Valid} 才会级联进去，这是一条很容易踩的 MyBatis/BeanValidation 坑。
     */
    @Data
    @Schema(description = "商品规格")
    public static class SkuItem {

        @NotBlank(message = "规格名称不能为空")
        private String spec;

        @NotNull(message = "规格单价不能为空")
        @DecimalMin(value = "0.01", message = "规格单价必须大于 0")
        private BigDecimal price;

        @NotNull(message = "规格库存不能为空")
        @Min(value = 0, message = "规格库存不能为负数")
        @Max(value = 999999, message = "规格库存过大")
        private Integer stock;
    }
}
