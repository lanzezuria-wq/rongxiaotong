package com.rxt.config;

import com.rxt.security.JwtAuthenticationFilter;
import com.rxt.security.RestAccessDeniedHandler;
import com.rxt.security.RestAuthEntryPoint;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

/**
 * 安全配置 —— 整个安全地基的"组装者"。
 *
 * <p><b>它必须最后写。</b>因为它要 import 上面全部零件：JWT 过滤器、两个失败处理器。
 * 零件没齐就组装，只会得到一堆编译错误。这也是"按依赖方向写代码"的典型例子。
 *
 * <p><b>⚠️ 这里是 Spring Security 7 的写法，网上照抄 Security 6 的教程必错：</b>
 * <table border="1">
 *   <tr><th>Security 6 及以前（网上教程都是这个）</th><th>Security 7（本项目）</th></tr>
 *   <tr><td>{@code http.csrf().disable().authorizeRequests()}</td><td>只有 Lambda DSL：{@code http.csrf(AbstractHttpConfigurer::disable)}</td></tr>
 *   <tr><td>{@code .antMatchers("/x")}</td><td>{@code .requestMatchers("/x")}</td></tr>
 *   <tr><td>{@code .and()} 串联各段</td><td><b>已移除</b>。只能一段一个 lambda，链式到底</td></tr>
 *   <tr><td>{@code @EnableGlobalMethodSecurity}</td><td>{@code @EnableMethodSecurity}</td></tr>
 *   <tr><td>{@code authorizeRequests()}</td><td>{@code authorizeHttpRequests()}</td></tr>
 * </table>
 * 而 Spring Boot 4.1.x 配套的正是 Security <b>7.1.x</b>（由父 pom 统一管理，不要手写版本号，
 * 更不要降到 6.x——那样会因 Bean 装配方式变更直接启动失败）。
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final RestAuthEntryPoint restAuthEntryPoint;
    private final RestAccessDeniedHandler restAccessDeniedHandler;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                // 前后端分离 + JWT，不用 Cookie 传会话标识，所以 CSRF 攻击不成立，关掉
                .csrf(AbstractHttpConfigurer::disable)
                // 前端跑在 5173 端口，属于跨域，要放行
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                // 这三个是给传统"服务端渲染 + 表单登录"用的，REST 接口不需要
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                // 无状态：不创建 HttpSession。身份完全靠每个请求自带的 JWT
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

                // ==================== 授权规则（按顺序匹配，前面的先命中） ====================
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/ping").permitAll()
                        // 注册和登录必须放行，否则没人能拿到 Token
                        .requestMatchers("/api/user/register", "/api/user/login").permitAll()
                        // 接口文档
                        .requestMatchers("/swagger-ui.html", "/swagger-ui/**", "/v3/api-docs/**").permitAll()
                        // /error 是 Spring Boot 的默认错误转发路径，不放行会把错误响应也变成 401
                        .requestMatchers("/error").permitAll()

                        // 字典查询：SRS 表59 里这一条只标了"认证 √"，没标"管理员"——
                        // 因为前端所有下拉框（产品分类/资金用途…）都要调它，普通用户也得能用。
                        // 所以它虽是 /api/admin/** 下的路径，但必须放在下面那条管理员规则之前单独放行。
                        .requestMatchers(HttpMethod.GET, "/api/admin/dict/**").authenticated()
                        // 其余系统管理接口一律管理员专属（用户管理 / 专家审核 / 字典增删改）
                        .requestMatchers("/api/admin/**").hasRole("admin")

                        // ==================== P2 交易模块（M2 首页 + M3 农产品交易） ====================
                        // 首页 + 商品浏览：SRS 表标注认证为"×"，游客不登录也能看
                        .requestMatchers(HttpMethod.GET, "/api/home/**", "/api/product/**").permitAll()
                        // 商品发布 / 编辑 / 上下架 / 删除：只允许农户角色。
                        // ⚠️ 这里只挡得住"角色对不对"，挡不住"是不是本人发布的商品"——
                        //    后者必须在 Service 层比对 farmer_id 和当前用户，否则农户 A 能改农户 B 的商品。
                        .requestMatchers(HttpMethod.POST, "/api/product").hasRole("farmer")
                        .requestMatchers(HttpMethod.PUT, "/api/product/**").hasRole("farmer")
                        .requestMatchers(HttpMethod.DELETE, "/api/product/**").hasRole("farmer")
                        // 购物车 / 订单 / 收货地址：登录即可访问，数据归属同样由 Service 层校验。
                        // 这三组不用显式声明，走下面的 anyRequest().authenticated()

                        // 文章审核藏在专家模块的路径下（表58），单独点出来限制管理员
                        .requestMatchers(HttpMethod.PUT, "/api/article/*/audit").hasRole("admin")
                        // 文章列表 / 详情：SRS 表58 标注认证为"×"，游客可读
                        .requestMatchers(HttpMethod.GET, "/api/article/**").permitAll()

                        // ============ P3 融资模块（M4 融资服务 + M6 银行端 + M5 专家助力） ============

                        // M6 银行端：审批是银行专属职能，别的角色一律进不来。
                        // ⚠️ 这条只按角色拦，判断不了"这个小组归不归你审批"——那属于业务规则，
                        //    在 BankServiceImpl.approve 里（要读小组数据才能判断）。
                        //    另外它和 BankController 上的 @PreAuthorize("hasRole('bank')")
                        //    以及 Service 里的 requireBankUser 是三层冗余，挡的不是同一件事
                        //    （详见 BankController 的类注释）。
                        .requestMatchers("/api/bank/**").hasRole("bank")

                        // M5 专家列表与详情：SRS 表58 里这两个接口认证为"×"，
                        // 游客在登录前应该能看到"平台上有哪些专家"，这是产品获客路径的一部分。
                        .requestMatchers(HttpMethod.GET, "/api/expert/**").permitAll()

                        // M4 融资服务（/api/financing/**）与 M5 的提问、预约
                        // （/api/question/**、/api/appointment/**）都由下面的
                        // anyRequest().authenticated() 兜住"登录即可访问"，这里不重复声明。
                        //
                        // 为什么它们的角色要求不写在这里？因为那是**"按动作"而不是"按路径"**的：
                        //   · 融资申请 / 发起小组   → 只有农户（FinancingServiceImpl 校验）
                        //   · 融资申请详情         → 本人 + admin/bank（FinancingServiceImpl 校验）
                        //   · 加入小组用的申请归属 → 必须是自己的申请（FinancingServiceImpl 校验）
                        //   · 专家回答 / 处理预约  → 必须是"记录归属的那个专家"
                        //     （ConsultServiceImpl 校验）—— 这个 URL 规则根本表达不了，
                        //     它要判断"你的 t_expert.id 是不是这条记录的 expertId"。
                        // 硬要写成多条互相穿插的 URL 规则，只会既难读又容易漏。

                        // ==================== P3 新增结束 ====================

                        // 除以上之外的所有接口，登录即可访问
                        .anyRequest().authenticated())

                // 认证/授权失败时，用我们自己的格式返回，而不是 Security 默认的裸 JSON
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(restAuthEntryPoint)
                        .accessDeniedHandler(restAccessDeniedHandler))

                // 把 JWT 过滤器插到"用户名密码认证过滤器"之前，
                // 保证在链尾的授权判断执行时，SecurityContext 里已经有身份了
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    /**
     * 跨域配置。
     *
     * <p>开发期前端在 {@code http://localhost:5173}（Vite 默认端口），后端在 8080，
     * 浏览器会拦截跨域请求的响应，所以必须声明允许的来源。
     *
     * <p>用 {@code setAllowedOriginPatterns} 而不是 {@code setAllowedOrigins}：
     * 后者写 {@code "*"} 时不能和 {@code allowCredentials(true)} 共用（浏览器规范禁止），
     * 而项目重启时 Vite 可能换端口，写死 {@code localhost:5173} 又太脆。
     * 用 "localhost 加任意端口"的通配模式最省事。
     *
     * <p>另外前端工程里配了 Vite 代理（{@code /api → http://localhost:8080}），
     * 走代理时根本不产生跨域；这里的配置是给"不使用代理、直接请求 8080"的场景兜底。
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOriginPatterns(List.of(
                "http://localhost:*",
                "http://127.0.0.1:*"));
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setAllowCredentials(true);
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}
