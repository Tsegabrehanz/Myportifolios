package com.eems.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public class PasswordChangeDtos {

    public record UpdatePhoneNumberRequest(
            @NotBlank
            @Pattern(regexp = "^\\+[1-9]\\d{6,14}$", message = "must be in E.164 format, e.g. +491701234567")
            String phoneNumber
    ) {}

    public record InitiateChangeRequest(
            @NotBlank String currentPassword,
            @NotBlank @Size(min = 8, message = "new password must be at least 8 characters") String newPassword
    ) {}

    public record InitiateChangeResponse(
            String message,
            String maskedPhoneNumber
    ) {}

    public record ConfirmChangeRequest(
            @NotBlank @Pattern(regexp = "^\\d{6}$", message = "must be a 6-digit code") String otpCode
    ) {}
}
