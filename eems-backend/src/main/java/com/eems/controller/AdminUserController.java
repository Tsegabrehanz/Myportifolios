package com.eems.controller;

import com.eems.dto.AdminUserDtos.PasswordResetResponse;
import com.eems.dto.AdminUserDtos.UpdateEnabledRequest;
import com.eems.dto.AdminUserDtos.UpdateRoleRequest;
import com.eems.dto.AdminUserDtos.UserSummaryResponse;
import com.eems.service.AdminUserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin/users")
@RequiredArgsConstructor
public class AdminUserController {

    private final AdminUserService adminUserService;

    @GetMapping
    public List<UserSummaryResponse> list() {
        return adminUserService.listUsers();
    }

    @PatchMapping("/{id}/role")
    public UserSummaryResponse updateRole(@PathVariable Long id, @Valid @RequestBody UpdateRoleRequest request, Authentication authentication) {
        return adminUserService.updateRole(id, request.role(), authentication);
    }

    @PatchMapping("/{id}/enabled")
    public UserSummaryResponse updateEnabled(@PathVariable Long id, @Valid @RequestBody UpdateEnabledRequest request, Authentication authentication) {
        return adminUserService.setEnabled(id, request.enabled(), authentication);
    }

    /**
     * Generates a new secure temporary password for this user and forces
     * a change on next login. Returns the plaintext password exactly
     * once - it cannot be retrieved again after this response.
     */
    @PostMapping("/{id}/reset-password")
    public PasswordResetResponse resetPassword(@PathVariable Long id, Authentication authentication) {
        return adminUserService.resetPassword(id, authentication);
    }
}
