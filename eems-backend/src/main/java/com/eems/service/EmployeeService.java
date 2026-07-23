package com.eems.service;

import com.eems.audit.AuditService;
import com.eems.dto.EmployeeDtos.CreateEmployeeRequest;
import com.eems.dto.EmployeeDtos.CreateEmployeeResponse;
import com.eems.dto.EmployeeDtos.EmployeeResponse;
import com.eems.dto.EmployeeDtos.UpdateEmployeeRequest;
import com.eems.entity.*;
import com.eems.exception.ForbiddenOperationException;
import com.eems.exception.ResourceNotFoundException;
import com.eems.repository.DepartmentRepository;
import com.eems.repository.EmployeeRepository;
import com.eems.repository.PositionRepository;
import com.eems.repository.UserRepository;
import com.eems.security.CredentialGenerator;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

@Service
@RequiredArgsConstructor
public class EmployeeService {

    private final EmployeeRepository employeeRepository;
    private final DepartmentRepository departmentRepository;
    private final PositionRepository positionRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuditService auditService;
    private final CredentialGenerator credentialGenerator;
    private final AppSettingsService appSettingsService;

    @Transactional
    public CreateEmployeeResponse create(CreateEmployeeRequest request) {
        boolean emailGenerated = request.email() == null || request.email().isBlank();
        String email = emailGenerated
                ? credentialGenerator.generateUsername(request.firstName(), request.lastName(), userRepository::existsByEmail)
                : request.email();

        if (userRepository.existsByEmail(email)) {
            throw new IllegalArgumentException("A user with this email already exists");
        }

        boolean passwordGenerated = request.initialPassword() == null || request.initialPassword().isBlank();
        String initialPassword = passwordGenerated
                ? credentialGenerator.generateTemporaryPassword()
                : request.initialPassword();

        User user = User.builder()
                .email(email)
                .passwordHash(passwordEncoder.encode(initialPassword))
                .role(Role.EMPLOYEE)
                .mustChangePassword(passwordGenerated) // only force a change for passwords we generated - respect an explicit admin-supplied one
                .build();
        user = userRepository.save(user);

        Department department = null;
        if (request.departmentId() != null) {
            department = departmentRepository.findById(request.departmentId())
                    .orElseThrow(() -> new ResourceNotFoundException("Department not found: " + request.departmentId()));
        }

        Position position = null;
        if (request.positionId() != null) {
            position = positionRepository.findById(request.positionId())
                    .orElseThrow(() -> new ResourceNotFoundException("Position not found: " + request.positionId()));
        }

        Employee manager = null;
        if (request.managerId() != null) {
            manager = employeeRepository.findById(request.managerId())
                    .orElseThrow(() -> new ResourceNotFoundException("Manager not found: " + request.managerId()));
        }

        Employee employee = Employee.builder()
                .user(user)
                .employeeCode(appSettingsService.generateNextEmployeeCode())
                .firstName(request.firstName())
                .lastName(request.lastName())
                .nationalId(request.nationalId())
                .position(position)
                .department(department)
                .manager(manager)
                .hireDate(request.hireDate())
                .status(EmployeeStatus.ONBOARDING)
                .build();

        employee = employeeRepository.save(employee);
        auditService.record("Employee", employee.getId().toString(), "CREATE", "Employee onboarded: " + employee.getFirstName() + " " + employee.getLastName());

        return new CreateEmployeeResponse(
                toResponse(employee),
                emailGenerated ? email : null,
                passwordGenerated ? initialPassword : null
        );
    }

    public EmployeeResponse getById(Long id, Authentication authentication) {
        Employee employee = employeeRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Employee not found: " + id));

        enforceVisibility(employee, authentication);
        auditService.record("Employee", id.toString(), "VIEW", "Employee record viewed");
        return toResponse(employee);
    }

    public List<EmployeeResponse> listVisibleTo(Authentication authentication) {
        String role = topRole(authentication);
        if (role.equals("ROLE_SUPER_ADMIN") || role.equals("ROLE_HR_ADMIN") || role.equals("ROLE_AUDITOR")) {
            return employeeRepository.findAllWithRelations().stream().map(this::toResponse).toList();
        }
        if (role.equals("ROLE_MANAGER")) {
            Employee manager = employeeRepository.findByUserEmail(authentication.getName())
                    .orElseThrow(() -> new ResourceNotFoundException("Employee profile not found for current user"));
            return employeeRepository.findByManagerIdWithRelations(manager.getId()).stream().map(this::toResponse).toList();
        }
        // Plain employee: only their own record (FR-1.3) - a single row, so
        // the N+1 concern that justifies the fetch-joined queries above
        // doesn't apply here; no change needed.
        Employee self = employeeRepository.findByUserEmail(authentication.getName())
                .orElseThrow(() -> new ResourceNotFoundException("Employee profile not found for current user"));
        return List.of(toResponse(self));
    }

    @Transactional
    public EmployeeResponse update(Long id, UpdateEmployeeRequest request, Authentication authentication) {
        Employee employee = employeeRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Employee not found: " + id));

        enforceVisibility(employee, authentication);

        if (request.firstName() != null) employee.setFirstName(request.firstName());
        if (request.lastName() != null) employee.setLastName(request.lastName());
        if (request.status() != null) employee.setStatus(request.status());
        if (request.positionId() != null) {
            Position position = positionRepository.findById(request.positionId())
                    .orElseThrow(() -> new ResourceNotFoundException("Position not found: " + request.positionId()));
            employee.setPosition(position);
        }
        if (request.departmentId() != null) {
            Department dept = departmentRepository.findById(request.departmentId())
                    .orElseThrow(() -> new ResourceNotFoundException("Department not found: " + request.departmentId()));
            employee.setDepartment(dept);
        }
        if (request.managerId() != null) {
            if (request.managerId().equals(employee.getId())) {
                throw new IllegalArgumentException("An employee cannot be their own manager.");
            }
            Employee manager = employeeRepository.findById(request.managerId())
                    .orElseThrow(() -> new ResourceNotFoundException("Manager not found: " + request.managerId()));
            employee.setManager(manager);
        }

        employee = employeeRepository.save(employee);
        auditService.record("Employee", id.toString(), "UPDATE", "Employee record updated");
        return toResponse(employee);
    }

    /**
     * Offboarding workflow (FR-2.3): revoke system access, mark record for
     * exit, and set exit date. Full anonymization/retention handling lives
     * in the GDPR module (future work) and is triggered from here.
     */
    @Transactional
    public void offboard(Long id) {
        Employee employee = employeeRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Employee not found: " + id));

        employee.setStatus(EmployeeStatus.OFFBOARDED);
        employee.setExitDate(LocalDate.now());
        if (employee.getUser() != null) {
            employee.getUser().setEnabled(false);
        }
        employeeRepository.save(employee);
        auditService.record("Employee", id.toString(), "OFFBOARD", "Employee offboarded and access revoked");
    }

    /** Enforces FR-1.3: managers may only view/edit their direct reports; employees only their own record. */
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
        throw new ForbiddenOperationException("You do not have permission to access this employee record");
    }

    private String topRole(Authentication authentication) {
        return authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .findFirst()
                .orElse("ROLE_EMPLOYEE");
    }

    private EmployeeResponse toResponse(Employee e) {
        Position position = e.getPosition();
        return new EmployeeResponse(
                e.getId(),
                e.getEmployeeCode(),
                e.getFirstName(),
                e.getLastName(),
                e.getUser() != null ? e.getUser().getEmail() : null,
                position != null ? position.getId() : null,
                position != null ? position.getTitle() : null,
                e.getDepartment() != null ? e.getDepartment().getId() : null,
                e.getDepartment() != null ? e.getDepartment().getName() : null,
                e.getManager() != null ? e.getManager().getId() : null,
                e.getManager() != null ? e.getManager().getFirstName() + " " + e.getManager().getLastName() : null,
                e.getHireDate(),
                e.getExitDate(),
                e.getStatus()
        );
    }
}
