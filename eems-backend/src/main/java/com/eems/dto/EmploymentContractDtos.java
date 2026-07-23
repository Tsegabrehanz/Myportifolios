package com.eems.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

public class EmploymentContractDtos {

    public record ContractResponse(
            Long id,
            Long employeeId,
            String contractType,
            LocalDate startDate,
            LocalDate endDate,
            String terms
    ) {}

    public record CreateContractRequest(
            @NotBlank String contractType,
            @NotNull LocalDate startDate,
            LocalDate endDate,
            String terms
    ) {}
}
