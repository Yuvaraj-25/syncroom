package com.project.controller;

import com.project.model.File;
import com.project.service.FileService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.io.IOException;
import java.net.MalformedURLException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/files")
public class FileController {

    private final FileService fileService;
    private final String uploadDir;

    public FileController(FileService fileService,
                          @Value("${file.upload.dir:./uploads}") String uploadDir) {
        this.fileService = fileService;
        this.uploadDir = uploadDir;
    }

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Map<String, Object>> uploadFile(
            @RequestParam("file") MultipartFile file,
            @RequestParam("roomId") String roomId,
            @RequestParam("uploaderId") String uploaderId,
            @RequestParam("uploaderName") String uploaderName) {

        try {
            File uploadedFile = fileService.uploadFile(roomId, uploaderId, uploaderName, file);

            String baseUrl = ServletUriComponentsBuilder.fromCurrentContextPath().build().toUriString();
            String downloadUrl = fileService.getDownloadUrl(baseUrl, uploadedFile);

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "file", Map.of(
                            "id", uploadedFile.getId(),
                            "roomId", uploadedFile.getRoomId(),
                            "uploaderId", uploadedFile.getUploaderId(),
                            "uploaderName", uploadedFile.getUploaderName(),
                            "originalFilename", uploadedFile.getOriginalFilename(),
                            "contentType", uploadedFile.getContentType(),
                            "fileSize", uploadedFile.getFileSize(),
                            "downloadUrl", downloadUrl,
                            "createdAt", uploadedFile.getCreatedAt()
                    )
            ));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "error", e.getMessage()
            ));
        } catch (IOException e) {
            return ResponseEntity.internalServerError().body(Map.of(
                    "success", false,
                    "error", "Failed to store file: " + e.getMessage()
            ));
        }
    }

    @GetMapping("/room/{roomId}")
    public ResponseEntity<List<File>> getRoomFiles(@PathVariable String roomId) {
        List<File> files = fileService.getRoomFiles(roomId);
        return ResponseEntity.ok(files);
    }

    @GetMapping("/{fileId}/download")
    public ResponseEntity<Resource> downloadFile(@PathVariable String fileId) {
        return fileService.getFileById(fileId)
                .map(this::buildDownloadResponse)
                .orElse(ResponseEntity.notFound().build());
    }

    private ResponseEntity<Resource> buildDownloadResponse(File file) {
        try {
            Path filePath = Paths.get(uploadDir).resolve(file.getFilePath()).normalize();
            Resource resource = new UrlResource(filePath.toUri());

            if (!resource.exists() || !resource.isReadable()) {
                return ResponseEntity.notFound().build();
            }

            String contentType = file.getContentType();
            if (contentType == null || contentType.isBlank()) {
                contentType = "application/octet-stream";
            }

            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType(contentType))
                    .header(HttpHeaders.CONTENT_DISPOSITION,
                            "attachment; filename=\"" + file.getOriginalFilename() + "\"")
                    .body(resource);
        } catch (MalformedURLException e) {
            return ResponseEntity.internalServerError().build();
        }
    }

    @DeleteMapping("/{fileId}")
    public ResponseEntity<Map<String, Object>> deleteFile(@PathVariable String fileId) {
        boolean deleted = fileService.deleteFile(fileId);
        if (deleted) {
            return ResponseEntity.ok(Map.of("success", true, "message", "File deleted"));
        } else {
            return ResponseEntity.notFound().build();
        }
    }
}