package com.eems.dto;

import jakarta.validation.constraints.NotBlank;

public class DepartmentDtos {

    public record DepartmentResponse(
            Long id,
            String name,
            Long parentDepartmentId,
            String location
    ) {}

    public record CreateDepartmentRequest(
            @NotBlank String name,
            Long parentDepartmentId,
            String location
    ) {}
}
