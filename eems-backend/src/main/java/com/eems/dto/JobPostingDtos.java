package com.eems.dto;

import com.eems.entity.JobPostingStatus;
import com.eems.entity.JobPostingVisibility;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

public class JobPostingDtos {

    public record JobPostingResponse(
            Long id,
            String title,
            String description,
            Long departmentId,
            String departmentName,
            Long positionId,
            String positionTitle,
            JobPostingVisibility visibility,
            JobPostingStatus status,
            String location,
            LocalDate postedDate,
            LocalDate closingDate
    ) {}

    public record CreateJobPostingRequest(
            @NotBlank String title,
            String description,
            Long departmentId,
            Long positionId,
            @NotNull JobPostingVisibility visibility,
            String location,
            @NotNull LocalDate postedDate,
            LocalDate closingDate
    ) {}

    public record UpdateJobPostingRequest(
            String title,
            String description,
            Long departmentId,
            Long positionId,
            JobPostingVisibility visibility,
            JobPostingStatus status,
            String location,
            LocalDate closingDate
    ) {}
}
