package com.rxt.trade.constant;

/**
 * 商品状态字典 —— 对应 t_product.status。
 *
 * <p>抽成常量类而不是散在代码里写 {@code 0 / 1 / 2}，原因是
 * "0 代表什么"这件事要在 Entity、Service、Controller、前端四处对齐，
 * 写数字迟早会有人把 2 当成"已下架"（实际是"已删除"）。
 *
 * <p><b>为什么删除用 status=2 而不是单独的 deleted 列？</b>
 * 因为任务包的表设计就是三态一个字段。代价是：软删除的商品和"下架"的商品
 * 混在同一列里，所有列表查询都必须显式带上 {@code status != 2} 条件，
 * 漏一处就会把已删除的商品查出来。
 */
public final class ProductConst {

    private ProductConst() {
    }

    /** 已下架：农户主动下架，列表不展示，但数据还在 */
    public static final int STATUS_OFF_SHELF = 0;

    /** 在售：首页推荐和商品列表只查这个状态 */
    public static final int STATUS_ON_SALE = 1;

    /** 已删除：软删除标记，任何列表都不应出现 */
    public static final int STATUS_DELETED = 2;

    /** 图片张数下限（SRS：至少 1 张） */
    public static final int MIN_IMAGES = 1;

    /** 图片张数上限（SRS：最多 6 张） */
    public static final int MAX_IMAGES = 6;

    /** 商品名长度上限，与 t_product.name 的 varchar(50) 对齐 */
    public static final int MAX_NAME_LENGTH = 50;

    /** 单个商品在购物车里的数量上限 */
    public static final int MAX_CART_QUANTITY = 999;

    /**
     * 把状态码翻译成中文名，给 VO 用。
     *
     * <p>前端也可以自己映射，但放在后端的好处是：以后新增状态时只改一处，
     * 不用等前端发版。
     */
    public static String nameOf(Integer status) {
        if (status == null) {
            return "";
        }
        return switch (status) {
            case STATUS_OFF_SHELF -> "已下架";
            case STATUS_ON_SALE -> "在售";
            case STATUS_DELETED -> "已删除";
            default -> "未知";
        };
    }
}
