package com.eems.service;

import com.eems.audit.AuditService;
import com.eems.dto.AdminUserDtos.PasswordResetResponse;
import com.eems.dto.AdminUserDtos.UserSummaryResponse;
import com.eems.entity.Employee;
import com.eems.entity.Role;
import com.eems.entity.User;
import com.eems.exception.ForbiddenOperationException;
import com.eems.exception.ResourceNotFoundException;
import com.eems.repository.EmployeeRepository;
import com.eems.repository.UserRepository;
import com.eems.security.CredentialGenerator;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Admin-side role grant/revoke, account enable/disable, and password
 * reset (SUPER_ADMIN only - see SecurityConfig). Deliberately separate
 * from EmployeeService: this operates on the User/auth identity, not
 * the Employee HR record, and the two shouldn't be conflated even
 * though most users have both.
 */
@Service
@RequiredArgsConstructor
public class AdminUserService {

    private final UserRepository userRepository;
    private final EmployeeRepository employeeRepository;
    private final PasswordEncoder passwordEncoder;
    private final CredentialGenerator credentialGenerator;
    private final AuditService auditService;

    public List<UserSummaryResponse> listUsers() {
        return userRepository.findAll().stream().map(this::toResponse).toList();
    }

    @Transactional
    public UserSummaryResponse updateRole(Long userId, Role newRole, Authentication authentication) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + userId));

        guardAgainstSelfModification(user, authentication, "change your own role");

        Role previousRole = user.getRole();
        user.setRole(newRole);
        user = userRepository.save(user);

        auditService.record("User", userId.toString(), "ROLE_CHANGED",
                "Role changed from " + previousRole + " to " + newRole);

        return toResponse(user);
    }

    @Transactional
    public UserSummaryResponse setEnabled(Long userId, boolean enabled, Authentication authentication) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + userId));

        guardAgainstSelfModification(user, authentication, enabled ? "re-enable your own account" : "disable your own account");

        user.setEnabled(enabled);
        user = userRepository.save(user);

        auditService.record("User", userId.toString(), enabled ? "ACCOUNT_ENABLED" : "ACCOUNT_DISABLED",
                "Account " + (enabled ? "enabled" : "disabled") + " by admin");

        return toResponse(user);
    }

    /**
     * Generates a new secure temporary password for a user (e.g. a
     * manager who's locked out, or as part of onboarding), sets
     * mustChangePassword so they're forced to set their own on next
     * login, and returns the plaintext temporary password exactly once.
     * Self-reset is blocked - use the normal SMS-confirmed change-password
     * flow for your own account instead, since that's what it's for.
     */
    @Transactional
    public PasswordResetResponse resetPassword(Long userId, Authentication authentication) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + userId));

        guardAgainstSelfModification(user, authentication, "reset your own password this way");

        String temporaryPassword = credentialGenerator.generateTemporaryPassword();
        user.setPasswordHash(passwordEncoder.encode(temporaryPassword));
        user.setMustChangePassword(true);
        user = userRepository.save(user);

        auditService.record("User", userId.toString(), "PASSWORD_RESET_BY_ADMIN",
                "Temporary password issued by admin; user must change it on next login");

        return new PasswordResetResponse(toResponse(user), temporaryPassword);
    }

    /**
     * Prevents a SUPER_ADMIN from changing their own role, disabling
     * their own account, or resetting their own password this way -
     * all are either lockout risks or already have a proper dedicated
     * flow (the SMS-confirmed change-password page). Use a different
     * SUPER_ADMIN account for the first two if it's ever genuinely needed.
     */
    private void guardAgainstSelfModification(User target, Authentication authentication, String action) {
        if (target.getEmail().equalsIgnoreCase(authentication.getName())) {
            throw new ForbiddenOperationException("You cannot " + action + ". Ask another SUPER_ADMIN to do this instead.");
        }
    }

    private UserSummaryResponse toResponse(User user) {
        Employee employee = employeeRepository.findByUserId(user.getId()).orElse(null);
        return new UserSummaryResponse(
                user.getId(),
                user.getEmail(),
                user.getRole(),
                user.isEnabled(),
                user.isMustChangePassword(),
                employee != null ? employee.getId() : null,
                employee != null ? employee.getFirstName() + " " + employee.getLastName() : null
        );
    }
}
