package com.eems.service;

import com.eems.audit.AuditService;
import com.eems.dto.EmergencyContactDtos.ContactResponse;
import com.eems.dto.EmergencyContactDtos.CreateContactRequest;
import com.eems.entity.Employee;
import com.eems.entity.EmergencyContact;
import com.eems.exception.ForbiddenOperationException;
import com.eems.exception.ResourceNotFoundException;
import com.eems.repository.EmergencyContactRepository;
import com.eems.repository.EmployeeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class EmergencyContactService {

    private final EmergencyContactRepository contactRepository;
    private final EmployeeRepository employeeRepository;
    private final AuditService auditService;

    public List<ContactResponse> list(Long employeeId, Authentication authentication) {
        Employee target = employeeRepository.findById(employeeId)
                .orElseThrow(() -> new ResourceNotFoundException("Employee not found: " + employeeId));
        enforceVisibility(target, authentication);

        return contactRepository.findByEmployeeId(employeeId).stream().map(this::toResponse).toList();
    }

    @Transactional
    public ContactResponse create(Long employeeId, CreateContactRequest request, Authentication authentication) {
        Employee target = employeeRepository.findById(employeeId)
                .orElseThrow(() -> new ResourceNotFoundException("Employee not found: " + employeeId));
        enforceVisibility(target, authentication);

        EmergencyContact contact = EmergencyContact.builder()
                .employee(target)
                .name(request.name())
                .relationship(request.relationship())
                .phone(request.phone())
                .email(request.email())
                .build();
        contact = contactRepository.save(contact);

        auditService.record("EmergencyContact", contact.getId().toString(), "CREATE", "Emergency contact added for employee " + employeeId);
        return toResponse(contact);
    }

    @Transactional
    public void delete(Long employeeId, Long contactId, Authentication authentication) {
        Employee target = employeeRepository.findById(employeeId)
                .orElseThrow(() -> new ResourceNotFoundException("Employee not found: " + employeeId));
        enforceVisibility(target, authentication);

        EmergencyContact contact = contactRepository.findById(contactId)
                .orElseThrow(() -> new ResourceNotFoundException("Emergency contact not found: " + contactId));
        if (!contact.getEmployee().getId().equals(employeeId)) {
            throw new ForbiddenOperationException("This emergency contact does not belong to that employee");
        }

        contactRepository.delete(contact);
        auditService.record("EmergencyContact", contactId.toString(), "DELETE", "Emergency contact removed for employee " + employeeId);
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
        throw new ForbiddenOperationException("You do not have permission to access this employee's emergency contacts");
    }

    private String topRole(Authentication authentication) {
        return authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .findFirst()
                .orElse("ROLE_EMPLOYEE");
    }

    private ContactResponse toResponse(EmergencyContact c) {
        return new ContactResponse(c.getId(), c.getEmployee().getId(), c.getName(), c.getRelationship(), c.getPhone(), c.getEmail());
    }
}
