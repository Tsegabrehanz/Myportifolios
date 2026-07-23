package com.eems.service;

import com.eems.audit.AuditService;
import com.eems.dto.SalaryDtos.CreateSalaryRequest;
import com.eems.dto.SalaryDtos.SalaryResponse;
import com.eems.entity.Employee;
import com.eems.entity.Salary;
import com.eems.exception.ForbiddenOperationException;
import com.eems.exception.ResourceNotFoundException;
import com.eems.repository.EmployeeRepository;
import com.eems.repository.SalaryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Deliberately more restricted than EmployeeAddressService/
 * EmergencyContactService/EmployeeDocumentService: a MANAGER does NOT
 * get access to a direct report's salary history, only
 * SUPER_ADMIN/HR_ADMIN (create+view) or the employee themself (view
 * only - see create() below). See Salary entity javadoc for the
 * reasoning.
 */
@Service
@RequiredArgsConstructor
public class SalaryService {

    private final SalaryRepository salaryRepository;
    private final EmployeeRepository employeeRepository;
    private final AuditService auditService;

    public List<SalaryResponse> list(Long employeeId, Authentication authentication) {
        Employee target = employeeRepository.findById(employeeId)
                .orElseThrow(() -> new ResourceNotFoundException("Employee not found: " + employeeId));
        enforceViewAccess(target, authentication);

        List<Salary> records = salaryRepository.findByEmployeeIdOrderByEffectiveFromDesc(employeeId);
        return records.stream().map(this::toResponse).toList();
    }

    /**
     * HR_ADMIN/SUPER_ADMIN only - not even the employee themself can set
     * their own salary. Closes out the previous "current" record
     * (effectiveTo null) by setting its effectiveTo to the day before
     * this new record's effectiveFrom, so there's never more than one
     * open-ended record per employee at a time.
     */
    @Transactional
    public SalaryResponse create(Long employeeId, CreateSalaryRequest request, Authentication authentication) {
        String role = topRole(authentication);
        if (!role.equals("ROLE_SUPER_ADMIN") && !role.equals("ROLE_HR_ADMIN")) {
            throw new ForbiddenOperationException("Only HR_ADMIN or SUPER_ADMIN can set an employee's salary");
        }

        Employee employee = employeeRepository.findById(employeeId)
                .orElseThrow(() -> new ResourceNotFoundException("Employee not found: " + employeeId));

        salaryRepository.findByEmployeeIdOrderByEffectiveFromDesc(employeeId).stream()
                .filter(s -> s.getEffectiveTo() == null)
                .findFirst()
                .ifPresent(previous -> {
                    previous.setEffectiveTo(request.effectiveFrom().minusDays(1));
                    salaryRepository.save(previous);
                });

        Salary salary = Salary.builder()
                .employee(employee)
                .basicSalary(request.basicSalary())
                .currency(request.currency())
                .taxNumber(request.taxNumber())
                .bankName(request.bankName())
                .iban(request.iban())
                .effectiveFrom(request.effectiveFrom())
                .build();
        salary = salaryRepository.save(salary);

        // Deliberately no salary figures in the audit detail string - the
        // audit log is readable by AUDITOR, which doesn't have salary
        // view access itself (see enforceViewAccess) and shouldn't learn
        // the figures this way either.
        auditService.record("Salary", salary.getId().toString(), "CREATE", "New salary record added for employee " + employeeId);

        return toResponse(salary);
    }

    /** Self or HR_ADMIN/SUPER_ADMIN only - notably NOT a direct manager, and NOT AUDITOR (financial data, not an operational record). */
    private void enforceViewAccess(Employee target, Authentication authentication) {
        String role = topRole(authentication);
        if (role.equals("ROLE_SUPER_ADMIN") || role.equals("ROLE_HR_ADMIN")) {
            return;
        }

        Employee requester = employeeRepository.findByUserEmail(authentication.getName())
                .orElseThrow(() -> new ResourceNotFoundException("Employee profile not found for current user"));

        if (requester.getId().equals(target.getId())) {
            return;
        }
        throw new ForbiddenOperationException("You do not have permission to view this employee's salary");
    }

    private String topRole(Authentication authentication) {
        return authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .findFirst()
                .orElse("ROLE_EMPLOYEE");
    }

    private SalaryResponse toResponse(Salary s) {
        boolean current = s.getEffectiveTo() == null;
        return new SalaryResponse(
                s.getId(), s.getEmployee().getId(), s.getBasicSalary(), s.getCurrency(),
                s.getTaxNumber(), s.getBankName(), s.getIban(), s.getEffectiveFrom(), s.getEffectiveTo(), current
        );
    }
}
