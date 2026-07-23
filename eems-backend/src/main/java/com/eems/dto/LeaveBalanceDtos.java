package com.eems.dto;

import com.eems.entity.LeaveType;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public class LeaveBalanceDtos {

    public record BalanceResponse(
            Long employeeId,
            String employeeName,
            LeaveType leaveType,
            int year,
            double allocatedDays,
            double usedDays,
            double pendingDays,
            double availableDays,
            boolean tracked // false for UNPAID/OTHER - shown for completeness but never limits submission
    ) {}

    public record UpsertBalanceRequest(
            @NotNull Long employeeId,
            @NotNull LeaveType leaveType,
            @NotNull Integer year,
            @NotNull @Min(0) Double allocatedDays
    ) {}

    /** Sets the same allocation for every ACTIVE employee (optionally scoped to one department) in one call. */
    public record BulkAllocateRequest(
            @NotNull LeaveType leaveType,
            @NotNull Integer year,
            @NotNull @Min(0) Double allocatedDays,
            Long departmentId // null = all departments
    ) {}

    public record BulkAllocateResponse(
            int employeeCount,
            java.util.List<BalanceResponse> balances
    ) {}

    /**
     * For each employee with a fromYear balance for leaveType, adds their
     * unused remaining days (allocated - used, ignoring pending since the
     * prior year should be closed out by the time you run this) onto
     * their toYear allocation, capped at maxCarryOverDays if given.
     */
    public record CarryOverRequest(
            @NotNull LeaveType leaveType,
            @NotNull Integer fromYear,
            @NotNull Integer toYear,
            Double maxCarryOverDays // null = uncapped
    ) {}

    public record CarryOverResult(
            Long employeeId,
            String employeeName,
            double carriedOverDays,
            double newToYearAllocation
    ) {}

    public record CarryOverResponse(
            int employeeCount,
            java.util.List<CarryOverResult> results
    ) {}
}
