package com.eems.service;

import com.eems.audit.AuditService;
import com.eems.dto.PositionDtos.CreatePositionRequest;
import com.eems.dto.PositionDtos.PositionResponse;
import com.eems.dto.PositionDtos.UpdatePositionRequest;
import com.eems.entity.Department;
import com.eems.entity.Position;
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
public class PositionService {

    private final PositionRepository positionRepository;
    private final DepartmentRepository departmentRepository;
    private final EmployeeRepository employeeRepository;
    private final AuditService auditService;

    @Transactional
    public PositionResponse create(CreatePositionRequest request) {
        // Same reasoning as DepartmentService.create's guard: no DB-level
        // unique constraint on title, so without this check a duplicate
        // (e.g. re-importing "Software Engineer" when dev seed data
        // already created one) would silently create a second row and
        // break findByTitleIgnoreCase for the next lookup.
        positionRepository.findByTitleIgnoreCase(request.title()).ifPresent(existing -> {
            throw new IllegalArgumentException("A position titled \"" + request.title() + "\" already exists (id " + existing.getId() + ")");
        });

        Department department = null;
        if (request.departmentId() != null) {
            department = departmentRepository.findById(request.departmentId())
                    .orElseThrow(() -> new ResourceNotFoundException("Department not found: " + request.departmentId()));
        }

        Position position = Position.builder()
                .title(request.title())
                .grade(request.grade())
                .salaryBand(request.salaryBand())
                .jobDescription(request.jobDescription())
                .department(department)
                .build();

        position = positionRepository.save(position);
        auditService.record("Position", position.getId().toString(), "CREATE", "Position created: " + position.getTitle());
        return toResponse(position);
    }

    /**
     * title is deliberately NOT editable here - it's the identifier CSV
     * import (positionTitle column) and other lookups match against
     * (findByTitleIgnoreCase), so renaming it after the fact would
     * silently break anything that referenced the old title. Everything
     * else, including the job description, can change freely.
     */
    @Transactional
    public PositionResponse update(Long id, UpdatePositionRequest request) {
        Position position = positionRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Position not found: " + id));

        if (request.grade() != null) position.setGrade(request.grade());
        if (request.salaryBand() != null) position.setSalaryBand(request.salaryBand());
        if (request.jobDescription() != null) position.setJobDescription(request.jobDescription());
        if (request.departmentId() != null) {
            Department department = departmentRepository.findById(request.departmentId())
                    .orElseThrow(() -> new ResourceNotFoundException("Department not found: " + request.departmentId()));
            position.setDepartment(department);
        }

        position = positionRepository.save(position);
        auditService.record("Position", id.toString(), "UPDATE", "Position updated: " + position.getTitle());
        return toResponse(position);
    }

    public List<PositionResponse> listAll() {
        return positionRepository.findAll().stream().map(this::toResponse).toList();
    }

    public PositionResponse getById(Long id) {
        return toResponse(positionRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Position not found: " + id)));
    }

    @Transactional
    public void delete(Long id) {
        Position position = positionRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Position not found: " + id));

        long employeeCount = employeeRepository.countByPositionId(id);
        if (employeeCount > 0) {
            throw new ForbiddenOperationException("Cannot delete: " + employeeCount + " employee(s) still hold this position. Reassign them first.");
        }

        positionRepository.delete(position);
        auditService.record("Position", id.toString(), "DELETE", "Position deleted: " + position.getTitle());
    }

    private PositionResponse toResponse(Position p) {
        return new PositionResponse(
                p.getId(),
                p.getTitle(),
                p.getGrade(),
                p.getSalaryBand(),
                p.getJobDescription(),
                p.getDepartment() != null ? p.getDepartment().getId() : null,
                p.getDepartment() != null ? p.getDepartment().getName() : null
        );
    }
}
