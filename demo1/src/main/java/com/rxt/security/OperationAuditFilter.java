package com.rxt.security;

import com.rxt.service.OperationLogService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;

@Slf4j
@RequiredArgsConstructor
public class OperationAuditFilter extends OncePerRequestFilter {
    private final OperationLogService logs;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String path = request.getRequestURI();
        boolean mutation = java.util.Set.of("POST", "PUT", "DELETE").contains(request.getMethod())
                && (path.startsWith("/api/admin/") || path.matches("/api/article/\\d+/audit"));
        Long actor = SecurityUtils.currentUserIdOrNull();
        try {
            chain.doFilter(request, response);
        } finally {
            if (mutation && response.getStatus() >= 400) {
                try {
                    logs.recordFailure(request, actor, response.getStatus());
                } catch (RuntimeException e) {
                    log.error("审计失败请求写入失败，requestId={}", request.getAttribute("rxt.requestId"), e);
                }
            }
        }
    }
}
