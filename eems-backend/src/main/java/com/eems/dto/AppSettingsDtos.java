package com.eems.dto;

import jakarta.validation.constraints.NotNull;

public class AppSettingsDtos {

    public record LogoStatusResponse(
            boolean hasCustomLogo
    ) {}

    public record EmployeeIdFormatResponse(
            String prefix,
            String suffix,
            int nextSequence,
            String exampleNextCode
    ) {}

    /** Changing the format only affects employees created after this call - existing employeeCodes are never regenerated. */
    public record UpdateEmployeeIdFormatRequest(
            @NotNull String prefix,
            @NotNull String suffix
    ) {}
}
