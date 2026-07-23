package com.eems.repository;

import com.eems.entity.AuditLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {
    List<AuditLog> findByEntityAndEntityId(String entity, String entityId);

    Page<AuditLog> findAllByOrderByTimestampDesc(Pageable pageable);
    Page<AuditLog> findByEntityIgnoreCaseOrderByTimestampDesc(String entity, Pageable pageable);
    Page<AuditLog> findByActorEmailIgnoreCaseOrderByTimestampDesc(String actorEmail, Pageable pageable);
    Page<AuditLog> findByEntityIgnoreCaseAndActorEmailIgnoreCaseOrderByTimestampDesc(String entity, String actorEmail, Pageable pageable);
}
