package com.eems.dto;

import com.eems.entity.Role;
import jakarta.validation.constraints.NotNull;

public class AdminUserDtos {

    public record UserSummaryResponse(
            Long id,
            String email,
            Role role,
            boolean enabled,
            boolean mustChangePassword,
            Long employeeId,
            String employeeName
    ) {}

    public record UpdateRoleRequest(
            @NotNull Role role
    ) {}

    public record UpdateEnabledRequest(
            @NotNull Boolean enabled
    ) {}

    /**
     * The only response that ever contains a plaintext temporary
     * password - shown once, right after an admin-triggered reset. It
     * isn't stored anywhere and can't be retrieved again afterward.
     */
    public record PasswordResetResponse(
            UserSummaryResponse user,
            String temporaryPassword
    ) {}
}
