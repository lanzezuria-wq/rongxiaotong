package com.rxt.common.exception;

import com.rxt.common.ResultCode;

/**
 * 操作过于频繁异常 —— 对应错误码 1005，HTTP 429。
 *
 * <p>两类场景会抛它：
 * <ol>
 *   <li><b>账号锁定</b>：连续输错密码 5 次后锁定 30 分钟（SRS 表12）；
 *       抛的时候会带自定义文案"账号已锁定，请 X 分钟后重试"。</li>
 *   <li><b>IP 限流</b>：同一 IP 在时间窗内请求登录/注册接口过多次数，由 {@code LoginRateLimiter} 抛出。</li>
 * </ol>
 *
 * <p><b>为什么归到 429 而不是 403？</b>429（Too Many Requests）是 HTTP 协议里表达
 * "限流"的标准状态码，前端和一些网关组件会识别它；403 表示的是"权限不够"，语义不同。
 * 概要设计 5.2.2 表给 1005 映射的正是 429，对得上。
 */
public class TooManyRequestsException extends BizException {

    public TooManyRequestsException(String message) {
        super(ResultCode.TOO_MANY_REQUESTS, message);
    }

    public TooManyRequestsException() {
        super(ResultCode.TOO_MANY_REQUESTS);
    }
}
