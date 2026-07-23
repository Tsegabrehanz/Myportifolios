package com.eems.controller;

import com.eems.dto.OfferLetterDtos.OfferLetterData;
import com.eems.report.OfferLetterPdfExporter;
import com.eems.service.OfferLetterService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/employees/{employeeId}/offer-letter")
@RequiredArgsConstructor
public class OfferLetterController {

    private final OfferLetterService offerLetterService;
    private final OfferLetterPdfExporter offerLetterPdfExporter;

    /**
     * Generates a draft offer letter PDF from this employee's current
     * position/department/most-recent-salary data. Same access as
     * salary itself - self or HR_ADMIN/SUPER_ADMIN only, enforced in
     * OfferLetterService.
     */
    @GetMapping(value = "/export.pdf")
    public ResponseEntity<byte[]> exportPdf(@PathVariable Long employeeId, Authentication authentication) {
        OfferLetterData data = offerLetterService.buildData(employeeId, authentication);
        byte[] bytes = offerLetterPdfExporter.export(data);
        String filename = "offer-letter-" + employeeId + ".pdf";
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(filename).build().toString())
                .contentType(MediaType.APPLICATION_PDF)
                .body(bytes);
    }
}
