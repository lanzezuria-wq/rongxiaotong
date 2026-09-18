package com.rxt.controller;

import com.rxt.common.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 健康检查接口 —— 整个地基的"验收标尺"。
 *
 * <p>它只有两个用途，但都很重要：
 * <ol>
 *   <li><b>验证容器能起来</b>：请求通了，说明 Web 层、统一响应、Security 的放行规则都对了；</li>
 *   <li><b>验证全局异常处理器在位</b>：把 {@code /api/ping} 的 body 改成
 *       {@code throw new RuntimeException("测试")}，它应该返回
 *       {@code {"code":1000,"message":"系统内部错误","data":null}}，
 *       而不是 Spring 默认那套 {@code Whitelabel Error Page} 或一堆堆栈。
 *       这一步过不了，说明 {@code GlobalExceptionHandler} 没生效。</li>
 * </ol>
 *
 * <p>注意它是 {@code permitAll} 的（见 SecurityConfig），不需要 Token 就能访问——
 * 健康检查本来就该能在没有任何凭证的情况下探测。
 */
@Tag(name = "00. 健康检查", description = "不需要登录，用于确认服务是否正常")
@RestController
@RequestMapping("/api")
public class HealthController {

    @Value("${spring.application.name:rxt-backend}")
    private String applicationName;

    @Operation(summary = "连通性探测", description = "返回服务名与状态。不需要认证。")
    @GetMapping("/ping")
    public Result<Map<String, Object>> ping() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("status", "UP");
        data.put("application", applicationName);
        return Result.ok("服务运行正常", data);
    }
}
