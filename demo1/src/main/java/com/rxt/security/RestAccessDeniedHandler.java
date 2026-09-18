package com.rxt.security;

import com.rxt.common.ResultCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * 授权失败处理器 —— 已登录但权限不够时，返回统一格式的 403 + <b>1007（权限不足）</b>。
 *
 * <p>触发场景：农户带着合法 Token 访问 {@code /api/admin/user/list}（M7 权限矩阵规定只有管理员能看）。
 *
 * <p>它和 {@link RestAuthEntryPoint} 的区别就是经典的 <b>401 vs 403</b>：
 * <ul>
 *   <li><b>401 Unauthorized</b> —— "我不知道你是谁"（没登录、Token 过期）。前端应该跳登录页；</li>
 *   <li><b>403 Forbidden</b> —— "我知道你是谁，但你不能干这个"。前端应该提示"无权限"，
 *       <b>绝对不要跳登录页</b>——因为跳过去再登一次，还是没权限，用户会陷入死循环。</li>
 * </ul>
 * 前端拦截器必须按 code 区分这两条路径：1002/1006 → 清 Token 跳登录；1007 → 弹提示留在原页。
 */
@Slf4j
@Component
public class RestAccessDeniedHandler implements AccessDeniedHandler {

    @Override
    public void handle(HttpServletRequest request,
                       HttpServletResponse response,
                       AccessDeniedException accessDeniedException) throws IOException {

        log.warn("权限不足 [{} {}]：需要更高角色", request.getMethod(), request.getRequestURI());
        RestResponseWriter.write(response,
                ResultCode.FORBIDDEN.getHttpStatus(),
                ResultCode.FORBIDDEN.getCode(),
                ResultCode.FORBIDDEN.getMessage());
    }
}
