package com.eems.service;

import com.eems.audit.AuditService;
import com.eems.dto.LeaveRequestDtos.CreateLeaveRequest;
import com.eems.dto.LeaveRequestDtos.LeaveDecisionRequest;
import com.eems.dto.LeaveRequestDtos.LeaveResponse;
import com.eems.entity.Employee;
import com.eems.entity.LeaveRequest;
import com.eems.entity.LeaveStatus;
import com.eems.exception.ForbiddenOperationException;
import com.eems.exception.ResourceNotFoundException;
import com.eems.repository.EmployeeRepository;
import com.eems.repository.LeaveRequestRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class LeaveService {

    private final LeaveRequestRepository leaveRequestRepository;
    private final EmployeeRepository employeeRepository;
    private final LeaveBalanceService leaveBalanceService;
    private final AuditService auditService;

    @Transactional
    public LeaveResponse submit(CreateLeaveRequest request, Authentication authentication) {
        Employee employee = currentEmployee(authentication);

        if (request.endDate().isBefore(request.startDate())) {
            throw new IllegalArgumentException("endDate cannot be before startDate");
        }

        // Throws if this would exceed the employee's available balance
        // for a tracked leave type (ANNUAL/SICK/PARENTAL) - no-op for
        // UNPAID/OTHER. Checked before persisting, so an over-request
        // never even reaches PENDING.
        leaveBalanceService.validateRequest(employee.getId(), request.type(), request.startDate(), request.endDate());

        LeaveRequest leave = LeaveRequest.builder()
                .employee(employee)
                .type(request.type())
                .startDate(request.startDate())
                .endDate(request.endDate())
                .reason(request.reason())
                .status(LeaveStatus.PENDING)
                .build();

        leave = leaveRequestRepository.save(leave);
        auditService.record("LeaveRequest", leave.getId().toString(), "CREATE",
                "Leave submitted: " + request.type() + " " + request.startDate() + " to " + request.endDate());
        return toResponse(leave);
    }

    /** FR-4.2: manager -> HR approval workflow. Only the employee's manager or HR/Admin may decide. */
    @Transactional
    public LeaveResponse decide(Long leaveRequestId, LeaveDecisionRequest decision, Authentication authentication) {
        if (decision.decision() != LeaveStatus.APPROVED && decision.decision() != LeaveStatus.REJECTED) {
            throw new IllegalArgumentException("decision must be APPROVED or REJECTED");
        }

        LeaveRequest leave = leaveRequestRepository.findById(leaveRequestId)
                .orElseThrow(() -> new ResourceNotFoundException("Leave request not found: " + leaveRequestId));

        String role = topRole(authentication);
        boolean isHrOrAdmin = role.equals("ROLE_SUPER_ADMIN") || role.equals("ROLE_HR_ADMIN");

        // Deliberately optional, not the throwing currentEmployee() helper -
        // a SUPER_ADMIN/HR_ADMIN approver (e.g. admin@eems.local in the
        // seed data) may have no linked Employee record at all, and that's
        // fine for them since isHrOrAdmin alone is enough to authorize the
        // decision below. Only a MANAGER genuinely needs one, to prove
        // they're this employee's direct manager.
        Optional<Employee> deciderEmployee = employeeRepository.findByUserEmail(authentication.getName());

        boolean isDirectManager = deciderEmployee.isPresent()
                && leave.getEmployee().getManager() != null
                && leave.getEmployee().getManager().getId().equals(deciderEmployee.get().getId());

        if (!isHrOrAdmin && !isDirectManager) {
            throw new ForbiddenOperationException("Only the employee's manager or HR can decide on this leave request");
        }
        if (leave.getStatus() != LeaveStatus.PENDING) {
            throw new IllegalArgumentException("This leave request has already been decided");
        }

        leave.setStatus(decision.decision());
        leave.setApprovedBy(deciderEmployee.orElse(null)); // stays null when the approver has no Employee record - that's fine, approvedBy is nullable
        leave.setDecidedAt(Instant.now());
        leave = leaveRequestRepository.save(leave);

        if (decision.decision() == LeaveStatus.APPROVED) {
            leaveBalanceService.applyApproval(leave);
        }

        String deciderLabel = deciderEmployee
                .map(e -> e.getFirstName() + " " + e.getLastName())
                .orElse(authentication.getName()); // fall back to the login email when there's no Employee profile to name
        auditService.record("LeaveRequest", leaveRequestId.toString(), "DECISION",
                "Leave " + decision.decision() + " by " + deciderLabel);
        return toResponse(leave);
    }

    public List<LeaveResponse> listForCurrentUser(Authentication authentication) {
        String role = topRole(authentication);

        if (role.equals("ROLE_SUPER_ADMIN") || role.equals("ROLE_HR_ADMIN") || role.equals("ROLE_AUDITOR")) {
            // findAllWithRelations(), not findAll() - toResponse() touches
            // l.getEmployee() and l.getApprovedBy(), both LAZY - without the
            // fetch join that's up to 2 extra queries per leave request.
            return leaveRequestRepository.findAllWithRelations().stream().map(this::toResponse).toList();
        }

        // Only MANAGER and EMPLOYEE roles are expected to have a linked
        // Employee profile - look it up here, not before the role check
        // above, since SUPER_ADMIN/HR_ADMIN/AUDITOR accounts may have none.
        Employee employee = currentEmployee(authentication);

        if (role.equals("ROLE_MANAGER")) {
            return leaveRequestRepository.findByEmployeeManagerIdAndStatus(employee.getId(), LeaveStatus.PENDING)
                    .stream().map(this::toResponse).toList();
        }
        return leaveRequestRepository.findByEmployeeId(employee.getId()).stream().map(this::toResponse).toList();
    }

    private Employee currentEmployee(Authentication authentication) {
        return employeeRepository.findByUserEmail(authentication.getName())
                .orElseThrow(() -> new ResourceNotFoundException("Employee profile not found for current user"));
    }

    private String topRole(Authentication authentication) {
        return authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .findFirst()
                .orElse("ROLE_EMPLOYEE");
    }

    private LeaveResponse toResponse(LeaveRequest l) {
        return new LeaveResponse(
                l.getId(),
                l.getEmployee().getId(),
                l.getEmployee().getFirstName() + " " + l.getEmployee().getLastName(),
                l.getType(),
                l.getStartDate(),
                l.getEndDate(),
                l.getReason(),
                l.getStatus(),
                l.getApprovedBy() != null ? l.getApprovedBy().getFirstName() + " " + l.getApprovedBy().getLastName() : null
        );
    }
}
