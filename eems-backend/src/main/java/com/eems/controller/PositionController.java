package com.eems.controller;

import com.eems.dto.PositionDtos.CreatePositionRequest;
import com.eems.dto.PositionDtos.PositionResponse;
import com.eems.dto.PositionDtos.UpdatePositionRequest;
import com.eems.dto.PositionImportDtos.ImportSummaryResponse;
import com.eems.report.PositionCsvExporter;
import com.eems.service.PositionImportService;
import com.eems.service.PositionService;
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
@RequestMapping("/api/positions")
@RequiredArgsConstructor
public class PositionController {

    private final PositionService positionService;
    private final PositionImportService positionImportService;
    private final PositionCsvExporter positionCsvExporter;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public PositionResponse create(@Valid @RequestBody CreatePositionRequest request) {
        return positionService.create(request);
    }

    @GetMapping
    public List<PositionResponse> list() {
        return positionService.listAll();
    }

    @GetMapping("/{id}")
    public PositionResponse getById(@PathVariable Long id) {
        return positionService.getById(id);
    }

    @PutMapping("/{id}")
    public PositionResponse update(@PathVariable Long id, @RequestBody UpdatePositionRequest request) {
        return positionService.update(id, request);
    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable Long id) {
        positionService.delete(id);
    }

    /** Bulk CSV import (title, grade, salaryBand, departmentName) - see PositionImportService javadoc. */
    @PostMapping(value = "/import", consumes = "multipart/form-data")
    public ImportSummaryResponse importPositions(@RequestParam("file") MultipartFile file) {
        return positionImportService.importFile(file);
    }

    /** CSV export: id, title, grade, salaryBand, departmentName for every position. */
    @GetMapping("/export.csv")
    public ResponseEntity<byte[]> exportCsv() {
        byte[] bytes = positionCsvExporter.export(positionService.listAll());
        String filename = "eems-positions-" + LocalDate.now() + ".csv";
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(filename).build().toString())
                .contentType(MediaType.parseMediaType("text/csv"))
                .body(bytes);
    }
}
