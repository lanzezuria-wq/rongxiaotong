package com.rxt.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * JWT 鉴权过滤器 —— 每个请求只跑一次，负责"把 Token 变成 SecurityContext 里的登录态"。
 *
 * <p><b>它在过滤器链里的位置</b>：插在 {@code UsernamePasswordAuthenticationFilter} 之前。
 * 顺序很关键——必须在授权判断（{@code AuthorizationFilter}，链的末端）之前完成认证，
 * 否则 {@code hasRole("admin")} 判断时 SecurityContext 还是空的。
 *
 * <p><b>⚠️ 最关键的一个设计决定：解析失败时"不报错、也不放行"，而是打个标记继续往下走。</b>
 * 这是很容易写错的地方。直觉写法是"Token 无效就在这里直接返回 401"，
 * 但那会误伤放行接口：用户带着一个过期的 Token 去调 {@code /api/user/login} 重新登录，
 * 过滤器抢先返回了 401，用户就永远登不回来了。
 *
 * <p>正确做法是把失败原因记在 request 属性里，然后 {@code chain.doFilter} 继续。
 * 如果这个接口需要认证，链尾的授权判断会因为"没有身份"而抛异常 →
 * 交给 {@link RestAuthEntryPoint}，由它读出这个标记，
 * 决定返回 <b>1006（Token 过期）</b> 还是 <b>1002（认证失败）</b>。
 * 这样放行接口不受影响，受保护接口又能给出精确的错误码。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    /** request 属性名：Token 解析失败的原因，供 RestAuthEntryPoint 读取 */
    public static final String JWT_ERROR_ATTR = "rxt.jwt.error";
    public static final String JWT_ERROR_EXPIRED = "expired";
    public static final String JWT_ERROR_INVALID = "invalid";
    public static final String JWT_ERROR_DISABLED = "disabled";

    private final JwtUtil jwtUtil;
    private final UserDetailsServiceImpl userDetailsService;

    @Value("${rxt.jwt.header:Authorization}")
    private String headerName;

    @Value("${rxt.jwt.prefix:Bearer }")
    private String tokenPrefix;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        String token = resolveToken(request);

        // 已有登录态就不重复解析（例如同一次请求内被转发）
        if (token != null && SecurityContextHolder.getContext().getAuthentication() == null) {
            try {
                Claims claims = jwtUtil.parse(token);
                Long userId = Long.valueOf(claims.getSubject());
                LoginUser loginUser = userDetailsService.loadById(userId);

                if (loginUser == null) {
                    // Token 合法，但账号已被物理删除
                    request.setAttribute(JWT_ERROR_ATTR, JWT_ERROR_INVALID);
                } else if (!loginUser.isAccountNonLocked()) {
                    // Token 合法，但账号在 Token 签发后被禁用了 —— 立即失效
                    request.setAttribute(JWT_ERROR_ATTR, JWT_ERROR_DISABLED);
                } else {
                    UsernamePasswordAuthenticationToken authentication =
                            new UsernamePasswordAuthenticationToken(
                                    loginUser, null, loginUser.getAuthorities());
                    authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                    SecurityContextHolder.getContext().setAuthentication(authentication);
                }
            } catch (ExpiredJwtException e) {
                request.setAttribute(JWT_ERROR_ATTR, JWT_ERROR_EXPIRED);
                log.debug("Token 已过期：{}", request.getRequestURI());
            } catch (JwtException | IllegalArgumentException e) {
                request.setAttribute(JWT_ERROR_ATTR, JWT_ERROR_INVALID);
                log.debug("Token 无效：{} —— {}", request.getRequestURI(), e.getMessage());
            }
        }

        filterChain.doFilter(request, response);
    }

    /**
     * 从请求头里取出 Token，并剥掉 {@code "Bearer "} 前缀。
     *
     * <p>大小写不敏感：HTTP 头本身是大小写不敏感的，而且 Postman / 某些前端库
     * 会写成小写的 {@code bearer}。用 {@code regionMatches(true, ...)} 一次搞定。
     */
    private String resolveToken(HttpServletRequest request) {
        String header = request.getHeader(headerName);
        if (header == null || header.isBlank()) {
            return null;
        }
        if (header.regionMatches(true, 0, tokenPrefix, 0, tokenPrefix.length())) {
            String token = header.substring(tokenPrefix.length()).trim();
            return token.isEmpty() ? null : token;
        }
        // 没带前缀也认，方便用 curl 直接测
        return header.trim();
    }
}
