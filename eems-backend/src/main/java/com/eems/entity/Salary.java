package com.eems.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;

/**
 * Historical, effective-dated salary records - a new row per change
 * rather than overwriting, so past compensation stays visible. Access
 * is deliberately more restricted than general employee data (see
 * SalaryService/SecurityConfig): self and HR_ADMIN/SUPER_ADMIN only -
 * NOT a direct manager, unlike most other employee fields, since salary
 * is more sensitive than the rest of the HR record.
 *
 * taxNumber/bankName/iban are financial PII with the same "should be
 * encrypted at rest via a KMS-backed converter in production" caveat as
 * Employee.nationalId - not implemented here for the same reason (no
 * real KMS available in this environment).
 */
@Entity
@Table(name = "salary")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Salary {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "employee_id")
    private Employee employee;

    @Column(nullable = false)
    private double basicSalary;

    @Column(nullable = false)
    private String currency;

    private String taxNumber;
    private String bankName;
    private String iban;

    @Column(nullable = false)
    private LocalDate effectiveFrom;

    /** Null means "still in effect" - the current record for an employee is the one with the latest effectiveFrom and a null (or future) effectiveTo. */
    private LocalDate effectiveTo;
}
