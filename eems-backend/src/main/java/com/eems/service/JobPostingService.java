package com.eems.service;

import com.eems.audit.AuditService;
import com.eems.dto.JobPostingDtos.CreateJobPostingRequest;
import com.eems.dto.JobPostingDtos.JobPostingResponse;
import com.eems.dto.JobPostingDtos.UpdateJobPostingRequest;
import com.eems.entity.*;
import com.eems.exception.ResourceNotFoundException;
import com.eems.repository.DepartmentRepository;
import com.eems.repository.JobPostingRepository;
import com.eems.repository.PositionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class JobPostingService {

    private final JobPostingRepository jobPostingRepository;
    private final DepartmentRepository departmentRepository;
    private final PositionRepository positionRepository;
    private final AuditService auditService;

    /**
     * SUPER_ADMIN/HR_ADMIN/AUDITOR see every posting regardless of
     * status/visibility (they're managing the recruitment process).
     * Everyone else only sees what an employee "browsing internal
     * openings" should: OPEN postings marked INTERNAL or BOTH - never
     * a DRAFT, never something CLOSED, and never an EXTERNAL-only
     * posting (that's for advertising outside the company, not for
     * employees to apply to internally).
     */
    public List<JobPostingResponse> list(Authentication authentication) {
        String role = topRole(authentication);
        List<JobPosting> postings = jobPostingRepository.findAllWithRelations();

        if (role.equals("ROLE_SUPER_ADMIN") || role.equals("ROLE_HR_ADMIN") || role.equals("ROLE_AUDITOR")) {
            return postings.stream().map(this::toResponse).toList();
        }

        return postings.stream()
                .filter(p -> p.getStatus() == JobPostingStatus.OPEN)
                .filter(p -> p.getVisibility() == JobPostingVisibility.INTERNAL || p.getVisibility() == JobPostingVisibility.BOTH)
                .map(this::toResponse)
                .toList();
    }

    public JobPostingResponse getById(Long id) {
        return toResponse(jobPostingRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Job posting not found: " + id)));
    }

    @Transactional
    public JobPostingResponse create(CreateJobPostingRequest request) {
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

        JobPosting posting = JobPosting.builder()
                .title(request.title())
                .description(request.description())
                .department(department)
                .position(position)
                .visibility(request.visibility())
                .status(JobPostingStatus.DRAFT)
                .location(request.location())
                .postedDate(request.postedDate())
                .closingDate(request.closingDate())
                .build();
        posting = jobPostingRepository.save(posting);

        auditService.record("JobPosting", posting.getId().toString(), "CREATE", "Job posting created: " + posting.getTitle());
        return toResponse(posting);
    }

    @Transactional
    public JobPostingResponse update(Long id, UpdateJobPostingRequest request) {
        JobPosting posting = jobPostingRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Job posting not found: " + id));

        if (request.title() != null) posting.setTitle(request.title());
        if (request.description() != null) posting.setDescription(request.description());
        if (request.visibility() != null) posting.setVisibility(request.visibility());
        if (request.status() != null) posting.setStatus(request.status());
        if (request.location() != null) posting.setLocation(request.location());
        if (request.closingDate() != null) posting.setClosingDate(request.closingDate());
        if (request.departmentId() != null) {
            Department department = departmentRepository.findById(request.departmentId())
                    .orElseThrow(() -> new ResourceNotFoundException("Department not found: " + request.departmentId()));
            posting.setDepartment(department);
        }
        if (request.positionId() != null) {
            Position position = positionRepository.findById(request.positionId())
                    .orElseThrow(() -> new ResourceNotFoundException("Position not found: " + request.positionId()));
            posting.setPosition(position);
        }

        posting = jobPostingRepository.save(posting);
        auditService.record("JobPosting", id.toString(), "UPDATE", "Job posting updated: " + posting.getTitle());
        return toResponse(posting);
    }

    @Transactional
    public void delete(Long id) {
        JobPosting posting = jobPostingRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Job posting not found: " + id));
        jobPostingRepository.delete(posting);
        auditService.record("JobPosting", id.toString(), "DELETE", "Job posting deleted: " + posting.getTitle());
    }

    private String topRole(Authentication authentication) {
        return authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .findFirst()
                .orElse("ROLE_EMPLOYEE");
    }

    private JobPostingResponse toResponse(JobPosting p) {
        return new JobPostingResponse(
                p.getId(),
                p.getTitle(),
                p.getDescription(),
                p.getDepartment() != null ? p.getDepartment().getId() : null,
                p.getDepartment() != null ? p.getDepartment().getName() : null,
                p.getPosition() != null ? p.getPosition().getId() : null,
                p.getPosition() != null ? p.getPosition().getTitle() : null,
                p.getVisibility(),
                p.getStatus(),
                p.getLocation(),
                p.getPostedDate(),
                p.getClosingDate()
        );
    }
}
