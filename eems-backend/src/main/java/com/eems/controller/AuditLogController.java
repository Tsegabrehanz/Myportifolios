package com.eems.controller;

import com.eems.dto.AuditLogDtos.AuditLogResponse;
import com.eems.dto.AuditLogDtos.PageResponse;
import com.eems.service.AuditLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/audit-logs")
@RequiredArgsConstructor
public class AuditLogController {

    private final AuditLogService auditLogService;

    @GetMapping
    public PageResponse<AuditLogResponse> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size,
            @RequestParam(required = false) String entity,
            @RequestParam(required = false) String actor
    ) {
        int cappedSize = Math.min(size, 100); // avoid a client asking for an unbounded page
        return auditLogService.list(page, cappedSize, entity, actor);
    }
}
