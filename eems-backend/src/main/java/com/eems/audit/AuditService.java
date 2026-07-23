package com.eems.audit;

import com.eems.entity.AuditLog;
import com.eems.repository.AuditLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuditService {

    private final AuditLogRepository auditLogRepository;

    public void record(String entity, String entityId, String action, String detail) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String actor = (authentication != null) ? authentication.getName() : "SYSTEM";

        AuditLog log = AuditLog.builder()
                .actorEmail(actor)
                .entity(entity)
                .entityId(entityId)
                .action(action)
                .detail(detail)
                .build();

        auditLogRepository.save(log);
    }
}
