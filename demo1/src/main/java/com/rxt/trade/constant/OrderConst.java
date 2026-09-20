package com.rxt.trade.constant;

/**
 * 订单状态字典 —— 对应 t_order.status。
 *
 * <p>状态机（SRS 表28，全项目最容易出错的地方）：
 * <pre>
 *   0 待付款 ──支付──→ 1 待发货 ──发货──→ 2 已发货 ──收货──→ 3 已完成
 *      │
 *      └──取消 / 15 分钟超时──→ 4 已取消
 * </pre>
 *
 * <p><b>三条铁律：</b>
 * <ol>
 *   <li>只能沿箭头走，不能跳级、不能回退。每次状态变更前必须校验当前状态
 *       （例如"待发货"才能发货，否则抛 {@code 2002}）；</li>
 *   <li>只有 {@link #STATUS_UNPAID} 能转 {@link #STATUS_CANCELLED} ——
 *       已付款的订单不能直接取消（本项目没做退款流程）；</li>
 *   <li>"发货"只能由订单上的 {@code farmerId} 本人操作，"收货/取消"只能由
 *       {@code buyerId} 本人操作。这一条<b>角色权限挡不住</b>（农户 A 和农户 B
 *       角色相同），必须在 Service 层比对 id。</li>
 * </ol>
 */
public final class OrderConst {

    private OrderConst() {
    }

    /** 待付款：下单后的初始状态，超过 expireAt 未支付自动取消 */
    public static final int STATUS_UNPAID = 0;

    /** 待发货：已支付，等卖家发货 */
    public static final int STATUS_UNSHIPPED = 1;

    /** 已发货：卖家填了快递单号 */
    public static final int STATUS_SHIPPED = 2;

    /** 已完成：买家确认收货，此时累计商品销量 */
    public static final int STATUS_COMPLETED = 3;

    /** 已取消：买家主动取消或超时未付 */
    public static final int STATUS_CANCELLED = 4;

    /** 待付款超时时间（分钟）—— SRS 规定 15 分钟 */
    public static final int EXPIRE_MINUTES = 15;

    /** 订单号前缀 */
    public static final String ORDER_NO_PREFIX = "RXT";

    /** 列表接口单页最大条数，防止前端传 size=999999 把库拖垮 */
    public static final int MAX_PAGE_SIZE = 100;

    /** 把状态码翻译成中文名，给 VO 用 */
    public static String nameOf(Integer status) {
        if (status == null) {
            return "";
        }
        return switch (status) {
            case STATUS_UNPAID -> "待付款";
            case STATUS_UNSHIPPED -> "待发货";
            case STATUS_SHIPPED -> "已发货";
            case STATUS_COMPLETED -> "已完成";
            case STATUS_CANCELLED -> "已取消";
            default -> "未知";
        };
    }
}
