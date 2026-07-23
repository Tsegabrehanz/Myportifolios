package com.eems.controller;

import com.eems.dto.EmployeeDtos.CreateEmployeeRequest;
import com.eems.dto.EmployeeDtos.CreateEmployeeResponse;
import com.eems.dto.EmployeeDtos.EmployeeResponse;
import com.eems.dto.EmployeeDtos.UpdateEmployeeRequest;
import com.eems.dto.EmployeeImportDtos.ImportSummaryResponse;
import com.eems.report.EmployeeCsvExporter;
import com.eems.service.EmployeeImportService;
import com.eems.service.EmployeeService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/employees")
@RequiredArgsConstructor
public class EmployeeController {

    private final EmployeeService employeeService;
    private final EmployeeImportService employeeImportService;
    private final EmployeeCsvExporter employeeCsvExporter;

    /**
     * Returns the created employee plus any server-generated email/temp
     * password - this is the only response that will ever contain the
     * plaintext temporary password, so the frontend must show it to the
     * caller immediately; it cannot be retrieved again afterward.
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CreateEmployeeResponse create(@Valid @RequestBody CreateEmployeeRequest request) {
        return employeeService.create(request);
    }

    @GetMapping
    public List<EmployeeResponse> list(Authentication authentication) {
        return employeeService.listVisibleTo(authentication);
    }

    @GetMapping("/{id}")
    public EmployeeResponse getById(@PathVariable Long id, Authentication authentication) {
        return employeeService.getById(id, authentication);
    }

    @PutMapping("/{id}")
    public EmployeeResponse update(@PathVariable Long id, @RequestBody UpdateEmployeeRequest request, Authentication authentication) {
        return employeeService.update(id, request, authentication);
    }

    @PostMapping("/{id}/offboard")
    public void offboard(@PathVariable Long id) {
        employeeService.offboard(id);
    }

    /**
     * Bulk import from CSV or XLSX (FR-9.3). See EmployeeImportService
     * javadoc for the expected column layout. Restricted to HR/Admin via
     * the same rule that covers POST /api/employees/** in SecurityConfig.
     */
    @PostMapping(value = "/import", consumes = "multipart/form-data")
    public ImportSummaryResponse importEmployees(@RequestParam("file") MultipartFile file) {
        return employeeImportService.importFile(file);
    }

    /**
     * CSV export: id, firstName, lastName, positionTitle, departmentName,
     * managerName, hireDate, exitDate, status - deliberately the same
     * columns EmployeeResponse already exposes, no nationalId or other
     * sensitive fields. Uses the same visibility scoping as GET
     * /api/employees - a MANAGER exports their own reports, a plain
     * EMPLOYEE exports just their own row.
     */
    @GetMapping("/export.csv")
    public ResponseEntity<byte[]> exportCsv(Authentication authentication) {
        byte[] bytes = employeeCsvExporter.export(employeeService.listVisibleTo(authentication));
        String filename = "eems-employees-" + LocalDate.now() + ".csv";
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(filename).build().toString())
                .contentType(MediaType.parseMediaType("text/csv"))
                .body(bytes);
    }
}
