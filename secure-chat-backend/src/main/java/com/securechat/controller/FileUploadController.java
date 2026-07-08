package com.securechat.controller;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Map;
import java.util.UUID;

/**
 * REST controller for file/image uploads.
 *
 * Uploaded files are stored on the local filesystem under the configured
 * upload directory. Each file is given a UUID-based filename to avoid collisions.
 *
 * The returned URL can then be referenced in a WebSocket message payload
 * as {@code attachmentUrl} when sending the message.
 */
@RestController
@RequestMapping("/api/upload")
@Slf4j
public class FileUploadController {

    @Value("${app.upload.dir:./uploads}")
    private String uploadDir;

    @Value("${app.upload.max-size-mb:10}")
    private int maxSizeMb;

    /**
     * Uploads a file and returns its accessible URL.
     *
     * @param file the multipart file to upload
     * @return JSON with the file URL and content type
     */
    @PostMapping
    public ResponseEntity<Map<String, String>> uploadFile(@RequestParam("file") MultipartFile file) throws IOException {
        if (file.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "File is empty"));
        }

        if (file.getSize() > (long) maxSizeMb * 1024 * 1024) {
            return ResponseEntity.badRequest().body(Map.of("error", "File exceeds maximum size of " + maxSizeMb + "MB"));
        }

        // Ensure upload directory exists
        Path uploadPath = Paths.get(uploadDir).toAbsolutePath().normalize();
        Files.createDirectories(uploadPath);

        // Generate unique filename preserving extension
        String originalFilename = file.getOriginalFilename();
        String extension = "";
        if (originalFilename != null && originalFilename.contains(".")) {
            extension = originalFilename.substring(originalFilename.lastIndexOf("."));
        }
        String storedFilename = UUID.randomUUID() + extension;

        // Store the file
        Path targetPath = uploadPath.resolve(storedFilename);
        Files.copy(file.getInputStream(), targetPath, StandardCopyOption.REPLACE_EXISTING);

        log.info("File uploaded: {} -> {} ({})", originalFilename, storedFilename, file.getContentType());

        // Return the URL that the frontend can use to reference this file
        String fileUrl = "/api/upload/files/" + storedFilename;

        return ResponseEntity.ok(Map.of(
                "url", fileUrl,
                "contentType", file.getContentType() != null ? file.getContentType() : "application/octet-stream",
                "originalName", originalFilename != null ? originalFilename : storedFilename
        ));
    }

    /**
     * Serves an uploaded file by its stored filename.
     *
     * @param filename the UUID-based filename
     * @return the file bytes with appropriate content type
     */
    @GetMapping("/files/{filename:.+}")
    public ResponseEntity<byte[]> getFile(@PathVariable String filename) throws IOException {
        Path filePath = Paths.get(uploadDir).toAbsolutePath().normalize().resolve(filename);

        if (!Files.exists(filePath)) {
            return ResponseEntity.notFound().build();
        }

        byte[] fileBytes = Files.readAllBytes(filePath);
        String contentType = Files.probeContentType(filePath);
        if (contentType == null) {
            contentType = "application/octet-stream";
        }

        return ResponseEntity.ok()
                .header("Content-Type", contentType)
                .header("Content-Disposition", "inline; filename=\"" + filename + "\"")
                .body(fileBytes);
    }
}
