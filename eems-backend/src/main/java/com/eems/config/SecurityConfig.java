package com.eems.config;

import com.eems.security.CustomUserDetailsService;
import com.eems.security.JwtAuthFilter;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity // enables @PreAuthorize on controller/service methods
@RequiredArgsConstructor
public class SecurityConfig {

    private final CustomUserDetailsService userDetailsService;
    private final JwtAuthFilter jwtAuthFilter;

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }

    @Bean
    public AuthenticationProvider authenticationProvider() {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider();
        provider.setUserDetailsService(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder());
        return provider;
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable()) // stateless JWT API; CSRF not applicable
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
            .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            // Without this, Spring Security has no httpBasic()/formLogin()
            // registered, so it falls back to Http403ForbiddenEntryPoint -
            // which returns 403 even for a MISSING or EXPIRED token, not just
            // for a real role mismatch. That breaks the frontend's "log out
            // on 401" logic (auth.interceptor.ts only listens for 401), so an
            // expired token left the user stuck with a stale "signed in as..."
            // UI and permanently-failing requests instead of being redirected
            // to log in again. This restores the normal convention: 401 =
            // "you're not authenticated" (missing/invalid/expired token), 403
            // = "you're authenticated, but not allowed to do this."
            .exceptionHandling(handling -> handling
                .authenticationEntryPoint(jsonAuthenticationEntryPoint())
                .accessDeniedHandler(jsonAccessDeniedHandler())
            )
            // H2's console UI renders itself inside an iframe internally -
            // without this, Spring Security's default X-Frame-Options: DENY
            // would make the /h2-console permitAll rule below reachable but
            // non-functional (the page loads, the frame inside it doesn't).
            // sameOrigin (not disabling frame protection outright) is enough
            // and doesn't weaken clickjacking protection for the rest of the app.
            .headers(headers -> headers.frameOptions(frame -> frame.sameOrigin()))
            .authorizeHttpRequests(auth -> auth
                // Only login/refresh are public - change-password and phone-number
                // endpoints live under /api/auth and /api/users but must NOT be
                // covered by a broad permitAll, since they require a real
                // authenticated user (see the explicit rules below and the
                // anyRequest().authenticated() catch-all).
                .requestMatchers("/api/auth/login", "/api/auth/refresh").permitAll()
                .requestMatchers("/h2-console/**").permitAll() // dev profile only

                // Company logo: reading it is public - the login page needs to
                // show it before anyone has authenticated. Uploading/removing
                // it is SUPER_ADMIN only, listed before the public GET rules
                // don't apply to POST/DELETE anyway (different HTTP verb), but
                // being explicit here rather than relying on that.
                .requestMatchers("GET", "/api/app-settings/logo", "/api/app-settings/logo/status").permitAll()
                .requestMatchers("POST", "/api/app-settings/logo").hasRole("SUPER_ADMIN")
                .requestMatchers("DELETE", "/api/app-settings/logo").hasRole("SUPER_ADMIN")

                // Employee ID format - GET is fine for anyone authenticated
                // (falls through to anyRequest().authenticated() below), only
                // changing it is HR/Admin.
                .requestMatchers("PUT", "/api/app-settings/employee-id-format").hasAnyRole("SUPER_ADMIN", "HR_ADMIN")

                // Role grant/revoke and account enable/disable is SUPER_ADMIN only -
                // even HR_ADMIN (who can manage employee records) shouldn't be able
                // to grant SUPER_ADMIN to themselves or anyone else. This must be
                // listed before the broader /api/admin/** rule below since Spring
                // Security uses first-match-wins.
                .requestMatchers("/api/admin/users/**").hasRole("SUPER_ADMIN")

                // Admin-only configuration & user management
                .requestMatchers("/api/admin/**").hasAnyRole("SUPER_ADMIN", "HR_ADMIN")

                // Department & employee management restricted to HR/Admin for writes;
                // fine-grained ownership checks (e.g. manager sees own reports) are
                // enforced additionally at the service layer.
                .requestMatchers("POST", "/api/departments/**").hasAnyRole("SUPER_ADMIN", "HR_ADMIN")
                .requestMatchers("PUT", "/api/departments/**").hasAnyRole("SUPER_ADMIN", "HR_ADMIN")
                .requestMatchers("DELETE", "/api/departments/**").hasAnyRole("SUPER_ADMIN", "HR_ADMIN")
                .requestMatchers("POST", "/api/positions/**").hasAnyRole("SUPER_ADMIN", "HR_ADMIN")
                .requestMatchers("PUT", "/api/positions/**").hasAnyRole("SUPER_ADMIN", "HR_ADMIN")
                .requestMatchers("DELETE", "/api/positions/**").hasAnyRole("SUPER_ADMIN", "HR_ADMIN")

                // Job postings: read is visibility-scoped in the service
                // (falls through to anyRequest().authenticated() below),
                // write is HR/Admin only - browsing internal openings
                // shouldn't let an EMPLOYEE create or edit one.
                .requestMatchers("POST", "/api/job-postings/**").hasAnyRole("SUPER_ADMIN", "HR_ADMIN")
                .requestMatchers("PUT", "/api/job-postings/**").hasAnyRole("SUPER_ADMIN", "HR_ADMIN")
                .requestMatchers("DELETE", "/api/job-postings/**").hasAnyRole("SUPER_ADMIN", "HR_ADMIN")

                // Sub-resources (address, emergency contacts, documents) allow
                // any authenticated user through this URL-level check - the
                // actual self/direct-manager/HR-Admin-Auditor decision is
                // enforced in each service's enforceVisibility, same rule as
                // EmployeeService. These MUST be listed before the broader
                // POST/PUT/DELETE /api/employees/** rules below (Spring
                // Security is first-match-wins), otherwise an EMPLOYEE could
                // never manage their own address/contacts/documents even
                // though the service layer explicitly allows "self".
                .requestMatchers("/api/employees/*/address/**").authenticated()
                .requestMatchers("/api/employees/*/emergency-contacts/**").authenticated()
                .requestMatchers("/api/employees/*/documents/**").authenticated()
                .requestMatchers("/api/employees/*/photo/**").authenticated()

                .requestMatchers("POST", "/api/employees/**").hasAnyRole("SUPER_ADMIN", "HR_ADMIN")
                .requestMatchers("PUT", "/api/employees/**").hasAnyRole("SUPER_ADMIN", "HR_ADMIN", "MANAGER")
                .requestMatchers("DELETE", "/api/employees/**").hasAnyRole("SUPER_ADMIN", "HR_ADMIN")

                // Leave approval restricted to managers/HR; submission open to any authenticated employee
                .requestMatchers("PATCH", "/api/leave-requests/*/decision").hasAnyRole("SUPER_ADMIN", "HR_ADMIN", "MANAGER")

                // Audit log read access restricted to auditors/admins
                .requestMatchers("/api/audit-logs/**").hasAnyRole("SUPER_ADMIN", "AUDITOR")

                // HR analytics dashboard + PDF/Excel export restricted to HR/Admin/Auditor
                .requestMatchers("/api/reports/**").hasAnyRole("SUPER_ADMIN", "HR_ADMIN", "AUDITOR")

                // Power BI data feed - same audience as the analytics dashboard
                .requestMatchers("/api/powerbi/**").hasAnyRole("SUPER_ADMIN", "HR_ADMIN", "AUDITOR")

                // Leave balance calculator: /me and /employee/{id} are visibility-
                // checked in the service (any authenticated user, scoped there) -
                // only the org-wide list and allocation management are restricted
                // here. Exact-path matchers (no /**) so they don't also catch
                // /api/leave-balances/me or /employee/{id}.
                .requestMatchers("GET", "/api/leave-balances").hasAnyRole("SUPER_ADMIN", "HR_ADMIN", "AUDITOR")
                .requestMatchers("POST", "/api/leave-balances").hasAnyRole("SUPER_ADMIN", "HR_ADMIN")
                .requestMatchers("POST", "/api/leave-balances/bulk-allocate").hasAnyRole("SUPER_ADMIN", "HR_ADMIN")
                .requestMatchers("POST", "/api/leave-balances/carry-over").hasAnyRole("SUPER_ADMIN", "HR_ADMIN")

                .anyRequest().authenticated()
            )
            .authenticationProvider(authenticationProvider())
            .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        // Angular dev server origin - replace/extend for staging & production origins.
        configuration.setAllowedOrigins(List.of("http://localhost:4200"));
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("Authorization", "Content-Type"));
        configuration.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    /**
     * 401 for "you're not authenticated at all" - missing header, malformed
     * token, expired token, invalid signature. Same JSON shape as
     * GlobalExceptionHandler's responses for consistency, since that
     * handler can't catch this - it only intercepts exceptions thrown
     * inside a controller method, and this fires earlier, in the security
     * filter chain, before the request ever reaches a controller.
     */
    @Bean
    public AuthenticationEntryPoint jsonAuthenticationEntryPoint() {
        ObjectMapper mapper = new ObjectMapper();
        return (request, response, authException) -> writeJsonError(
                response, mapper, HttpStatus.UNAUTHORIZED, "Authentication required. Please log in again.");
    }

    /** 403 for "you're authenticated, but this role can't do that" - a genuine role/ownership denial, distinct from the 401 case above. */
    @Bean
    public AccessDeniedHandler jsonAccessDeniedHandler() {
        ObjectMapper mapper = new ObjectMapper();
        return (request, response, accessDeniedException) -> writeJsonError(
                response, mapper, HttpStatus.FORBIDDEN, "You do not have permission to perform this action.");
    }

    private void writeJsonError(HttpServletResponse response, ObjectMapper mapper, HttpStatus status, String message) throws java.io.IOException {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("timestamp", Instant.now().toString());
        body.put("status", status.value());
        body.put("error", status.getReasonPhrase());
        body.put("message", message);

        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write(mapper.writeValueAsString(body));
    }
}
