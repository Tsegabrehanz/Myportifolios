package com.eems.dto;

import java.time.Instant;
import java.time.LocalDate;

/**
 * Deliberately flat (no nested objects) - Power BI's Web/JSON connector
 * handles a flat array of flat records far better than nested structures,
 * and explicit foreign-key id columns let Power BI's model view infer
 * relationships between these three "tables" (Employees / Departments /
 * LeaveRequests) the same way it would with real database tables.
 */
public class PowerBiDtos {

    public record EmployeeRow(
            Long employeeId,
            String firstName,
            String lastName,
            Long positionId,
            String positionTitle,
            String status,
            LocalDate hireDate,
            LocalDate exitDate,
            Long departmentId,
            String departmentName,
            Long managerId,
            String managerName
    ) {}

    public record DepartmentRow(
            Long departmentId,
            String departmentName,
            String location,
            long activeHeadcount,
            long totalHeadcount
    ) {}

    public record LeaveRequestRow(
            Long leaveRequestId,
            String leaveType,
            String leaveStatus,
            LocalDate startDate,
            LocalDate endDate,
            long durationDays,
            Instant createdAt,
            Instant decidedAt,
            Long employeeId,
            String employeeName,
            Long departmentId,
            String departmentName,
            Long approvedById,
            String approvedByName
    ) {}
}
