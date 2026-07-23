package com.eems.dto;

import jakarta.validation.constraints.NotBlank;

public class PositionDtos {

    public record PositionResponse(
            Long id,
            String title,
            String grade,
            String salaryBand,
            String jobDescription,
            Long departmentId,
            String departmentName
    ) {}

    public record CreatePositionRequest(
            @NotBlank String title,
            String grade,
            String salaryBand,
            String jobDescription,
            Long departmentId
    ) {}

    public record UpdatePositionRequest(
            String grade,
            String salaryBand,
            String jobDescription,
            Long departmentId
    ) {}
}
