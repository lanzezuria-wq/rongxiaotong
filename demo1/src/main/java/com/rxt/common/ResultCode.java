package com.rxt.common;

import lombok.Getter;

/**
 * 全局错误码字典。
 *
 * <p><b>为什么它必须第一个写？</b>因为 Result 的静态工厂、BizException 的构造、
 * GlobalExceptionHandler 的十几个分支、前端拦截器的判断，全都在引用这里的枚举值。
 * 字典一旦定了就不要再改名字——改一个要回头改几十处。
 *
 * <p><b>数据来源</b>：《融销通_概要设计说明书》5.2.2 全局错误码规范（逐字对照）。
 *
 * <p><b>【已发现的文档矛盾，需组内定稿】</b>
 * <ul>
 *   <li>SRS 表61 说 1002=认证失败、1003=权限不足、1004=资源不存在；</li>
 *   <li>概要设计 5.2.2 与 SRS 表54 说 1002=手机号或密码错误、1003=账号已被禁用、1004=验证码错误。</li>
 * </ul>
 * 本类<b>以概要设计 5.2.2 为准</b>：它是超集、带 HTTP 状态码映射，且能和 7.1.1 全局异常处理表逐条对上。
 *
 * <p><b>另一处矛盾</b>：7.1.1 表里写 {@code ResourceNotFoundException → 1004（资源不存在）}，
 * 但 5.2.2 已经把 1004 分配给了"验证码错误"。本类取 5.2.2 为准，
 * "资源不存在"另用补充码 {@link #RESOURCE_NOT_FOUND}（1008，文档未定义，需与组内确认）。
 */
@Getter
public enum ResultCode {

    // ================= 0xxx 通用 / 1xxx 用户模块（P1 用到） =================
    SUCCESS(0, "成功", 200),
    SYSTEM_ERROR(1000, "系统内部错误", 500),
    PARAM_ERROR(1001, "请求参数校验失败", 400),
    LOGIN_FAILED(1002, "手机号或密码错误", 401),
    ACCOUNT_DISABLED(1003, "账号已被禁用", 403),
    VERIFY_CODE_ERROR(1004, "验证码错误", 400),
    TOO_MANY_REQUESTS(1005, "操作过于频繁", 429),
    TOKEN_EXPIRED(1006, "Token 过期", 401),
    FORBIDDEN(1007, "权限不足", 403),
    /** 补充码：文档 5.2.2 未定义"资源不存在"，7.1.1 误用了 1004，这里补 1008 待定稿 */
    RESOURCE_NOT_FOUND(1008, "资源不存在", 404),

    // ================= 2xxx 交易模块（P2 用，此处先登记占位） =================
    STOCK_NOT_ENOUGH(2001, "库存不足", 400),
    /** P2 补充：文档 5.2.2 未定义 2002，用于订单状态机的前置校验失败 —— 例如"未付款就发货" */
    ORDER_STATUS_ERROR(2002, "订单当前状态不允许该操作", 400),
    PRODUCT_OFF_SHELF(2003, "商品已下架", 400),
    CART_EMPTY(2004, "购物车为空", 400),

    // ================= 3xxx 融资模块（P3 用，此处先登记占位） =================
    FINANCING_EXISTS(3001, "已有待匹配融资申请", 400),
    GROUP_FULL(3002, "联保小组已满员", 400),
    FINANCING_HANDLED(3003, "融资申请已被银行处理，无法取消", 400);

    /** 业务错误码，返回给前端的 code 字段 */
    private final int code;

    /** 默认提示文案，可直接展示给用户 */
    private final String message;

    /** 对应的 HTTP 状态码，供 GlobalExceptionHandler 设置响应状态用 */
    private final int httpStatus;

    ResultCode(int code, String message, int httpStatus) {
        this.code = code;
        this.message = message;
        this.httpStatus = httpStatus;
    }
}
