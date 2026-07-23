package com.eems.controller;

import com.eems.dto.JobPostingDtos.CreateJobPostingRequest;
import com.eems.dto.JobPostingDtos.JobPostingResponse;
import com.eems.dto.JobPostingDtos.UpdateJobPostingRequest;
import com.eems.service.JobPostingService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/job-postings")
@RequiredArgsConstructor
public class JobPostingController {

    private final JobPostingService jobPostingService;

    /** Visibility-scoped in the service: HR/Admin/Auditor see everything, everyone else only OPEN + INTERNAL/BOTH postings. */
    @GetMapping
    public List<JobPostingResponse> list(Authentication authentication) {
        return jobPostingService.list(authentication);
    }

    @GetMapping("/{id}")
    public JobPostingResponse getById(@PathVariable Long id) {
        return jobPostingService.getById(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public JobPostingResponse create(@Valid @RequestBody CreateJobPostingRequest request) {
        return jobPostingService.create(request);
    }

    @PutMapping("/{id}")
    public JobPostingResponse update(@PathVariable Long id, @RequestBody UpdateJobPostingRequest request) {
        return jobPostingService.update(id, request);
    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable Long id) {
        jobPostingService.delete(id);
    }
}
