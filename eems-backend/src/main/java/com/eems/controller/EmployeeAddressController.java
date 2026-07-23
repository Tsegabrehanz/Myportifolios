package com.eems.controller;

import com.eems.dto.EmployeeAddressDtos.AddressResponse;
import com.eems.dto.EmployeeAddressDtos.UpsertAddressRequest;
import com.eems.service.EmployeeAddressService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/employees/{employeeId}/address")
@RequiredArgsConstructor
public class EmployeeAddressController {

    private final EmployeeAddressService addressService;

    @GetMapping
    public AddressResponse get(@PathVariable Long employeeId, Authentication authentication) {
        return addressService.get(employeeId, authentication);
    }

    @PutMapping
    public AddressResponse upsert(@PathVariable Long employeeId, @RequestBody UpsertAddressRequest request, Authentication authentication) {
        return addressService.upsert(employeeId, request, authentication);
    }
}
