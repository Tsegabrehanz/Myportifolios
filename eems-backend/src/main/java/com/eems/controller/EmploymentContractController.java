package com.eems.controller;

import com.eems.dto.EmploymentContractDtos.ContractResponse;
import com.eems.dto.EmploymentContractDtos.CreateContractRequest;
import com.eems.service.EmploymentContractService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/employees/{employeeId}/employment-contracts")
@RequiredArgsConstructor
public class EmploymentContractController {

    private final EmploymentContractService contractService;

    @GetMapping
    public List<ContractResponse> list(@PathVariable Long employeeId, Authentication authentication) {
        return contractService.list(employeeId, authentication);
    }

    /** HR_ADMIN/SUPER_ADMIN only in practice - see SecurityConfig's general POST /api/employees/** rule. */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ContractResponse create(@PathVariable Long employeeId, @Valid @RequestBody CreateContractRequest request, Authentication authentication) {
        return contractService.create(employeeId, request, authentication);
    }

    @DeleteMapping("/{contractId}")
    public void delete(@PathVariable Long employeeId, @PathVariable Long contractId, Authentication authentication) {
        contractService.delete(employeeId, contractId, authentication);
    }
}
