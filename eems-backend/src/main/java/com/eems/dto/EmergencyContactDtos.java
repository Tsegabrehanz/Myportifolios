package com.eems.dto;

import jakarta.validation.constraints.NotBlank;

public class EmergencyContactDtos {

    public record ContactResponse(
            Long id,
            Long employeeId,
            String name,
            String relationship,
            String phone,
            String email
    ) {}

    public record CreateContactRequest(
            @NotBlank String name,
            String relationship,
            String phone,
            String email
    ) {}
}
