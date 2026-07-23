package com.eems.storage;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Objects;
import java.util.UUID;

/**
 * Real local-disk file storage - this is genuinely written and read
 * from disk, not a stub. Files live under `file.upload-dir` (see
 * application.yml), one subfolder per "bucket" (an employee's uploads,
 * or the app-wide settings bucket for things like the company logo),
 * with a UUID-prefixed filename on disk (so two uploads named "cv.pdf"
 * never collide) while the original filename is preserved separately
 * in the database for display/download.
 *
 * This is local disk, not S3/MinIO/Azure Blob - fine for a single-
 * instance deployment with a persistent volume (see docker-compose.yml,
 * which mounts a volume at this path), but won't survive a container
 * being recreated without that volume, and won't work at all if this
 * app is ever horizontally scaled to multiple instances (each would
 * have its own disk). Migrating to real object storage would mean
 * swapping this class's implementation - the method signatures
 * wouldn't need to change.
 */
@Service
public class FileStorageService {

    /** Fixed bucket name for app-wide settings (currently just the company logo) - not tied to any employee. */
    private static final String APP_SETTINGS_BUCKET = "app-settings";

    private final Path baseDir;

    public FileStorageService(@Value("${file.upload-dir:/tmp/eems-uploads}") String uploadDir) {
        this.baseDir = Paths.get(uploadDir).toAbsolutePath().normalize();
        try {
            Files.createDirectories(this.baseDir);
        } catch (IOException e) {
            throw new IllegalStateException("Could not create upload directory: " + this.baseDir, e);
        }
    }

    /** @return the generated on-disk filename (store this, not the full path, in the database) */
    public String store(Long employeeId, MultipartFile file) {
        return storeInBucket("employee-" + employeeId, file);
    }

    public InputStream load(Long employeeId, String storedFileName) {
        return loadFromBucket("employee-" + employeeId, storedFileName);
    }

    public void delete(Long employeeId, String storedFileName) {
        deleteFromBucket("employee-" + employeeId, storedFileName);
    }

    /** Same as store(), but for app-wide settings (the company logo) rather than a specific employee's uploads. */
    public String storeAppSetting(MultipartFile file) {
        return storeInBucket(APP_SETTINGS_BUCKET, file);
    }

    public InputStream loadAppSetting(String storedFileName) {
        return loadFromBucket(APP_SETTINGS_BUCKET, storedFileName);
    }

    public void deleteAppSetting(String storedFileName) {
        deleteFromBucket(APP_SETTINGS_BUCKET, storedFileName);
    }

    private String storeInBucket(String bucket, MultipartFile file) {
        String originalName = Objects.requireNonNullElse(file.getOriginalFilename(), "file");
        String safeOriginalName = originalName.replaceAll("[^a-zA-Z0-9._-]", "_");
        String storedFileName = UUID.randomUUID() + "-" + safeOriginalName;

        Path bucketDir = baseDir.resolve(bucket);
        try {
            Files.createDirectories(bucketDir);
            Path target = bucketDir.resolve(storedFileName).normalize();

            // Defense in depth against a crafted filename trying to escape
            // bucketDir via "../" sequences - normalize() above already
            // resolves those, this just double-checks the result is still
            // inside bucketDir before writing anything.
            if (!target.startsWith(bucketDir)) {
                throw new IllegalArgumentException("Invalid file name");
            }

            file.transferTo(target);
            return storedFileName;
        } catch (IOException e) {
            throw new IllegalStateException("Failed to store uploaded file", e);
        }
    }

    private InputStream loadFromBucket(String bucket, String storedFileName) {
        Path file = resolveAndValidate(bucket, storedFileName);
        try {
            if (!Files.exists(file)) {
                throw new IllegalStateException("Stored file is missing on disk: " + storedFileName);
            }
            return Files.newInputStream(file);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read stored file", e);
        }
    }

    private void deleteFromBucket(String bucket, String storedFileName) {
        Path file = resolveAndValidate(bucket, storedFileName);
        try {
            Files.deleteIfExists(file);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to delete stored file", e);
        }
    }

    private Path resolveAndValidate(String bucket, String storedFileName) {
        Path bucketDir = baseDir.resolve(bucket);
        Path file = bucketDir.resolve(storedFileName).normalize();
        if (!file.startsWith(bucketDir)) {
            throw new IllegalArgumentException("Invalid file name");
        }
        return file;
    }
}
