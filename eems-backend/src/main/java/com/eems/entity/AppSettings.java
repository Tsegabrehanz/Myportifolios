package com.eems.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Single-row table for app-wide settings - currently just the company
 * logo. Always id=1; AppSettingsService fetches-or-creates that one row
 * rather than ever inserting a second. Kept as its own tiny entity
 * rather than folding into an existing one, since more app-wide
 * settings (beyond just the logo) are a likely next addition here.
 */
@Entity
@Table(name = "app_settings")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AppSettings {

    @Id
    private Long id;

    @Column(name = "logo_stored_file_name")
    private String logoStoredFileName;

    @Column(name = "logo_content_type")
    private String logoContentType;

    /**
     * Employee ID format: {prefix}{zero-padded sequence}{suffix}, e.g.
     * prefix="EMP-", sequence=7 -> "EMP-0007". Changing the prefix/suffix
     * only affects employees created AFTER the change - existing
     * employeeCodes are never retroactively regenerated or renamed.
     */
    @Column(name = "employee_id_prefix")
    @Builder.Default
    private String employeeIdPrefix = "EMP-";

    @Column(name = "employee_id_suffix")
    @Builder.Default
    private String employeeIdSuffix = "";

    /** Next sequence number to assign - incremented (not reused) every time an employee code is generated, even if an employee is later deleted. */
    @Column(name = "next_employee_id_sequence")
    @Builder.Default
    private int nextEmployeeIdSequence = 1;
}
