package com.eems.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * A password change that has been requested (current password already
 * verified) but not yet confirmed via the SMS one-time code. The new
 * password is stored pre-hashed - never in plaintext - and is only
 * copied onto the User once the OTP is confirmed.
 *
 * One row per in-flight request; a fresh request supersedes any earlier
 * unconsumed one for the same user (see PasswordChangeService).
 */
@Entity
@Table(name = "password_change_request")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PasswordChangeRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id")
    private User user;

    /** BCrypt hash of the new password the user wants to switch to. */
    @Column(nullable = false)
    private String pendingPasswordHash;

    /** BCrypt hash of the 6-digit OTP sent via SMS - never stored in plaintext. */
    @Column(nullable = false)
    private String otpHash;

    @Column(nullable = false)
    private Instant expiresAt;

    @Column(nullable = false)
    @Builder.Default
    private boolean consumed = false;

    @Builder.Default
    private int attemptCount = 0;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        this.createdAt = Instant.now();
    }
}
