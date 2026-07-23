package com.eems.service;

import com.eems.audit.AuditService;
import com.eems.dto.OfferLetterDtos.OfferLetterData;
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

import java.time.LocalDate;
import java.util.List;
import java.util.Locale;

/**
 * Assembles an OfferLetterData from an employee's existing records
 * (position, department, most recent salary if one exists) and
 * generates a PDF via OfferLetterPdfExporter. Same access rule as
 * Salary itself (self + HR_ADMIN/SUPER_ADMIN only, no manager) since
 * the letter can include a compensation figure.
 */
@Service
@RequiredArgsConstructor
public class OfferLetterService {

    private final EmployeeRepository employeeRepository;
    private final SalaryRepository salaryRepository;
    private final AuditService auditService;

    public OfferLetterData buildData(Long employeeId, Authentication authentication) {
        Employee employee = employeeRepository.findById(employeeId)
                .orElseThrow(() -> new ResourceNotFoundException("Employee not found: " + employeeId));

        enforceAccess(employee, authentication);

        String employeeName = employee.getFirstName() + " " + employee.getLastName();
        String positionTitle = employee.getPosition() != null ? employee.getPosition().getTitle() : "New Position";
        String departmentName = employee.getDepartment() != null ? employee.getDepartment().getName() : null;
        LocalDate startDate = employee.getHireDate() != null ? employee.getHireDate() : LocalDate.now();

        List<Salary> salaries = salaryRepository.findByEmployeeIdOrderByEffectiveFromDesc(employeeId);
        String compensationLine = salaries.isEmpty() ? null : formatCompensation(salaries.get(0));

        auditService.record("Employee", employeeId.toString(), "OFFER_LETTER_GENERATED",
                "Offer letter PDF generated for " + employeeName);

        return new OfferLetterData(employeeName, positionTitle, departmentName, startDate, LocalDate.now(), compensationLine);
    }

    /** Same rule as SalaryService.enforceViewAccess - self or HR_ADMIN/SUPER_ADMIN only, deliberately not a manager. */
    private void enforceAccess(Employee target, Authentication authentication) {
        String role = topRole(authentication);
        if (role.equals("ROLE_SUPER_ADMIN") || role.equals("ROLE_HR_ADMIN")) {
            return;
        }

        Employee requester = employeeRepository.findByUserEmail(authentication.getName())
                .orElseThrow(() -> new ResourceNotFoundException("Employee profile not found for current user"));

        if (requester.getId().equals(target.getId())) {
            return;
        }
        throw new ForbiddenOperationException("You do not have permission to generate this employee's offer letter");
    }

    private String topRole(Authentication authentication) {
        return authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .findFirst()
                .orElse("ROLE_EMPLOYEE");
    }

    private String formatCompensation(Salary salary) {
        return String.format(Locale.ROOT,
                "Your starting base compensation will be %s %,.2f per year, paid in accordance with the company's standard payroll schedule.",
                salary.getCurrency(), salary.getBasicSalary());
    }
}
