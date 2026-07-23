package com.eems.service;

import com.eems.audit.AuditService;
import com.eems.dto.EmployeePhotoDtos.PhotoStatusResponse;
import com.eems.entity.Employee;
import com.eems.exception.ForbiddenOperationException;
import com.eems.exception.ResourceNotFoundException;
import com.eems.repository.EmployeeRepository;
import com.eems.storage.FileStorageService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;

/**
 * Profile photo upload/download - a real upload via FileStorageService
 * (same disk-backed storage as EmployeeDocument's real-upload path),
 * with the reference kept directly on Employee rather than a separate
 * table, since it's a genuine 1:1 ("one current photo") rather than a
 * history like documents.
 */
@Service
@RequiredArgsConstructor
public class EmployeePhotoService {

    private final EmployeeRepository employeeRepository;
    private final FileStorageService fileStorageService;
    private final AuditService auditService;

    public PhotoStatusResponse status(Long employeeId, Authentication authentication) {
        Employee employee = findAndCheckVisibility(employeeId, authentication);
        return new PhotoStatusResponse(employee.getId(), employee.getPhotoStoredFileName() != null);
    }

    @Transactional
    public PhotoStatusResponse upload(Long employeeId, MultipartFile file, Authentication authentication) {
        Employee employee = findAndCheckVisibility(employeeId, authentication);

        if (file.isEmpty()) {
            throw new IllegalArgumentException("Uploaded file is empty");
        }
        String contentType = file.getContentType();
        if (contentType == null || !contentType.startsWith("image/")) {
            throw new IllegalArgumentException("Profile photo must be an image file");
        }

        // Replace, don't accumulate - a profile photo is "the current one", not a history.
        if (employee.getPhotoStoredFileName() != null) {
            fileStorageService.delete(employeeId, employee.getPhotoStoredFileName());
        }

        String storedFileName = fileStorageService.store(employeeId, file);
        employee.setPhotoStoredFileName(storedFileName);
        employee.setPhotoContentType(contentType);
        employeeRepository.save(employee);

        auditService.record("Employee", employeeId.toString(), "PHOTO_UPLOADED", "Profile photo updated for employee " + employeeId);
        return new PhotoStatusResponse(employeeId, true);
    }

    public record DownloadResult(InputStream stream, String contentType) {}

    public DownloadResult download(Long employeeId, Authentication authentication) {
        Employee employee = findAndCheckVisibility(employeeId, authentication);
        if (employee.getPhotoStoredFileName() == null) {
            throw new ResourceNotFoundException("No profile photo on file for employee " + employeeId);
        }
        InputStream stream = fileStorageService.load(employeeId, employee.getPhotoStoredFileName());
        return new DownloadResult(stream, employee.getPhotoContentType());
    }

    @Transactional
    public void delete(Long employeeId, Authentication authentication) {
        Employee employee = findAndCheckVisibility(employeeId, authentication);
        if (employee.getPhotoStoredFileName() != null) {
            fileStorageService.delete(employeeId, employee.getPhotoStoredFileName());
            employee.setPhotoStoredFileName(null);
            employee.setPhotoContentType(null);
            employeeRepository.save(employee);
            auditService.record("Employee", employeeId.toString(), "PHOTO_REMOVED", "Profile photo removed for employee " + employeeId);
        }
    }

    private Employee findAndCheckVisibility(Long employeeId, Authentication authentication) {
        Employee employee = employeeRepository.findById(employeeId)
                .orElseThrow(() -> new ResourceNotFoundException("Employee not found: " + employeeId));
        enforceVisibility(employee, authentication);
        return employee;
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
        throw new ForbiddenOperationException("You do not have permission to access this employee's photo");
    }

    private String topRole(Authentication authentication) {
        return authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .findFirst()
                .orElse("ROLE_EMPLOYEE");
    }
}
