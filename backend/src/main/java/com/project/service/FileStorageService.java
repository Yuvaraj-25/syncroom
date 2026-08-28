package com.project.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

@Service
public class FileStorageService {

    private final Path uploadDir;
    private final long maxFileSize;
    private final String[] allowedMimeTypes;

    public FileStorageService(
            @Value("${file.upload.dir:./uploads}") String uploadDir,
            @Value("${file.upload.max-size:52428800}") long maxFileSize,
            @Value("${file.upload.allowed-types:}") String allowedTypes) {
        this.uploadDir = Paths.get(uploadDir).toAbsolutePath().normalize();
        this.maxFileSize = maxFileSize;
        this.allowedMimeTypes = allowedTypes.isBlank()
                ? new String[]{} // empty = allow all
                : allowedTypes.split(",");

        try {
            Files.createDirectories(this.uploadDir);
        } catch (IOException e) {
            throw new RuntimeException("Could not create upload directory: " + this.uploadDir, e);
        }
    }

    public StoredFileInfo storeFile(MultipartFile file, String roomId) throws IOException {
        validateFile(file);

        String originalFilename = file.getOriginalFilename();
        if (originalFilename == null || originalFilename.isBlank()) {
            throw new IllegalArgumentException("Original filename is required");
        }

        // Sanitize filename
        String sanitizedOriginal = sanitizeFilename(originalFilename);
        String extension = getExtension(sanitizedOriginal);

        // Generate unique stored filename: timestamp_uuid.extension
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        String uuid = UUID.randomUUID().toString().substring(0, 8);
        String storedFilename = String.format("%s_%s%s", timestamp, uuid, extension);

        // Create room-specific subdirectory
        Path roomDir = this.uploadDir.resolve(roomId);
        Files.createDirectories(roomDir);

        Path targetPath = roomDir.resolve(storedFilename);
        Files.copy(file.getInputStream(), targetPath, StandardCopyOption.REPLACE_EXISTING);

        return new StoredFileInfo(
                sanitizedOriginal,
                storedFilename,
                file.getContentType(),
                file.getSize(),
                targetPath.toString(),
                roomDir.relativize(targetPath).toString() // relative path for URL
        );
    }

    public Path getFilePath(String roomId, String storedFilename) {
        return this.uploadDir.resolve(roomId).resolve(storedFilename).normalize();
    }

    public boolean deleteFile(String roomId, String storedFilename) {
        try {
            Path filePath = getFilePath(roomId, storedFilename);
            // Security check: ensure file is within upload directory
            if (!filePath.startsWith(this.uploadDir)) {
                return false;
            }
            return Files.deleteIfExists(filePath);
        } catch (IOException e) {
            return false;
        }
    }

    private void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("File is empty");
        }
        if (file.getSize() > maxFileSize) {
            throw new IllegalArgumentException(
                    String.format("File size exceeds maximum allowed size of %d bytes", maxFileSize));
        }
        if (allowedMimeTypes.length > 0) {
            String contentType = file.getContentType();
            if (contentType == null || !isAllowedType(contentType)) {
                throw new IllegalArgumentException("File type not allowed: " + contentType);
            }
        }
    }

    private boolean isAllowedType(String contentType) {
        for (String allowed : allowedMimeTypes) {
            if (allowed.endsWith("*")) {
                String prefix = allowed.substring(0, allowed.length() - 1);
                if (contentType.startsWith(prefix)) {
                    return true;
                }
            } else if (allowed.equalsIgnoreCase(contentType)) {
                return true;
            }
        }
        return false;
    }

    private String sanitizeFilename(String filename) {
        // Remove path traversal attempts
        String sanitized = filename.replaceAll("[^a-zA-Z0-9._-]", "_");
        // Limit length
        if (sanitized.length() > 255) {
            String ext = getExtension(sanitized);
            sanitized = sanitized.substring(0, 255 - ext.length()) + ext;
        }
        return sanitized;
    }

    private String getExtension(String filename) {
        int lastDot = filename.lastIndexOf('.');
        if (lastDot > 0 && lastDot < filename.length() - 1) {
            return filename.substring(lastDot);
        }
        return "";
    }

    public record StoredFileInfo(
            String originalFilename,
            String storedFilename,
            String contentType,
            long fileSize,
            String absolutePath,
            String relativePath
    ) {}
}