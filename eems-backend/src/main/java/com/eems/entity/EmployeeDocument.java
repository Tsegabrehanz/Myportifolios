package com.eems.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.time.LocalDate;

/**
 * Two ways a document record gets created: a real upload (via
 * FileStorageService - storedFileName/contentType/fileSizeBytes are set,
 * the actual bytes live on disk) or metadata-only with just a fileUrl
 * pointing somewhere external. storedFileName is null for the latter -
 * that's how the controller decides whether GET .../download has
 * anything to actually stream back.
 */
@Entity
@Table(name = "employee_document")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EmployeeDocument {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "employee_id")
    private Employee employee;

    @Column(nullable = false)
    private String documentType; // e.g. Passport, Visa, Contract, Certificate, CV

    @Column(nullable = false)
    private String fileName;

    /** Metadata-only path: a plain string pointing wherever an external file actually lives. Null for real uploads. */
    private String fileUrl;

    /** Real-upload path: the on-disk filename FileStorageService generated (not the path - see FileStorageService). Null for metadata-only entries. */
    @Column(name = "stored_file_name")
    private String storedFileName;

    @Column(name = "content_type")
    private String contentType;

    @Column(name = "file_size_bytes")
    private Long fileSizeBytes;

    private LocalDate expiryDate;

    @Column(nullable = false, updatable = false)
    private Instant uploadedAt;

    @PrePersist
    void onCreate() {
        this.uploadedAt = Instant.now();
    }
}
