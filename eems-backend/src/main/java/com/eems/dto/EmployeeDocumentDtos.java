package com.eems.dto;

import jakarta.validation.constraints.NotBlank;

import java.time.Instant;
import java.time.LocalDate;

public class EmployeeDocumentDtos {

    public record DocumentResponse(
            Long id,
            Long employeeId,
            String documentType,
            String fileName,
            String fileUrl,
            String contentType,
            Long fileSizeBytes,
            boolean downloadable, // true when this was a real upload (storedFileName is set) - the frontend shows a Download button only then
            LocalDate expiryDate,
            Instant uploadedAt
    ) {}

    /**
     * Metadata-only path: no bytes are uploaded here, just a record
     * pointing at fileUrl (wherever the real file actually lives). For
     * an actual upload, use POST .../documents/upload (multipart) instead.
     */
    public record CreateDocumentRequest(
            @NotBlank String documentType,
            @NotBlank String fileName,
            String fileUrl,
            LocalDate expiryDate
    ) {}
}
