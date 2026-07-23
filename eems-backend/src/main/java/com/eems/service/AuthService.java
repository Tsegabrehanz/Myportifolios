package com.eems.service;

import com.eems.audit.AuditService;
import com.eems.dto.AuthDtos.AuthResponse;
import com.eems.dto.AuthDtos.LoginRequest;
import com.eems.dto.AuthDtos.RefreshRequest;
import com.eems.entity.Employee;
import com.eems.entity.User;
import com.eems.exception.ResourceNotFoundException;
import com.eems.repository.EmployeeRepository;
import com.eems.repository.UserRepository;
import com.eems.security.JwtService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final AuthenticationManager authenticationManager;
    private final UserRepository userRepository;
    private final EmployeeRepository employeeRepository;
    private final JwtService jwtService;
    private final AuditService auditService;

    public AuthResponse login(LoginRequest request) {
        try {
            Authentication authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(request.email(), request.password())
            );
            UserDetails userDetails = (UserDetails) authentication.getPrincipal();

            User user = userRepository.findByEmail(request.email())
                    .orElseThrow(() -> new ResourceNotFoundException("User not found"));

            Long employeeId = employeeRepository.findByUserId(user.getId())
                    .map(Employee::getId)
                    .orElse(null);

            String accessToken = jwtService.generateAccessToken(userDetails, employeeId);
            String refreshToken = jwtService.generateRefreshToken(userDetails);

            auditService.record("User", user.getId().toString(), "LOGIN", "Successful login");

            return new AuthResponse(accessToken, refreshToken, user.getEmail(), user.getRole().name(), employeeId, user.isMustChangePassword());
        } catch (BadCredentialsException ex) {
            auditService.record("User", request.email(), "LOGIN_FAILED", "Bad credentials");
            throw ex;
        }
    }

    public AuthResponse refresh(RefreshRequest request) {
        String email = jwtService.extractUsername(request.refreshToken());
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        var userDetails = org.springframework.security.core.userdetails.User.builder()
                .username(user.getEmail())
                .password(user.getPasswordHash())
                .authorities("ROLE_" + user.getRole().name())
                .build();

        if (!jwtService.isTokenValid(request.refreshToken(), userDetails)) {
            throw new BadCredentialsException("Invalid or expired refresh token");
        }

        Long employeeId = employeeRepository.findByUserId(user.getId())
                .map(Employee::getId)
                .orElse(null);

        String newAccessToken = jwtService.generateAccessToken(userDetails, employeeId);
        return new AuthResponse(newAccessToken, request.refreshToken(), user.getEmail(), user.getRole().name(), employeeId, user.isMustChangePassword());
    }
}
