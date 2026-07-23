package com.eems.service;

import com.eems.audit.AuditService;
import com.eems.dto.EmploymentContractDtos.ContractResponse;
import com.eems.dto.EmploymentContractDtos.CreateContractRequest;
import com.eems.entity.Employee;
import com.eems.entity.EmploymentContract;
import com.eems.exception.ForbiddenOperationException;
import com.eems.exception.ResourceNotFoundException;
import com.eems.repository.EmployeeRepository;
import com.eems.repository.EmploymentContractRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class EmploymentContractService {

    private final EmploymentContractRepository contractRepository;
    private final EmployeeRepository employeeRepository;
    private final AuditService auditService;

    public List<ContractResponse> list(Long employeeId, Authentication authentication) {
        Employee target = employeeRepository.findById(employeeId)
                .orElseThrow(() -> new ResourceNotFoundException("Employee not found: " + employeeId));
        enforceVisibility(target, authentication);

        return contractRepository.findByEmployeeIdOrderByStartDateDesc(employeeId).stream().map(this::toResponse).toList();
    }

    /** Creation is HR_ADMIN/SUPER_ADMIN only in practice - enforced by the general POST /api/employees/** rule in SecurityConfig, not repeated here. */
    @Transactional
    public ContractResponse create(Long employeeId, CreateContractRequest request, Authentication authentication) {
        Employee target = employeeRepository.findById(employeeId)
                .orElseThrow(() -> new ResourceNotFoundException("Employee not found: " + employeeId));
        enforceVisibility(target, authentication);

        EmploymentContract contract = EmploymentContract.builder()
                .employee(target)
                .contractType(request.contractType())
                .startDate(request.startDate())
                .endDate(request.endDate())
                .terms(request.terms())
                .build();
        contract = contractRepository.save(contract);

        auditService.record("EmploymentContract", contract.getId().toString(), "CREATE",
                "Contract added for employee " + employeeId + ": " + request.contractType());
        return toResponse(contract);
    }

    @Transactional
    public void delete(Long employeeId, Long contractId, Authentication authentication) {
        Employee target = employeeRepository.findById(employeeId)
                .orElseThrow(() -> new ResourceNotFoundException("Employee not found: " + employeeId));
        enforceVisibility(target, authentication);

        EmploymentContract contract = contractRepository.findById(contractId)
                .orElseThrow(() -> new ResourceNotFoundException("Contract not found: " + contractId));
        if (!contract.getEmployee().getId().equals(employeeId)) {
            throw new ForbiddenOperationException("This contract does not belong to that employee");
        }

        contractRepository.delete(contract);
        auditService.record("EmploymentContract", contractId.toString(), "DELETE", "Contract removed for employee " + employeeId);
    }

    /** Same rule as EmployeeService.enforceVisibility - self, direct manager, or HR/Admin/Auditor. */
    private void enforceVisibility(Employee target, Authentication authentication) {
        String role = topRole(authentication);
        if (role.equals("ROLE_SUPER_ADMIN") || role.equals("ROLE_HR_ADMIN") || role.equals("ROLE_AUDITOR")) {
            return;
        }

        Employee requester = employeeRepository.findByUserEmail(authentication.getName())
                .orElseThrow(() -> new ResourceNotFoundException("Employee profile not found for current user"));

        boolean isSelf = requester.getId().equals(target.getId());
        boolean isDirectManager = target.getManager() != null && target.getManager().getId().equals(requester.getId());

        if (role.equals("ROLE_MANAGER") && (isSelf || isDirectManager)) {
            return;
        }
        if (role.equals("ROLE_EMPLOYEE") && isSelf) {
            return;
        }
        throw new ForbiddenOperationException("You do not have permission to access this employee's employment contracts");
    }

    private String topRole(Authentication authentication) {
        return authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .findFirst()
                .orElse("ROLE_EMPLOYEE");
    }

    private ContractResponse toResponse(EmploymentContract c) {
        return new ContractResponse(c.getId(), c.getEmployee().getId(), c.getContractType(), c.getStartDate(), c.getEndDate(), c.getTerms());
    }
}
