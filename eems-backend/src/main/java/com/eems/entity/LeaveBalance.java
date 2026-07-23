package com.eems.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * One row per (employee, leaveType, year). Only leave types in
 * LeaveBalanceService.BALANCE_TRACKED_TYPES actually use this - UNPAID
 * and OTHER leave aren't capped, so no balance is tracked for them.
 *
 * usedDays is updated by LeaveBalanceService.applyApproval when a leave
 * request is approved - it is NOT recalculated by summing historical
 * requests on every read, so it stays correct even after old requests
 * are no longer easily queryable (e.g. after an employee offboards).
 */
@Entity
@Table(name = "leave_balance", uniqueConstraints = @UniqueConstraint(columnNames = {"employee_id", "leave_type", "balance_year"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LeaveBalance {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "employee_id")
    private Employee employee;

    @Enumerated(EnumType.STRING)
    @Column(name = "leave_type", nullable = false)
    private LeaveType leaveType;

    // Column explicitly named "balance_year", NOT "year" - "year" is a
    // reserved keyword in H2's SQL dialect (a date/time function), and
    // Hibernate's default naming would otherwise generate an unquoted
    // "year" column that H2's DDL parser rejects outright at startup
    // ("expected identifier"). PostgreSQL doesn't reserve the word, but
    // using a non-reserved name here keeps the dev (H2) and prod/docker
    // (PostgreSQL) schemas identical either way.
    @Column(name = "balance_year", nullable = false)
    private int year;

    @Column(nullable = false)
    @Builder.Default
    private double allocatedDays = 0;

    @Column(nullable = false)
    @Builder.Default
    private double usedDays = 0;
}
