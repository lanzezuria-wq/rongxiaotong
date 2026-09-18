package com.rxt.security;

import com.rxt.common.ResultCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * 认证失败入口 —— 未登录 / Token 无效 / Token 过期 时，返回统一格式的 401。
 *
 * <p><b>为什么必须有这个类？</b>因为异常发生在<b>过滤器链里</b>，
 * 还没进到 Controller，所以 {@code @RestControllerAdvice} 那个全局异常处理器
 * 根本接管不到。如果不写它，Spring Security 会返回它自己那套格式
 * （一个 {@code {"timestamp":..,"status":401,"error":"Unauthorized","path":".."}} 的空 body），
 * 前端按 {@code code} 字段解析会拿到 {@code undefined}，
 * 表现是"接口报 401，但前端不知道该怎么提示用户"。
 *
 * <p><b>和 GlobalExceptionHandler 的分工（重要）：</b>
 * <pre>
 *   过滤器链里失败（大部分情况） → 本类 / RestAccessDeniedHandler
 *   Controller/Service 里失败     → GlobalExceptionHandler
 * </pre>
 * 两处都要写，缺一个就会出现"有时是 Result 格式、有时不是"的诡异现象。
 *
 * <p><b>错误码怎么选</b>：
 * <ul>
 *   <li>请求头里带了 Token 但已过期 → <b>1006</b>（Token 过期），
 *       前端据此静默跳登录页；</li>
 *   <li>没带 Token、或 Token 签名/格式不对 → <b>1002</b>，
 *       前端提示"请先登录"；</li>
 *   <li>Token 有效但账号已被禁用 → <b>1003</b>（账号已被禁用）。</li>
 * </ul>
 * 三种情况都靠 {@link JwtAuthenticationFilter} 留在 request 属性里的标记来区分。
 */
@Slf4j
@Component
public class RestAuthEntryPoint implements AuthenticationEntryPoint {

    @Override
    public void commence(HttpServletRequest request,
                         HttpServletResponse response,
                         AuthenticationException authException) throws IOException {

        Object reason = request.getAttribute(JwtAuthenticationFilter.JWT_ERROR_ATTR);

        ResultCode resultCode;
        String message;
        if (JwtAuthenticationFilter.JWT_ERROR_EXPIRED.equals(reason)) {
            resultCode = ResultCode.TOKEN_EXPIRED;
            message = "登录已过期，请重新登录";
        } else if (JwtAuthenticationFilter.JWT_ERROR_DISABLED.equals(reason)) {
            resultCode = ResultCode.ACCOUNT_DISABLED;
            message = "账号已被禁用，请联系管理员";
        } else {
            resultCode = ResultCode.LOGIN_FAILED;
            message = "未登录或登录状态无效，请先登录";
        }

        log.warn("认证未通过 [{} {}]：{}", request.getMethod(), request.getRequestURI(), message);
        RestResponseWriter.write(response, resultCode.getHttpStatus(), resultCode.getCode(), message);
    }
}
