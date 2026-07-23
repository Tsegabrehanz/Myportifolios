package com.eems.service;

import com.eems.dto.AuditLogDtos.AuditLogResponse;
import com.eems.dto.AuditLogDtos.PageResponse;
import com.eems.entity.AuditLog;
import com.eems.repository.AuditLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuditLogService {

    private final AuditLogRepository auditLogRepository;

    public PageResponse<AuditLogResponse> list(int page, int size, String entityFilter, String actorFilter) {
        PageRequest pageRequest = PageRequest.of(page, size);
        boolean hasEntity = entityFilter != null && !entityFilter.isBlank();
        boolean hasActor = actorFilter != null && !actorFilter.isBlank();

        Page<AuditLog> result;
        if (hasEntity && hasActor) {
            result = auditLogRepository.findByEntityIgnoreCaseAndActorEmailIgnoreCaseOrderByTimestampDesc(entityFilter, actorFilter, pageRequest);
        } else if (hasEntity) {
            result = auditLogRepository.findByEntityIgnoreCaseOrderByTimestampDesc(entityFilter, pageRequest);
        } else if (hasActor) {
            result = auditLogRepository.findByActorEmailIgnoreCaseOrderByTimestampDesc(actorFilter, pageRequest);
        } else {
            result = auditLogRepository.findAllByOrderByTimestampDesc(pageRequest);
        }

        return new PageResponse<>(
                result.getContent().stream().map(this::toResponse).toList(),
                result.getNumber(),
                result.getSize(),
                result.getTotalElements(),
                result.getTotalPages()
        );
    }

    private AuditLogResponse toResponse(AuditLog log) {
        return new AuditLogResponse(log.getId(), log.getActorEmail(), log.getEntity(), log.getEntityId(), log.getAction(), log.getDetail(), log.getTimestamp());
    }
}
