package com.eems.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "app_user", uniqueConstraints = @UniqueConstraint(columnNames = "email"))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String email;

    /** BCrypt hash - never store or return plaintext. */
    @Column(nullable = false)
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Role role;

    /**
     * E.164 format (e.g. +491701234567) - required before a user can use
     * the SMS-confirmed password change flow. Nullable because not every
     * seeded/legacy account will have one set immediately.
     */
    @Column(name = "phone_number")
    private String phoneNumber;

    @Column(nullable = false)
    @Builder.Default
    private boolean enabled = true;

    /**
     * True when the current password is a server-generated temporary one
     * (new hire onboarding, or an admin-triggered reset) that hasn't been
     * changed yet. The frontend redirects straight to the change-password
     * flow on login when this is true; PasswordChangeService clears it
     * once the user successfully sets their own password.
     */
    @Column(nullable = false)
    @Builder.Default
    private boolean mustChangePassword = false;

    @Column(nullable = false)
    @Builder.Default
    private boolean accountNonLocked = true;

    private Integer failedLoginAttempts;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        this.createdAt = Instant.now();
        if (this.failedLoginAttempts == null) {
            this.failedLoginAttempts = 0;
        }
    }
}
