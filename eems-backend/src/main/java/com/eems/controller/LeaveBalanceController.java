package com.eems.controller;

import com.eems.dto.LeaveBalanceDtos.BalanceResponse;
import com.eems.dto.LeaveBalanceDtos.BulkAllocateRequest;
import com.eems.dto.LeaveBalanceDtos.BulkAllocateResponse;
import com.eems.dto.LeaveBalanceDtos.CarryOverRequest;
import com.eems.dto.LeaveBalanceDtos.CarryOverResponse;
import com.eems.dto.LeaveBalanceDtos.UpsertBalanceRequest;
import com.eems.service.LeaveBalanceService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/leave-balances")
@RequiredArgsConstructor
public class LeaveBalanceController {

    private final LeaveBalanceService leaveBalanceService;

    @GetMapping("/me")
    public List<BalanceResponse> myBalances(@RequestParam(required = false) Integer year, Authentication authentication) {
        return leaveBalanceService.listForCurrentUser(resolveYear(year), authentication);
    }

    /** Manager-scoped visibility enforced in the service - same rule as EmployeeService. */
    @GetMapping("/employee/{employeeId}")
    public List<BalanceResponse> forEmployee(@PathVariable Long employeeId, @RequestParam(required = false) Integer year, Authentication authentication) {
        return leaveBalanceService.listForEmployeeChecked(employeeId, resolveYear(year), authentication);
    }

    /** HR/Admin overview of everyone with a balance row for the given year - see SecurityConfig for role restriction. */
    @GetMapping
    public List<BalanceResponse> listAll(@RequestParam(required = false) Integer year) {
        return leaveBalanceService.listAll(resolveYear(year));
    }

    /** Set/update an employee's allocation for a tracked leave type + year. HR/Admin only. */
    @PostMapping
    public BalanceResponse upsert(@Valid @RequestBody UpsertBalanceRequest request) {
        return leaveBalanceService.upsertAllocation(request.employeeId(), request.leaveType(), request.year(), request.allocatedDays());
    }

    /** Set the same allocation for every active employee (optionally scoped to one department). HR/Admin only. */
    @PostMapping("/bulk-allocate")
    public BulkAllocateResponse bulkAllocate(@Valid @RequestBody BulkAllocateRequest request) {
        return leaveBalanceService.bulkAllocate(request.leaveType(), request.year(), request.allocatedDays(), request.departmentId());
    }

    /** Carry over unused fromYear balance onto toYear for every employee who has one. HR/Admin only. */
    @PostMapping("/carry-over")
    public CarryOverResponse carryOver(@Valid @RequestBody CarryOverRequest request) {
        return leaveBalanceService.carryOver(request.leaveType(), request.fromYear(), request.toYear(), request.maxCarryOverDays());
    }

    private int resolveYear(Integer year) {
        return year != null ? year : LocalDate.now().getYear();
    }
}
