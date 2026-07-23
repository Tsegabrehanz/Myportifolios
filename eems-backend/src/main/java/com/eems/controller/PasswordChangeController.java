package com.eems.controller;

import com.eems.dto.PasswordChangeDtos.ConfirmChangeRequest;
import com.eems.dto.PasswordChangeDtos.InitiateChangeRequest;
import com.eems.dto.PasswordChangeDtos.InitiateChangeResponse;
import com.eems.dto.PasswordChangeDtos.UpdatePhoneNumberRequest;
import com.eems.service.PasswordChangeService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
public class PasswordChangeController {

    private final PasswordChangeService passwordChangeService;

    /** Any authenticated user manages their own phone number - required before SMS-confirmed password changes work. */
    @PatchMapping("/api/users/me/phone")
    public void updatePhoneNumber(@Valid @RequestBody UpdatePhoneNumberRequest request, Authentication authentication) {
        passwordChangeService.updatePhoneNumber(authentication, request.phoneNumber());
    }

    @PostMapping("/api/auth/change-password/initiate")
    public InitiateChangeResponse initiate(@Valid @RequestBody InitiateChangeRequest request, Authentication authentication) {
        return passwordChangeService.initiate(authentication, request);
    }

    @PostMapping("/api/auth/change-password/confirm")
    public void confirm(@Valid @RequestBody ConfirmChangeRequest request, Authentication authentication) {
        passwordChangeService.confirm(authentication, request);
    }
}
