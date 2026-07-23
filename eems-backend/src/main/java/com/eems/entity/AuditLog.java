package com.eems.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * Append-only audit trail. Rows are never updated or deleted by
 * application code (see AuditService - only inserts are performed).
 */
@Entity
@Table(name = "audit_log")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Email of the acting user - kept denormalized so the log survives account deletion. */
    @Column(nullable = false)
    private String actorEmail;

    @Column(nullable = false)
    private String entity;

    @Column(nullable = false)
    private String entityId;

    @Column(nullable = false)
    private String action; // e.g. CREATE, UPDATE, DELETE, VIEW, EXPORT

    @Lob
    private String detail;

    @Column(nullable = false, updatable = false)
    private Instant timestamp;

    @PrePersist
    void onCreate() {
        this.timestamp = Instant.now();
    }
}
