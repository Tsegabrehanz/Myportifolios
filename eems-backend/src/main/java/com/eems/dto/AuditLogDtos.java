package com.eems.dto;

import java.time.Instant;
import java.util.List;

public class AuditLogDtos {

    public record AuditLogResponse(
            Long id,
            String actorEmail,
            String entity,
            String entityId,
            String action,
            String detail,
            Instant timestamp
    ) {}

    public record PageResponse<T>(
            List<T> content,
            int page,
            int size,
            long totalElements,
            int totalPages
    ) {}
}
