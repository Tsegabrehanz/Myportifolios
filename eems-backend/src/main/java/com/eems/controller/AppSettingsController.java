package com.eems.controller;

import com.eems.dto.AppSettingsDtos.LogoStatusResponse;
import com.eems.service.AppSettingsService;
import com.eems.service.AppSettingsService.DownloadResult;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/app-settings/logo")
@RequiredArgsConstructor
public class AppSettingsController {

    private final AppSettingsService appSettingsService;

    /** Public/unauthenticated - see SecurityConfig. Just tells the frontend whether a custom logo exists, without needing to attempt the (also public) download and handle a 404. */
    @GetMapping("/status")
    public LogoStatusResponse status() {
        return appSettingsService.logoStatus();
    }

    /** Public/unauthenticated - the login page needs to show the logo before anyone has logged in. */
    @GetMapping
    public ResponseEntity<InputStreamResource> download() {
        DownloadResult result = appSettingsService.downloadLogo();
        MediaType mediaType = result.contentType() != null ? MediaType.parseMediaType(result.contentType()) : MediaType.IMAGE_PNG;
        return ResponseEntity.ok().contentType(mediaType).body(new InputStreamResource(result.stream()));
    }

    /** SUPER_ADMIN only - see SecurityConfig. */
    @PostMapping(consumes = "multipart/form-data")
    public LogoStatusResponse upload(@RequestParam("file") MultipartFile file) {
        return appSettingsService.uploadLogo(file);
    }

    /** SUPER_ADMIN only - see SecurityConfig. */
    @DeleteMapping
    public void remove() {
        appSettingsService.removeLogo();
    }
}
