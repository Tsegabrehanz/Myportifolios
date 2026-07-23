package com.eems.service;

import com.eems.audit.AuditService;
import com.eems.dto.DepartmentDtos.CreateDepartmentRequest;
import com.eems.dto.DepartmentDtos.DepartmentResponse;
import com.eems.entity.Department;
import com.eems.exception.ForbiddenOperationException;
import com.eems.exception.ResourceNotFoundException;
import com.eems.repository.DepartmentRepository;
import com.eems.repository.EmployeeRepository;
import com.eems.repository.PositionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class DepartmentService {

    private final DepartmentRepository departmentRepository;
    private final EmployeeRepository employeeRepository;
    private final PositionRepository positionRepository;
    private final AuditService auditService;

    @Transactional
    public DepartmentResponse create(CreateDepartmentRequest request) {
        // Departments have no DB-level unique constraint on name, so
        // without this check a duplicate name (e.g. importing
        // "Engineering" again when the dev seed data already created
        // one) would silently create a second row - and then
        // findByNameIgnoreCase (used by employee/position import to
        // resolve a department by name) would throw
        // IncorrectResultSizeDataAccessException the next time anyone
        // looks that name up, since it expects 0-or-1 results.
        departmentRepository.findByNameIgnoreCase(request.name()).ifPresent(existing -> {
            throw new IllegalArgumentException("A department named \"" + request.name() + "\" already exists (id " + existing.getId() + ")");
        });

        Department parent = null;
        if (request.parentDepartmentId() != null) {
            parent = departmentRepository.findById(request.parentDepartmentId())
                    .orElseThrow(() -> new ResourceNotFoundException("Parent department not found: " + request.parentDepartmentId()));
        }

        Department department = Department.builder()
                .name(request.name())
                .parentDepartment(parent)
                .location(request.location())
                .build();

        department = departmentRepository.save(department);
        auditService.record("Department", department.getId().toString(), "CREATE", "Department created: " + department.getName());
        return toResponse(department);
    }

    public List<DepartmentResponse> listAll() {
        return departmentRepository.findAll().stream().map(this::toResponse).toList();
    }

    public DepartmentResponse getById(Long id) {
        return toResponse(departmentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Department not found: " + id)));
    }

    /**
     * Hard delete, guarded by referential-integrity checks rather than a
     * soft-delete flag - simpler for a department that's genuinely never
     * been used, and refuses cleanly (rather than a raw FK-constraint
     * DB error) if it's still referenced by employees, positions, or
     * child departments.
     */
    @Transactional
    public void delete(Long id) {
        Department department = departmentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Department not found: " + id));

        long employeeCount = employeeRepository.countByDepartmentId(id);
        if (employeeCount > 0) {
            throw new ForbiddenOperationException("Cannot delete: " + employeeCount + " employee(s) still belong to this department. Reassign them first.");
        }
        long positionCount = positionRepository.findAll().stream()
                .filter(p -> p.getDepartment() != null && p.getDepartment().getId().equals(id))
                .count();
        if (positionCount > 0) {
            throw new ForbiddenOperationException("Cannot delete: " + positionCount + " position(s) still belong to this department. Reassign or delete them first.");
        }
        if (!departmentRepository.findByParentDepartmentId(id).isEmpty()) {
            throw new ForbiddenOperationException("Cannot delete: this department has child departments. Reassign or delete them first.");
        }

        departmentRepository.delete(department);
        auditService.record("Department", id.toString(), "DELETE", "Department deleted: " + department.getName());
    }

    private DepartmentResponse toResponse(Department d) {
        return new DepartmentResponse(
                d.getId(),
                d.getName(),
                d.getParentDepartment() != null ? d.getParentDepartment().getId() : null,
                d.getLocation()
        );
    }
}
