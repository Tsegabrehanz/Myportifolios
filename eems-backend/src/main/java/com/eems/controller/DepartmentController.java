package com.eems.controller;

import com.eems.dto.DepartmentDtos.CreateDepartmentRequest;
import com.eems.dto.DepartmentDtos.DepartmentResponse;
import com.eems.dto.DepartmentImportDtos.ImportSummaryResponse;
import com.eems.report.DepartmentCsvExporter;
import com.eems.service.DepartmentImportService;
import com.eems.service.DepartmentService;
import com.eems.service.PowerBiService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/departments")
@RequiredArgsConstructor
public class DepartmentController {

    private final DepartmentService departmentService;
    private final DepartmentImportService departmentImportService;
    private final DepartmentCsvExporter departmentCsvExporter;
    private final PowerBiService powerBiService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public DepartmentResponse create(@Valid @RequestBody CreateDepartmentRequest request) {
        return departmentService.create(request);
    }

    @GetMapping
    public List<DepartmentResponse> list() {
        return departmentService.listAll();
    }

    @GetMapping("/{id}")
    public DepartmentResponse getById(@PathVariable Long id) {
        return departmentService.getById(id);
    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable Long id) {
        departmentService.delete(id);
    }

    /** Bulk CSV import (name, location, parentDepartmentName) - see DepartmentImportService javadoc. */
    @PostMapping(value = "/import", consumes = "multipart/form-data")
    public ImportSummaryResponse importDepartments(@RequestParam("file") MultipartFile file) {
        return departmentImportService.importFile(file);
    }

    /** CSV export: id, name, location, active/total headcount per department. */
    @GetMapping("/export.csv")
    public ResponseEntity<byte[]> exportCsv() {
        byte[] bytes = departmentCsvExporter.export(powerBiService.departmentRows());
        String filename = "eems-departments-" + LocalDate.now() + ".csv";
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(filename).build().toString())
                .contentType(MediaType.parseMediaType("text/csv"))
                .body(bytes);
    }
}
