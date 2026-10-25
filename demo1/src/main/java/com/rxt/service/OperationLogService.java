package com.rxt.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.rxt.common.PageResult;
import com.rxt.entity.OperationLog;
import com.rxt.mapper.OperationLogMapper;
import com.rxt.security.ClientIpResolver;
import com.rxt.security.SecurityUtils;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Service
@RequiredArgsConstructor
public class OperationLogService {
    private final OperationLogMapper mapper;
    private final ClientIpResolver clientIpResolver;

    public void record(Long actor, String operation, Long targetId) {
        OperationLog log = entry(actor, operation, request());
        // 只记录允许的目标标识，不序列化请求体，防止密码、凭证或个人资料进入审计。
        log.setParams("targetId=" + targetId);
        log.setSuccess(1);
        mapper.insert(log);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordFailure(HttpServletRequest request, Long actor, int status) {
        OperationLog log = entry(actor, "操作未完成", request);
        log.setSuccess(0);
        log.setErrorMsg("HTTP " + status);
        mapper.insert(log);
    }

    public PageResult<OperationLog> page(int page, int size, String keyword, Integer success) {
        String term = StringUtils.hasText(keyword) ? keyword.trim() : null;
        var query = new LambdaQueryWrapper<OperationLog>()
                .eq(success != null, OperationLog::getSuccess, success)
                .and(term != null, w -> w.like(OperationLog::getOperation, term)
                        .or().like(OperationLog::getRequestId, term).or().like(OperationLog::getUri, term))
                .orderByDesc(OperationLog::getId);
        var result = mapper.selectPage(new Page<>(Math.max(1, page), Math.max(1, Math.min(200, size))), query);
        return PageResult.of(result, log -> log);
    }

    private OperationLog entry(Long actor, String operation, HttpServletRequest request) {
        OperationLog log = new OperationLog();
        log.setUserId(actor);
        log.setModule("系统管理");
        log.setOperation(operation);
        var user = SecurityUtils.currentUserOrNull();
        if (user != null && user.getPhone() != null) {
            log.setPhone(user.getPhone().replaceAll("(\\d{3})\\d{4}(\\d{4})", "$1****$2"));
        }
        if (request != null) {
            log.setMethod(request.getMethod());
            String uri = request.getRequestURI();
            log.setUri(uri.substring(0, Math.min(uri.length(), 255)));
            log.setIp(clientIpResolver.resolve(request));
            log.setRequestId((String) request.getAttribute("rxt.requestId"));
        }
        return log;
    }

    private HttpServletRequest request() {
        var attrs = RequestContextHolder.getRequestAttributes();
        return attrs instanceof ServletRequestAttributes servlet ? servlet.getRequest() : null;
    }
}
