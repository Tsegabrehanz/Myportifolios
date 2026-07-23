package com.eems.service;

import com.eems.audit.AuditService;
import com.eems.dto.AppSettingsDtos.EmployeeIdFormatResponse;
import com.eems.dto.AppSettingsDtos.LogoStatusResponse;
import com.eems.entity.AppSettings;
import com.eems.exception.ResourceNotFoundException;
import com.eems.repository.AppSettingsRepository;
import com.eems.storage.FileStorageService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;

/**
 * App-wide settings - the company logo (SUPER_ADMIN only to change,
 * public to read - the login page needs it pre-auth) and the employee
 * ID format (prefix/suffix/sequence, SUPER_ADMIN/HR_ADMIN only).
 */
@Service
@RequiredArgsConstructor
public class AppSettingsService {

    private static final long SETTINGS_ID = 1L;
    private static final int SEQUENCE_PAD_WIDTH = 4;

    private final AppSettingsRepository appSettingsRepository;
    private final FileStorageService fileStorageService;
    private final AuditService auditService;

    public LogoStatusResponse logoStatus() {
        AppSettings settings = getOrCreate();
        return new LogoStatusResponse(settings.getLogoStoredFileName() != null);
    }

    public record DownloadResult(InputStream stream, String contentType) {}

    public DownloadResult downloadLogo() {
        AppSettings settings = getOrCreate();
        if (settings.getLogoStoredFileName() == null) {
            throw new ResourceNotFoundException("No custom logo set - use the default logo.svg asset instead");
        }
        InputStream stream = fileStorageService.loadAppSetting(settings.getLogoStoredFileName());
        return new DownloadResult(stream, settings.getLogoContentType());
    }

    @Transactional
    public LogoStatusResponse uploadLogo(MultipartFile file) {
        if (file.isEmpty()) {
            throw new IllegalArgumentException("Uploaded file is empty");
        }
        String contentType = file.getContentType();
        if (contentType == null || !contentType.startsWith("image/")) {
            throw new IllegalArgumentException("Logo must be an image file");
        }

        AppSettings settings = getOrCreate();
        if (settings.getLogoStoredFileName() != null) {
            fileStorageService.deleteAppSetting(settings.getLogoStoredFileName());
        }

        String storedFileName = fileStorageService.storeAppSetting(file);
        settings.setLogoStoredFileName(storedFileName);
        settings.setLogoContentType(contentType);
        appSettingsRepository.save(settings);

        auditService.record("AppSettings", "1", "LOGO_UPLOADED", "Company logo updated");
        return new LogoStatusResponse(true);
    }

    @Transactional
    public void removeLogo() {
        AppSettings settings = getOrCreate();
        if (settings.getLogoStoredFileName() != null) {
            fileStorageService.deleteAppSetting(settings.getLogoStoredFileName());
            settings.setLogoStoredFileName(null);
            settings.setLogoContentType(null);
            appSettingsRepository.save(settings);
            auditService.record("AppSettings", "1", "LOGO_REMOVED", "Company logo reverted to default");
        }
    }

    private AppSettings getOrCreate() {
        return appSettingsRepository.findById(SETTINGS_ID)
                .orElseGet(() -> appSettingsRepository.save(AppSettings.builder().id(SETTINGS_ID).build()));
    }

    public EmployeeIdFormatResponse employeeIdFormat() {
        AppSettings settings = getOrCreate();
        return toFormatResponse(settings);
    }

    /** SUPER_ADMIN/HR_ADMIN only (see SecurityConfig). Only affects employees created after this call - existing employeeCodes are never regenerated or renamed. */
    @Transactional
    public EmployeeIdFormatResponse updateEmployeeIdFormat(String prefix, String suffix) {
        AppSettings settings = getOrCreate();
        settings.setEmployeeIdPrefix(prefix);
        settings.setEmployeeIdSuffix(suffix);
        appSettingsRepository.save(settings);

        auditService.record("AppSettings", "1", "EMPLOYEE_ID_FORMAT_UPDATED",
                "Employee ID format changed to \"" + prefix + "####" + suffix + "\" (existing employee codes unaffected)");
        return toFormatResponse(settings);
    }

    /**
     * Generates the next employee code (e.g. "EMP-0007") and advances the
     * sequence counter in the same transaction, so it's called from
     * within EmployeeService.create's own @Transactional method - both
     * the new Employee row and the incremented counter commit together
     * or not at all. The sequence is never reused, even if an employee
     * is later deleted, so two employees can never end up with the same
     * code from this method alone.
     *
     * Known limitation: this reads-then-writes the settings row without
     * a pessimistic lock, so two employees created in the same
     * split-second by concurrent requests could theoretically read the
     * same sequence value before either write commits, producing a
     * duplicate code (the `employee_code` unique constraint would catch
     * it as a save failure, not silently corrupt data, but the second
     * request would fail rather than gracefully retry). Fine for a
     * single HR admin creating employees one at a time; a real
     * high-concurrency deployment would want a DB sequence or a
     * pessimistic lock on the settings row instead.
     */
    @Transactional
    public String generateNextEmployeeCode() {
        AppSettings settings = getOrCreate();
        String code = formatCode(settings, settings.getNextEmployeeIdSequence());
        settings.setNextEmployeeIdSequence(settings.getNextEmployeeIdSequence() + 1);
        appSettingsRepository.save(settings);
        return code;
    }

    private EmployeeIdFormatResponse toFormatResponse(AppSettings settings) {
        return new EmployeeIdFormatResponse(
                settings.getEmployeeIdPrefix(),
                settings.getEmployeeIdSuffix(),
                settings.getNextEmployeeIdSequence(),
                formatCode(settings, settings.getNextEmployeeIdSequence())
        );
    }

    private String formatCode(AppSettings settings, int sequence) {
        String padded = String.format("%0" + SEQUENCE_PAD_WIDTH + "d", sequence);
        return settings.getEmployeeIdPrefix() + padded + settings.getEmployeeIdSuffix();
    }
}
