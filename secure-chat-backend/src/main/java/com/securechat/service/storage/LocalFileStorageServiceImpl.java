package com.securechat.service.storage;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.UUID;

/**
 * Implementation of FileStorageService for local filesystem storage.
 * Used when app.storage.provider=local (or by default if not specified).
 */
@Service
@ConditionalOnProperty(name = "app.storage.provider", havingValue = "local", matchIfMissing = true)
@Slf4j
public class LocalFileStorageServiceImpl implements FileStorageService {

    private final String uploadDir;

    public LocalFileStorageServiceImpl(@Value("${app.upload.dir:./uploads}") String uploadDir) {
        this.uploadDir = uploadDir;
    }

    @Override
    public String uploadFile(MultipartFile file, String originalFilename, String extension) throws IOException {
        Path uploadPath = Paths.get(uploadDir).toAbsolutePath().normalize();
        Files.createDirectories(uploadPath);

        String storedFilename = UUID.randomUUID() + extension;
        Path targetPath = uploadPath.resolve(storedFilename);
        
        Files.copy(file.getInputStream(), targetPath, StandardCopyOption.REPLACE_EXISTING);
        log.info("File saved locally: {} -> {}", originalFilename, storedFilename);

        return "/api/upload/files/" + storedFilename;
    }

    @Override
    public byte[] getFile(String filename) throws IOException {
        Path filePath = Paths.get(uploadDir).toAbsolutePath().normalize().resolve(filename);
        if (!Files.exists(filePath)) {
            throw new IOException("File not found: " + filename);
        }
        return Files.readAllBytes(filePath);
    }

    @Override
    public boolean fileExists(String filename) {
        Path filePath = Paths.get(uploadDir).toAbsolutePath().normalize().resolve(filename);
        return Files.exists(filePath);
    }
}
