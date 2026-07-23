package com.eems.entity;

/**
 * System roles. Spring Security expects the "ROLE_" prefix on the
 * authority string, which is added in CustomUserDetailsService.
 */
public enum Role {
    SUPER_ADMIN,
    HR_ADMIN,
    MANAGER,
    EMPLOYEE,
    AUDITOR
}
