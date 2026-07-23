package com.eems.controller;

import com.eems.dto.EmployeePhotoDtos.PhotoStatusResponse;
import com.eems.service.EmployeePhotoService;
import com.eems.service.EmployeePhotoService.DownloadResult;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/employees/{employeeId}/photo")
@RequiredArgsConstructor
public class EmployeePhotoController {

    private final EmployeePhotoService photoService;

    @GetMapping("/status")
    public PhotoStatusResponse status(@PathVariable Long employeeId, Authentication authentication) {
        return photoService.status(employeeId, authentication);
    }

    @PostMapping(consumes = "multipart/form-data")
    public PhotoStatusResponse upload(@PathVariable Long employeeId, @RequestParam("file") MultipartFile file, Authentication authentication) {
        return photoService.upload(employeeId, file, authentication);
    }

    @GetMapping
    public ResponseEntity<InputStreamResource> download(@PathVariable Long employeeId, Authentication authentication) {
        DownloadResult result = photoService.download(employeeId, authentication);
        MediaType mediaType = result.contentType() != null ? MediaType.parseMediaType(result.contentType()) : MediaType.IMAGE_JPEG;
        return ResponseEntity.ok()
                .contentType(mediaType)
                .body(new InputStreamResource(result.stream()));
    }

    @DeleteMapping
    public void delete(@PathVariable Long employeeId, Authentication authentication) {
        photoService.delete(employeeId, authentication);
    }
}
