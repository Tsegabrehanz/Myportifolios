package com.eems.controller;

import com.eems.dto.EmployeeDocumentDtos.CreateDocumentRequest;
import com.eems.dto.EmployeeDocumentDtos.DocumentResponse;
import com.eems.service.EmployeeDocumentService;
import com.eems.service.EmployeeDocumentService.DownloadResult;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/employees/{employeeId}/documents")
@RequiredArgsConstructor
public class EmployeeDocumentController {

    private final EmployeeDocumentService documentService;

    @GetMapping
    public List<DocumentResponse> list(@PathVariable Long employeeId, Authentication authentication) {
        return documentService.list(employeeId, authentication);
    }

    /** Metadata-only record (no file bytes) - fileUrl points wherever the file actually lives externally. */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public DocumentResponse create(@PathVariable Long employeeId, @Valid @RequestBody CreateDocumentRequest request, Authentication authentication) {
        return documentService.create(employeeId, request, authentication);
    }

    /** Real file upload - the actual bytes are stored on disk via FileStorageService. */
    @PostMapping(value = "/upload", consumes = "multipart/form-data")
    @ResponseStatus(HttpStatus.CREATED)
    public DocumentResponse upload(
            @PathVariable Long employeeId,
            @RequestParam("documentType") String documentType,
            @RequestParam("file") MultipartFile file,
            Authentication authentication) {
        return documentService.upload(employeeId, documentType, file, authentication);
    }

    /** Streams the real uploaded file back. 400s if this document record is metadata-only (no uploaded file to serve). */
    @GetMapping("/{documentId}/download")
    public ResponseEntity<InputStreamResource> download(@PathVariable Long employeeId, @PathVariable Long documentId, Authentication authentication) {
        DownloadResult result = documentService.download(employeeId, documentId, authentication);

        MediaType mediaType = result.contentType() != null
                ? MediaType.parseMediaType(result.contentType())
                : MediaType.APPLICATION_OCTET_STREAM;

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(result.fileName()).build().toString())
                .contentType(mediaType)
                .body(new InputStreamResource(result.stream()));
    }

    @DeleteMapping("/{documentId}")
    public void delete(@PathVariable Long employeeId, @PathVariable Long documentId, Authentication authentication) {
        documentService.delete(employeeId, documentId, authentication);
    }
}
