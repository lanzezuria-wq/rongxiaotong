package com.rxt.controller;

import com.rxt.common.PageResult;
import com.rxt.common.Result;
import com.rxt.entity.OperationLog;
import com.rxt.service.OperationLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/logs")
@PreAuthorize("hasRole('admin')")
@RequiredArgsConstructor
public class AdminLogController {
    private final OperationLogService logs;

    @GetMapping
    public Result<PageResult<OperationLog>> page(@RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size, @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Integer success) {
        return Result.ok(logs.page(page, size, keyword, success));
    }
}
