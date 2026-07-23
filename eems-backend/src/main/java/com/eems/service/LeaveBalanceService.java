package com.eems.service;

import com.eems.audit.AuditService;
import com.eems.dto.LeaveBalanceDtos.BalanceResponse;
import com.eems.dto.LeaveBalanceDtos.BulkAllocateResponse;
import com.eems.dto.LeaveBalanceDtos.CarryOverResponse;
import com.eems.dto.LeaveBalanceDtos.CarryOverResult;
import com.eems.entity.Employee;
import com.eems.entity.EmployeeStatus;
import com.eems.entity.LeaveBalance;
import com.eems.entity.LeaveRequest;
import com.eems.entity.LeaveStatus;
import com.eems.entity.LeaveType;
import com.eems.exception.ForbiddenOperationException;
import com.eems.exception.ResourceNotFoundException;
import com.eems.repository.EmployeeRepository;
import com.eems.repository.LeaveBalanceRepository;
import com.eems.repository.LeaveRequestRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * The leave balance "calculator": computes allocated / used / pending /
 * available days per employee, leave type, and calendar year, and is
 * the single source of truth both for validating new requests and for
 * deducting balance on approval.
 *
 * Only ANNUAL, SICK, and PARENTAL are balance-tracked (see
 * BALANCE_TRACKED_TYPES) - matching how most real HR systems treat
 * UNPAID (no cap by definition) and OTHER (a catch-all) as not subject
 * to an accrual limit. Untracked types are never blocked by this
 * service, regardless of what's in the balance table for them.
 */
@Service
@RequiredArgsConstructor
public class LeaveBalanceService {

    public static final Set<LeaveType> BALANCE_TRACKED_TYPES = EnumSet.of(LeaveType.ANNUAL, LeaveType.SICK, LeaveType.PARENTAL);

    private final LeaveBalanceRepository leaveBalanceRepository;
    private final LeaveRequestRepository leaveRequestRepository;
    private final EmployeeRepository employeeRepository;
    private final AuditService auditService;

    /** All five leave types for the given employee/year, tracked or not, for display. */
    public List<BalanceResponse> listForEmployee(Long employeeId, int year) {
        Employee employee = employeeRepository.findById(employeeId)
                .orElseThrow(() -> new ResourceNotFoundException("Employee not found: " + employeeId));

        return List.of(LeaveType.values()).stream()
                .map(type -> toResponse(employee, type, year))
                .toList();
    }

    /**
     * Same as listForEmployee, but enforces the same visibility rule as
     * EmployeeService: SUPER_ADMIN/HR_ADMIN/AUDITOR see anyone, a
     * MANAGER sees their direct reports and themselves, an EMPLOYEE
     * sees only their own balances.
     */
    public List<BalanceResponse> listForEmployeeChecked(Long employeeId, int year, Authentication authentication) {
        String role = topRole(authentication);
        if (role.equals("ROLE_SUPER_ADMIN") || role.equals("ROLE_HR_ADMIN") || role.equals("ROLE_AUDITOR")) {
            return listForEmployee(employeeId, year);
        }

        Employee requester = employeeRepository.findByUserEmail(authentication.getName())
                .orElseThrow(() -> new ResourceNotFoundException("Employee profile not found for current user"));
        Employee target = employeeRepository.findById(employeeId)
                .orElseThrow(() -> new ResourceNotFoundException("Employee not found: " + employeeId));

        boolean isSelf = requester.getId().equals(target.getId());
        boolean isDirectManager = target.getManager() != null && target.getManager().getId().equals(requester.getId());

        if (role.equals("ROLE_MANAGER") && (isSelf || isDirectManager)) {
            return listForEmployee(employeeId, year);
        }
        if (role.equals("ROLE_EMPLOYEE") && isSelf) {
            return listForEmployee(employeeId, year);
        }
        throw new ForbiddenOperationException("You do not have permission to view this employee's leave balances");
    }

    /** The current authenticated user's own balances - always allowed, no role check needed. */
    /**
     * The current authenticated user's own balances. Returns an empty
     * list (not a 404) when there's no linked Employee profile - this
     * is expected and normal for SUPER_ADMIN/HR_ADMIN/AUDITOR accounts,
     * which often have no Employee record at all. "My leave balances"
     * is simply not applicable for those, not an error.
     */
    public List<BalanceResponse> listForCurrentUser(int year, Authentication authentication) {
        return employeeRepository.findByUserEmail(authentication.getName())
                .map(employee -> listForEmployee(employee.getId(), year))
                .orElseGet(List::of);
    }

    private String topRole(Authentication authentication) {
        return authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .findFirst()
                .orElse("ROLE_EMPLOYEE");
    }

    public List<BalanceResponse> listAll(int year) {
        return leaveBalanceRepository.findByYear(year).stream()
                .map(b -> toResponse(b.getEmployee(), b.getLeaveType(), year))
                .toList();
    }

    @Transactional
    public BalanceResponse upsertAllocation(Long employeeId, LeaveType leaveType, int year, double allocatedDays) {
        if (!BALANCE_TRACKED_TYPES.contains(leaveType)) {
            throw new IllegalArgumentException(leaveType + " is not balance-tracked (only ANNUAL, SICK, PARENTAL are) - setting an allocation for it would have no effect on submissions.");
        }
        Employee employee = employeeRepository.findById(employeeId)
                .orElseThrow(() -> new ResourceNotFoundException("Employee not found: " + employeeId));

        LeaveBalance balance = leaveBalanceRepository.findByEmployeeIdAndLeaveTypeAndYear(employeeId, leaveType, year)
                .orElseGet(() -> LeaveBalance.builder().employee(employee).leaveType(leaveType).year(year).build());
        balance.setAllocatedDays(allocatedDays);
        balance = leaveBalanceRepository.save(balance);

        auditService.record("LeaveBalance", balance.getId().toString(), "ALLOCATION_SET",
                "Set " + leaveType + " " + year + " allocation for employee " + employeeId + " to " + allocatedDays + " days");

        return toResponse(employee, leaveType, year);
    }

    /**
     * Sets the same allocation for every ACTIVE employee (optionally
     * scoped to one department) in a single call - the natural
     * complement to upsertAllocation for annual bulk setup (e.g. "give
     * everyone 25 days of annual leave for 2027") instead of one
     * employee at a time.
     */
    @Transactional
    public BulkAllocateResponse bulkAllocate(LeaveType leaveType, int year, double allocatedDays, Long departmentId) {
        if (!BALANCE_TRACKED_TYPES.contains(leaveType)) {
            throw new IllegalArgumentException(leaveType + " is not balance-tracked (only ANNUAL, SICK, PARENTAL are).");
        }

        List<Employee> employees = (departmentId != null)
                ? employeeRepository.findByDepartmentId(departmentId)
                : employeeRepository.findAll();
        employees = employees.stream().filter(e -> e.getStatus() == EmployeeStatus.ACTIVE).toList();

        List<BalanceResponse> results = new ArrayList<>();
        for (Employee employee : employees) {
            LeaveBalance balance = leaveBalanceRepository.findByEmployeeIdAndLeaveTypeAndYear(employee.getId(), leaveType, year)
                    .orElseGet(() -> LeaveBalance.builder().employee(employee).leaveType(leaveType).year(year).build());
            balance.setAllocatedDays(allocatedDays);
            leaveBalanceRepository.save(balance);
            results.add(toResponse(employee, leaveType, year));
        }

        auditService.record("LeaveBalance", "bulk", "BULK_ALLOCATION_SET",
                "Set " + leaveType + " " + year + " allocation to " + allocatedDays + " days for "
                        + employees.size() + " employee(s)" + (departmentId != null ? " in department " + departmentId : ""));

        return new BulkAllocateResponse(employees.size(), results);
    }

    /**
     * Carries over each employee's unused fromYear balance (allocated -
     * used, ignoring pending - the prior year should be closed out by
     * the time you run this) onto their toYear allocation, optionally
     * capped at maxCarryOverDays. Adds to whatever toYear allocation
     * already exists rather than overwriting it, so this is safe to run
     * after upsertAllocation/bulkAllocate has already set a base
     * allocation for the new year.
     */
    @Transactional
    public CarryOverResponse carryOver(LeaveType leaveType, int fromYear, int toYear, Double maxCarryOverDays) {
        if (!BALANCE_TRACKED_TYPES.contains(leaveType)) {
            throw new IllegalArgumentException(leaveType + " is not balance-tracked (only ANNUAL, SICK, PARENTAL are).");
        }
        if (toYear <= fromYear) {
            throw new IllegalArgumentException("toYear must be after fromYear");
        }

        List<LeaveBalance> fromBalances = leaveBalanceRepository.findByYear(fromYear).stream()
                .filter(b -> b.getLeaveType() == leaveType)
                .toList();

        List<CarryOverResult> results = new ArrayList<>();
        for (LeaveBalance fromBalance : fromBalances) {
            double remaining = fromBalance.getAllocatedDays() - fromBalance.getUsedDays();
            if (remaining <= 0) {
                continue; // nothing to carry over
            }
            double carried = (maxCarryOverDays != null) ? Math.min(remaining, maxCarryOverDays) : remaining;

            Employee employee = fromBalance.getEmployee();
            LeaveBalance toBalance = leaveBalanceRepository.findByEmployeeIdAndLeaveTypeAndYear(employee.getId(), leaveType, toYear)
                    .orElseGet(() -> LeaveBalance.builder().employee(employee).leaveType(leaveType).year(toYear).build());
            double newAllocation = toBalance.getAllocatedDays() + carried;
            toBalance.setAllocatedDays(newAllocation);
            leaveBalanceRepository.save(toBalance);

            results.add(new CarryOverResult(employee.getId(), employee.getFirstName() + " " + employee.getLastName(), carried, newAllocation));
        }

        auditService.record("LeaveBalance", "carry-over", "CARRY_OVER_APPLIED",
                "Carried over " + leaveType + " balance from " + fromYear + " to " + toYear + " for " + results.size() + " employee(s)");

        return new CarryOverResponse(results.size(), results);
    }

    /**
     * Throws IllegalArgumentException if this request would exceed the
     * employee's available balance for a tracked leave type. No-op for
     * untracked types (UNPAID, OTHER). Called from LeaveService.submit
     * before the request is persisted.
     */
    public void validateRequest(Long employeeId, LeaveType leaveType, LocalDate startDate, LocalDate endDate) {
        if (!BALANCE_TRACKED_TYPES.contains(leaveType)) {
            return;
        }
        int year = startDate.getYear();
        double duration = durationDays(startDate, endDate);
        double available = computeAvailable(employeeId, leaveType, year, null);

        if (duration > available) {
            throw new IllegalArgumentException(String.format(
                    "Insufficient %s leave balance: requested %.1f day(s), %.1f available in %d.",
                    leaveType, duration, available, year));
        }
    }

    /**
     * Called from LeaveService.decide when a request is approved.
     * Increments usedDays for the tracked type; no-op for untracked
     * types. Creates the balance row (0 allocated) if HR hasn't set one
     * yet, so the "used" figure is still tracked and visible even
     * before an allocation exists - it will simply show as over budget
     * until HR allocates enough days to cover it.
     */
    @Transactional
    public void applyApproval(LeaveRequest request) {
        LeaveType type = request.getType();
        if (!BALANCE_TRACKED_TYPES.contains(type)) {
            return;
        }
        int year = request.getStartDate().getYear();
        double duration = durationDays(request.getStartDate(), request.getEndDate());
        Employee employee = request.getEmployee();

        LeaveBalance balance = leaveBalanceRepository.findByEmployeeIdAndLeaveTypeAndYear(employee.getId(), type, year)
                .orElseGet(() -> LeaveBalance.builder().employee(employee).leaveType(type).year(year).build());
        balance.setUsedDays(balance.getUsedDays() + duration);
        leaveBalanceRepository.save(balance);
    }

    private double computeAvailable(Long employeeId, LeaveType leaveType, int year, Long excludeRequestId) {
        double allocated = leaveBalanceRepository.findByEmployeeIdAndLeaveTypeAndYear(employeeId, leaveType, year)
                .map(LeaveBalance::getAllocatedDays)
                .orElse(0.0);
        double used = leaveBalanceRepository.findByEmployeeIdAndLeaveTypeAndYear(employeeId, leaveType, year)
                .map(LeaveBalance::getUsedDays)
                .orElse(0.0);
        double pending = pendingDays(employeeId, leaveType, year, excludeRequestId);
        return allocated - used - pending;
    }

    /** Sum of duration across this employee's currently PENDING requests of this type in this year - "holds" balance without a separate reserved column. */
    private double pendingDays(Long employeeId, LeaveType leaveType, int year, Long excludeRequestId) {
        return leaveRequestRepository.findByEmployeeId(employeeId).stream()
                .filter(r -> r.getStatus() == LeaveStatus.PENDING)
                .filter(r -> r.getType() == leaveType)
                .filter(r -> r.getStartDate().getYear() == year)
                .filter(r -> excludeRequestId == null || !r.getId().equals(excludeRequestId))
                .mapToDouble(r -> durationDays(r.getStartDate(), r.getEndDate()))
                .sum();
    }

    private double durationDays(LocalDate start, LocalDate end) {
        return ChronoUnit.DAYS.between(start, end) + 1;
    }

    private BalanceResponse toResponse(Employee employee, LeaveType type, int year) {
        boolean tracked = BALANCE_TRACKED_TYPES.contains(type);
        if (!tracked) {
            return new BalanceResponse(employee.getId(), employee.getFirstName() + " " + employee.getLastName(), type, year, 0, 0, 0, 0, false);
        }

        LeaveBalance balance = leaveBalanceRepository.findByEmployeeIdAndLeaveTypeAndYear(employee.getId(), type, year).orElse(null);
        double allocated = balance != null ? balance.getAllocatedDays() : 0;
        double used = balance != null ? balance.getUsedDays() : 0;
        double pending = pendingDays(employee.getId(), type, year, null);
        double available = allocated - used - pending;

        return new BalanceResponse(employee.getId(), employee.getFirstName() + " " + employee.getLastName(), type, year, allocated, used, pending, available, true);
    }
}
