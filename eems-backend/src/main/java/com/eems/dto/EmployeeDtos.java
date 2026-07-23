package com.eems.dto;

import com.eems.entity.EmployeeStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

public class EmployeeDtos {

    /**
     * Default read projection. nationalId is intentionally omitted here -
     * NFR-1.3 requires PII to be masked/excluded from responses by default.
     * A separate, permission-gated endpoint should be used if the raw
     * value is ever genuinely needed (e.g. payroll export).
     */
    public record EmployeeResponse(
            Long id,
            String employeeCode,
            String firstName,
            String lastName,
            String email,
            Long positionId,
            String positionTitle,
            Long departmentId,
            String departmentName,
            Long managerId,
            String managerName,
            LocalDate hireDate,
            LocalDate exitDate,
            EmployeeStatus status
    ) {}

    public record CreateEmployeeRequest(
            @NotBlank String firstName,
            @NotBlank String lastName,
            String nationalId,
            Long positionId,
            Long departmentId,
            Long managerId,
            @NotNull LocalDate hireDate,
            String email, // optional - auto-generated as firstname.lastname@eems.local if blank
            String initialPassword // optional - a secure temporary password is generated server-side if blank
    ) {}

    public record UpdateEmployeeRequest(
            String firstName,
            String lastName,
            Long positionId,
            Long departmentId,
            Long managerId,
            EmployeeStatus status
    ) {}

    /**
     * Wraps the created employee with the credentials actually used, but
     * ONLY when this endpoint generated them (both null if the caller
     * supplied their own email/password). This is the one and only time
     * the plaintext temporary password is ever returned - it isn't
     * stored anywhere and can't be retrieved again after this response.
     */
    public record CreateEmployeeResponse(
            EmployeeResponse employee,
            String generatedEmail,
            String generatedTemporaryPassword
    ) {}
}
