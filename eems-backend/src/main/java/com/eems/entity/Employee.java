package com.eems.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;

/**
 * HR profile for an employee. Sensitive PII (nationalId) is kept in its
 * own column so it can be targeted independently for encryption-at-rest
 * and excluded from default DTO projections (see EmployeeDto).
 */
@Entity
@Table(name = "employee")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Employee {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", unique = true)
    private User user;

    @Column(nullable = false)
    private String firstName;

    @Column(nullable = false)
    private String lastName;

    /** Sensitive PII - should be encrypted at rest via a converter/KMS in production. */
    @Column(name = "national_id")
    private String nationalId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "position_id")
    private Position position;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "department_id")
    private Department department;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "manager_id")
    private Employee manager;

    private LocalDate hireDate;

    private LocalDate exitDate;

    @Enumerated(EnumType.STRING)
    @Builder.Default
    private EmployeeStatus status = EmployeeStatus.ACTIVE;

    // Real uploaded profile photo, stored on disk via FileStorageService -
    // same pattern as EmployeeDocument's real-upload path, not a stub.
    // Null until someone actually uploads one; the frontend falls back to
    // initials when this is absent.
    @Column(name = "photo_stored_file_name")
    private String photoStoredFileName;

    @Column(name = "photo_content_type")
    private String photoContentType;

    /**
     * Human-readable ID (e.g. "EMP-0007"), distinct from the internal
     * database `id` - generated once at creation time via
     * AppSettingsService.generateNextEmployeeCode() using whatever
     * prefix/suffix/sequence was configured at that moment. Nullable for
     * employees created before this feature existed; never
     * retroactively backfilled, since that could silently reassign an ID
     * someone may have already written down or referenced elsewhere.
     */
    @Column(name = "employee_code", unique = true)
    private String employeeCode;
}
