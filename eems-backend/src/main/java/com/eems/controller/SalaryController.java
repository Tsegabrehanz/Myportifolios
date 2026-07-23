package com.eems.controller;

import com.eems.dto.SalaryDtos.CreateSalaryRequest;
import com.eems.dto.SalaryDtos.SalaryResponse;
import com.eems.service.SalaryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/employees/{employeeId}/salary")
@RequiredArgsConstructor
public class SalaryController {

    private final SalaryService salaryService;

    @GetMapping
    public List<SalaryResponse> list(@PathVariable Long employeeId, Authentication authentication) {
        return salaryService.list(employeeId, authentication);
    }

    /** HR_ADMIN/SUPER_ADMIN only - see SecurityConfig, this is stricter than the general PUT /api/employees/** rule. */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public SalaryResponse create(@PathVariable Long employeeId, @Valid @RequestBody CreateSalaryRequest request, Authentication authentication) {
        return salaryService.create(employeeId, request, authentication);
    }
}
