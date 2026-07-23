package com.eems.service;

import com.eems.audit.AuditService;
import com.eems.dto.PasswordChangeDtos.ConfirmChangeRequest;
import com.eems.dto.PasswordChangeDtos.InitiateChangeRequest;
import com.eems.dto.PasswordChangeDtos.InitiateChangeResponse;
import com.eems.entity.PasswordChangeRequest;
import com.eems.entity.User;
import com.eems.exception.ForbiddenOperationException;
import com.eems.exception.ResourceNotFoundException;
import com.eems.exception.TooManyRequestsException;
import com.eems.repository.PasswordChangeRequestRepository;
import com.eems.repository.UserRepository;
import com.eems.security.RateLimiter;
import com.eems.sms.SmsSender;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

@Service
@RequiredArgsConstructor
public class PasswordChangeService {

    private static final int OTP_LENGTH = 6;
    private static final int OTP_TTL_MINUTES = 5;
    private static final int MAX_OTP_ATTEMPTS = 5;
    private static final int MAX_INITIATE_ATTEMPTS_PER_HOUR = 5;

    private final UserRepository userRepository;
    private final PasswordChangeRequestRepository requestRepository;
    private final PasswordEncoder passwordEncoder;
    private final SmsSender smsSender;
    private final RateLimiter rateLimiter;
    private final AuditService auditService;
    private final SecureRandom random = new SecureRandom();

    /** FR: user updates their own contact number before SMS confirmation is available to them. */
    @Transactional
    public void updatePhoneNumber(Authentication authentication, String phoneNumber) {
        User user = currentUser(authentication);
        user.setPhoneNumber(phoneNumber);
        userRepository.save(user);
        auditService.record("User", user.getId().toString(), "UPDATE_PHONE", "Phone number updated");
    }

    /**
     * Step 1: verify the caller actually knows their current password,
     * then send a one-time code to their phone. The new password is
     * hashed and stashed in a PasswordChangeRequest row - it does NOT
     * take effect until confirm() succeeds.
     */
    @Transactional
    public InitiateChangeResponse initiate(Authentication authentication, InitiateChangeRequest request) {
        User user = currentUser(authentication);

        // Rate-limited per account (not per IP) - keyed on email, since
        // this is a per-user SMS cost/abuse concern rather than a
        // network-level one. In-memory only - see RateLimiter javadoc
        // for the single-instance caveat.
        if (!rateLimiter.tryConsume("password-change-initiate:" + user.getEmail(), MAX_INITIATE_ATTEMPTS_PER_HOUR, Duration.ofHours(1))) {
            throw new TooManyRequestsException("Too many password change requests. Please wait before trying again.");
        }

        if (!passwordEncoder.matches(request.currentPassword(), user.getPasswordHash())) {
            auditService.record("User", user.getId().toString(), "PASSWORD_CHANGE_FAILED", "Current password did not match");
            throw new BadCredentialsException("Current password is incorrect");
        }
        if (user.getPhoneNumber() == null || user.getPhoneNumber().isBlank()) {
            throw new IllegalArgumentException(
                    "No phone number on file. Add one first via PATCH /api/users/me/phone before changing your password.");
        }

        String otp = generateOtp();
        PasswordChangeRequest pending = PasswordChangeRequest.builder()
                .user(user)
                .pendingPasswordHash(passwordEncoder.encode(request.newPassword()))
                .otpHash(passwordEncoder.encode(otp))
                .expiresAt(Instant.now().plus(OTP_TTL_MINUTES, ChronoUnit.MINUTES))
                .build();
        requestRepository.save(pending);

        smsSender.send(user.getPhoneNumber(),
                "Your EEMS verification code is " + otp + ". It expires in " + OTP_TTL_MINUTES + " minutes.");

        auditService.record("User", user.getId().toString(), "PASSWORD_CHANGE_INITIATED", "OTP sent for password change confirmation");

        return new InitiateChangeResponse(
                "A verification code has been sent to your phone.",
                maskPhoneNumber(user.getPhoneNumber()));
    }

    /**
     * Step 2: verify the OTP and, if it matches, copy the pending hash
     * onto the user's real password field. Single-use, time-limited,
     * and attempt-limited to slow down brute-forcing a 6-digit code.
     */
    @Transactional
    public void confirm(Authentication authentication, ConfirmChangeRequest request) {
        User user = currentUser(authentication);

        PasswordChangeRequest pending = requestRepository
                .findFirstByUserIdAndConsumedFalseOrderByCreatedAtDesc(user.getId())
                .orElseThrow(() -> new IllegalArgumentException("No pending password change request found. Start again."));

        if (pending.getExpiresAt().isBefore(Instant.now())) {
            pending.setConsumed(true);
            requestRepository.save(pending);
            throw new IllegalArgumentException("This verification code has expired. Please request a new one.");
        }
        if (pending.getAttemptCount() >= MAX_OTP_ATTEMPTS) {
            pending.setConsumed(true);
            requestRepository.save(pending);
            auditService.record("User", user.getId().toString(), "PASSWORD_CHANGE_LOCKED", "Too many incorrect OTP attempts");
            throw new ForbiddenOperationException("Too many incorrect attempts. Please request a new code.");
        }

        if (!passwordEncoder.matches(request.otpCode(), pending.getOtpHash())) {
            pending.setAttemptCount(pending.getAttemptCount() + 1);
            requestRepository.save(pending);
            throw new IllegalArgumentException("Incorrect verification code.");
        }

        user.setPasswordHash(pending.getPendingPasswordHash());
        user.setMustChangePassword(false);
        userRepository.save(user);

        pending.setConsumed(true);
        requestRepository.save(pending);

        auditService.record("User", user.getId().toString(), "PASSWORD_CHANGED", "Password changed after SMS confirmation");
    }

    private User currentUser(Authentication authentication) {
        return userRepository.findByEmail(authentication.getName())
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
    }

    private String generateOtp() {
        int code = random.nextInt(1_000_000); // 0 .. 999999
        return String.format("%0" + OTP_LENGTH + "d", code);
    }

    private String maskPhoneNumber(String phoneNumber) {
        if (phoneNumber.length() <= 4) {
            return "****";
        }
        return "*".repeat(phoneNumber.length() - 4) + phoneNumber.substring(phoneNumber.length() - 4);
    }
}
