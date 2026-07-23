package com.eems.controller;

import com.eems.dto.AppSettingsDtos.EmployeeIdFormatResponse;
import com.eems.dto.AppSettingsDtos.UpdateEmployeeIdFormatRequest;
import com.eems.service.AppSettingsService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/app-settings/employee-id-format")
@RequiredArgsConstructor
public class EmployeeIdFormatController {

    private final AppSettingsService appSettingsService;

    /** Any authenticated user - not sensitive, just the current prefix/suffix/next sequence. */
    @GetMapping
    public EmployeeIdFormatResponse get() {
        return appSettingsService.employeeIdFormat();
    }

    /**
     * SUPER_ADMIN/HR_ADMIN only (see SecurityConfig). Only affects
     * employees created after this call - existing employeeCodes are
     * never regenerated or renamed.
     */
    @PutMapping
    public EmployeeIdFormatResponse update(@Valid @RequestBody UpdateEmployeeIdFormatRequest request) {
        return appSettingsService.updateEmployeeIdFormat(request.prefix(), request.suffix());
    }
}
