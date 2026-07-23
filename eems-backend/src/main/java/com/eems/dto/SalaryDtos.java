package com.eems.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.time.LocalDate;

public class SalaryDtos {

    public record SalaryResponse(
            Long id,
            Long employeeId,
            double basicSalary,
            String currency,
            String taxNumber,
            String bankName,
            String iban,
            LocalDate effectiveFrom,
            LocalDate effectiveTo,
            boolean current
    ) {}

    public record CreateSalaryRequest(
            @NotNull @Positive Double basicSalary,
            @NotBlank String currency,
            String taxNumber,
            String bankName,
            String iban,
            @NotNull LocalDate effectiveFrom
    ) {}
}
