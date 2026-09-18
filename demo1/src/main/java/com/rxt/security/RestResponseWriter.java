package com.rxt.security;

import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;

/**
 * 向响应体里写一个 {@code Result} 形状的 JSON。
 *
 * <p><b>为什么不注入 ObjectMapper 序列化？</b>这是一个真实的版本陷阱。
 * Spring Boot 4 的 Web 层用的是 <b>Jackson 3</b>（{@code tools.jackson.databind}），
 * 而类路径上同时存在 Jackson 2（{@code com.fasterxml.jackson.databind}，
 * 由 jjwt-jackson 传递引入）。
 * 如果按类型注入 {@code com.fasterxml.jackson.databind.ObjectMapper}，
 * 拿到的是 jjwt 带进来的那个<b>没经过 Spring 配置的裸 mapper</b>，
 * 序列化行为和 Controller 里用的不完全一致（比如日期格式）。
 * 想按类型拿到对的那个又有歧义，得写 {@code @Qualifier}。
 *
 * <p>而这里要写的内容是固定结构、全部由我们自己产生的文案，
 * 所以直接拼字符串最稳：零依赖、零版本耦合、输出形状 100% 可控。
 */
final class RestResponseWriter {

    private RestResponseWriter() {
    }

    static void write(HttpServletResponse response, int httpStatus, int code, String message) throws IOException {
        response.setStatus(httpStatus);
        response.setCharacterEncoding("UTF-8");
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write("{\"code\":" + code
                + ",\"message\":\"" + escape(message)
                + "\",\"data\":null}");
        response.getWriter().flush();
    }

    /** 最小可用的 JSON 字符串转义。消息里不会有控制字符，但引号和反斜杠必须处理 */
    private static String escape(String s) {
        if (s == null) {
            return "";
        }
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }
}
