package com.eems.service;

import com.eems.audit.AuditService;
import com.eems.dto.EmployeeAddressDtos.AddressResponse;
import com.eems.dto.EmployeeAddressDtos.UpsertAddressRequest;
import com.eems.entity.Employee;
import com.eems.entity.EmployeeAddress;
import com.eems.exception.ForbiddenOperationException;
import com.eems.exception.ResourceNotFoundException;
import com.eems.repository.EmployeeAddressRepository;
import com.eems.repository.EmployeeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class EmployeeAddressService {

    private final EmployeeAddressRepository addressRepository;
    private final EmployeeRepository employeeRepository;
    private final AuditService auditService;

    /** Same visibility rule as EmployeeService: self, direct manager, or HR/Admin/Auditor. Returns null fields (not 404) if no address has been recorded yet. */
    public AddressResponse get(Long employeeId, Authentication authentication) {
        Employee target = employeeRepository.findById(employeeId)
                .orElseThrow(() -> new ResourceNotFoundException("Employee not found: " + employeeId));
        enforceVisibility(target, authentication);

        return addressRepository.findByEmployeeId(employeeId)
                .map(this::toResponse)
                .orElse(new AddressResponse(employeeId, null, null, null, null));
    }

    @Transactional
    public AddressResponse upsert(Long employeeId, UpsertAddressRequest request, Authentication authentication) {
        Employee target = employeeRepository.findById(employeeId)
                .orElseThrow(() -> new ResourceNotFoundException("Employee not found: " + employeeId));
        enforceVisibility(target, authentication);

        EmployeeAddress address = addressRepository.findByEmployeeId(employeeId)
                .orElseGet(() -> EmployeeAddress.builder().employee(target).build());
        address.setCountry(request.country());
        address.setCity(request.city());
        address.setStreet(request.street());
        address.setPostalCode(request.postalCode());
        address = addressRepository.save(address);

        auditService.record("EmployeeAddress", employeeId.toString(), "UPSERT", "Address updated for employee " + employeeId);
        return toResponse(address);
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
        throw new ForbiddenOperationException("You do not have permission to access this employee's address");
    }

    private String topRole(Authentication authentication) {
        return authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .findFirst()
                .orElse("ROLE_EMPLOYEE");
    }

    private AddressResponse toResponse(EmployeeAddress address) {
        return new AddressResponse(
                address.getEmployee().getId(),
                address.getCountry(),
                address.getCity(),
                address.getStreet(),
                address.getPostalCode()
        );
    }
}
