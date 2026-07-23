package com.eems.service;

import com.eems.audit.AuditService;
import com.eems.dto.EmployeeDocumentDtos.CreateDocumentRequest;
import com.eems.dto.EmployeeDocumentDtos.DocumentResponse;
import com.eems.entity.Employee;
import com.eems.entity.EmployeeDocument;
import com.eems.exception.ForbiddenOperationException;
import com.eems.exception.ResourceNotFoundException;
import com.eems.repository.EmployeeDocumentRepository;
import com.eems.repository.EmployeeRepository;
import com.eems.storage.FileStorageService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.util.List;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class EmployeeDocumentService {

    private final EmployeeDocumentRepository documentRepository;
    private final EmployeeRepository employeeRepository;
    private final FileStorageService fileStorageService;
    private final AuditService auditService;

    public List<DocumentResponse> list(Long employeeId, Authentication authentication) {
        Employee target = employeeRepository.findById(employeeId)
                .orElseThrow(() -> new ResourceNotFoundException("Employee not found: " + employeeId));
        enforceVisibility(target, authentication);

        return documentRepository.findByEmployeeIdOrderByUploadedAtDesc(employeeId).stream().map(this::toResponse).toList();
    }

    /** Metadata-only record - no bytes stored, just a fileUrl pointing somewhere external. */
    @Transactional
    public DocumentResponse create(Long employeeId, CreateDocumentRequest request, Authentication authentication) {
        Employee target = employeeRepository.findById(employeeId)
                .orElseThrow(() -> new ResourceNotFoundException("Employee not found: " + employeeId));
        enforceVisibility(target, authentication);

        EmployeeDocument document = EmployeeDocument.builder()
                .employee(target)
                .documentType(request.documentType())
                .fileName(request.fileName())
                .fileUrl(request.fileUrl())
                .expiryDate(request.expiryDate())
                .build();
        document = documentRepository.save(document);

        auditService.record("EmployeeDocument", document.getId().toString(), "CREATE",
                "Document record added for employee " + employeeId + ": " + request.documentType());
        return toResponse(document);
    }

    /**
     * Real upload - the file's actual bytes are written to disk via
     * FileStorageService, and this record's storedFileName/contentType/
     * fileSizeBytes are populated so GET .../download can stream it back.
     */
    @Transactional
    public DocumentResponse upload(Long employeeId, String documentType, MultipartFile file, Authentication authentication) {
        Employee target = employeeRepository.findById(employeeId)
                .orElseThrow(() -> new ResourceNotFoundException("Employee not found: " + employeeId));
        enforceVisibility(target, authentication);

        if (file.isEmpty()) {
            throw new IllegalArgumentException("Uploaded file is empty");
        }

        String storedFileName = fileStorageService.store(employeeId, file);

        EmployeeDocument document = EmployeeDocument.builder()
                .employee(target)
                .documentType(documentType)
                .fileName(Objects.requireNonNullElse(file.getOriginalFilename(), "file"))
                .storedFileName(storedFileName)
                .contentType(file.getContentType())
                .fileSizeBytes(file.getSize())
                .build();
        document = documentRepository.save(document);

        auditService.record("EmployeeDocument", document.getId().toString(), "UPLOAD",
                "File uploaded for employee " + employeeId + ": " + documentType + " (" + file.getSize() + " bytes)");
        return toResponse(document);
    }

    /** @return the file's bytes as a stream, plus the metadata the controller needs to set response headers correctly */
    public DownloadResult download(Long employeeId, Long documentId, Authentication authentication) {
        Employee target = employeeRepository.findById(employeeId)
                .orElseThrow(() -> new ResourceNotFoundException("Employee not found: " + employeeId));
        enforceVisibility(target, authentication);

        EmployeeDocument document = documentRepository.findById(documentId)
                .orElseThrow(() -> new ResourceNotFoundException("Document not found: " + documentId));
        if (!document.getEmployee().getId().equals(employeeId)) {
            throw new ForbiddenOperationException("This document does not belong to that employee");
        }
        if (document.getStoredFileName() == null) {
            throw new IllegalArgumentException("This document record has no uploaded file to download (it's metadata-only, with a fileUrl instead)");
        }

        InputStream stream = fileStorageService.load(employeeId, document.getStoredFileName());
        auditService.record("EmployeeDocument", documentId.toString(), "DOWNLOAD", "File downloaded for employee " + employeeId);
        return new DownloadResult(stream, document.getFileName(), document.getContentType());
    }

    @Transactional
    public void delete(Long employeeId, Long documentId, Authentication authentication) {
        Employee target = employeeRepository.findById(employeeId)
                .orElseThrow(() -> new ResourceNotFoundException("Employee not found: " + employeeId));
        enforceVisibility(target, authentication);

        EmployeeDocument document = documentRepository.findById(documentId)
                .orElseThrow(() -> new ResourceNotFoundException("Document not found: " + documentId));
        if (!document.getEmployee().getId().equals(employeeId)) {
            throw new ForbiddenOperationException("This document does not belong to that employee");
        }

        if (document.getStoredFileName() != null) {
            fileStorageService.delete(employeeId, document.getStoredFileName());
        }
        documentRepository.delete(document);
        auditService.record("EmployeeDocument", documentId.toString(), "DELETE", "Document record removed for employee " + employeeId);
    }

    /** Same rule as EmployeeService.enforceVisibility - self, direct manager, or HR/Admin/Auditor. */
    private void enforceVisibility(Employee target, Authentication authentication) {
        String role = topRole(authentication);
        if (role.equals("ROLE_SUPER_ADMIN") || role.equals("ROLE_HR_ADMIN") || role.equals("ROLE_AUDITOR")) {
            return;
        }

        Employee requester = employeeRepository.findByUserEmail(authentication.getName())
                .orElseThrow(() -> new ResourceNotFoundException("Employee profile not found for current user"));

        boolean isSelf = requester.getId().equals(target.getId());
        boolean isDirectManager = target.getManager() != null && target.getManager().getId().equals(requester.getId());

        if (role.equals("ROLE_MANAGER") && (isSelf || isDirectManager)) {
            return;
        }
        if (role.equals("ROLE_EMPLOYEE") && isSelf) {
            return;
        }
        throw new ForbiddenOperationException("You do not have permission to access this employee's documents");
    }

    private String topRole(Authentication authentication) {
        return authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .findFirst()
                .orElse("ROLE_EMPLOYEE");
    }

    private DocumentResponse toResponse(EmployeeDocument d) {
        return new DocumentResponse(
                d.getId(), d.getEmployee().getId(), d.getDocumentType(), d.getFileName(), d.getFileUrl(),
                d.getContentType(), d.getFileSizeBytes(), d.getStoredFileName() != null, d.getExpiryDate(), d.getUploadedAt()
        );
    }

    public record DownloadResult(InputStream stream, String fileName, String contentType) {}
}
