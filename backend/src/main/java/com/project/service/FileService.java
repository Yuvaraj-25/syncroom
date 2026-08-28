package com.project.service;

import com.project.model.File;
import com.project.repository.FileRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

@Service
@Transactional
public class FileService {

    private final FileRepository fileRepository;
    private final FileStorageService fileStorageService;

    public FileService(FileRepository fileRepository, FileStorageService fileStorageService) {
        this.fileRepository = fileRepository;
        this.fileStorageService = fileStorageService;
    }

    public File uploadFile(String roomId, String uploaderId, String uploaderName, MultipartFile file) throws IOException {
        FileStorageService.StoredFileInfo storedInfo = fileStorageService.storeFile(file, roomId);

        File fileEntity = new File(
                roomId,
                uploaderId,
                uploaderName,
                storedInfo.originalFilename(),
                storedInfo.storedFilename(),
                storedInfo.contentType(),
                storedInfo.fileSize(),
                storedInfo.relativePath()
        );

        return fileRepository.save(fileEntity);
    }

    public List<File> getRoomFiles(String roomId) {
        return fileRepository.findByRoomIdOrderByCreatedAtDesc(roomId);
    }

    public List<File> getRecentRoomFiles(String roomId, int limit) {
        return fileRepository.findRecentFilesByRoomId(roomId, limit);
    }

    public Optional<File> getFileById(String fileId) {
        return fileRepository.findById(fileId);
    }

    public Optional<File> getFileByStoredFilename(String storedFilename) {
        return fileRepository.findByStoredFilename(storedFilename);
    }

    public boolean deleteFile(String fileId) {
        Optional<File> fileOpt = fileRepository.findById(fileId);
        if (fileOpt.isPresent()) {
            File file = fileOpt.get();
            boolean deleted = fileStorageService.deleteFile(file.getRoomId(), file.getStoredFilename());
            if (deleted) {
                fileRepository.delete(file);
            }
            return deleted;
        }
        return false;
    }

    public String getDownloadUrl(String baseUrl, File file) {
        return String.format("%s/api/files/%s/download", baseUrl, file.getId());
    }
}