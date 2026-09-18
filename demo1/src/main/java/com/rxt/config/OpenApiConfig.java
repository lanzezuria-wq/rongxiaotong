package com.rxt.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 接口文档配置（springdoc-openapi 3.x）。
 *
 * <p>启动后访问 <b>http://localhost:8080/swagger-ui.html</b> 就能看到全部接口，
 * 并可以直接在页面上调通（先调登录拿 Token，点右上角 Authorize 填进去，
 * 后面所有需要鉴权的接口都会自动带上 Header）。
 *
 * <p><b>⚠️ 版本陷阱：</b>设计文档写的是 springdoc {@code 2.3.x}，
 * 但 2.x 只支持 Spring Boot 3；Spring Boot 4 必须用 <b>3.x</b>（本项目用 3.1.1）。
 * 引错版本的表现是：应用能启动，但访问 /swagger-ui.html 一直 404 或者报
 * {@code NoProviderFoundException}，很容易误判成"路径写错了"。
 *
 * <p>这里配的 {@code SecurityScheme} 只是为了在 Swagger 页面上出现那个
 * "Authorize" 按钮，让联调时不用手工去拼 {@code Authorization: Bearer xxx} 头。
 * 它<b>不影响</b>真实的鉴权逻辑（那在 JwtAuthenticationFilter 里）。
 */
@Configuration
public class OpenApiConfig {

    private static final String BEARER_SCHEME = "bearerAuth";

    @Bean
    public OpenAPI rxtOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("融销通后端接口文档")
                        .version("1.0.0")
                        .description("P1 交付范围：后端地基 + 用户体系 M1（5 个接口）+ 系统管理 M7（8 个接口）。"
                                + "所有接口统一返回 { code, message, data } 结构，code=0 表示成功。"))
                .components(new Components().addSecuritySchemes(BEARER_SCHEME,
                        new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")
                                .description("登录接口返回的 token，填在这里即可，不要带 Bearer 前缀")))
                .addSecurityItem(new SecurityRequirement().addList(BEARER_SCHEME));
    }
}
