package com.eems.dto;

import com.eems.entity.LeaveStatus;
import com.eems.entity.LeaveType;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

public class LeaveRequestDtos {

    public record CreateLeaveRequest(
            @NotNull LeaveType type,
            @NotNull LocalDate startDate,
            @NotNull LocalDate endDate,
            String reason
    ) {}

    public record LeaveDecisionRequest(
            @NotNull LeaveStatus decision // APPROVED or REJECTED
    ) {}

    public record LeaveResponse(
            Long id,
            Long employeeId,
            String employeeName,
            LeaveType type,
            LocalDate startDate,
            LocalDate endDate,
            String reason,
            LeaveStatus status,
            String approvedByName
    ) {}
}
