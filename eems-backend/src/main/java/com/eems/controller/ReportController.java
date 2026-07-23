package com.eems.controller;

import com.eems.dto.ReportDtos.HrSummaryReport;
import com.eems.report.HrSummaryExcelExporter;
import com.eems.report.HrSummaryPdfExporter;
import com.eems.service.ReportService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/reports")
@RequiredArgsConstructor
public class ReportController {

    private final ReportService reportService;
    private final HrSummaryExcelExporter excelExporter;
    private final HrSummaryPdfExporter pdfExporter;

    @GetMapping("/hr-summary")
    public HrSummaryReport hrSummary() {
        return reportService.generateHrSummary();
    }

    @GetMapping(value = "/hr-summary/export.xlsx")
    public ResponseEntity<byte[]> exportExcel() {
        byte[] bytes = excelExporter.export(reportService.generateHrSummary());
        String filename = "eems-hr-summary-" + LocalDate.now() + ".xlsx";
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(filename).build().toString())
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(bytes);
    }

    @GetMapping(value = "/hr-summary/export.pdf")
    public ResponseEntity<byte[]> exportPdf() {
        byte[] bytes = pdfExporter.export(reportService.generateHrSummary());
        String filename = "eems-hr-summary-" + LocalDate.now() + ".pdf";
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(filename).build().toString())
                .contentType(MediaType.APPLICATION_PDF)
                .body(bytes);
    }
}
