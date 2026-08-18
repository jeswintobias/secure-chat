package com.securechat.controller;

import com.securechat.service.storage.FileStorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Map;

/**
 * REST controller for file/image/audio uploads.
 *
 * Delegates to a FileStorageService (Local or S3).
 * The returned URL can then be referenced in a WebSocket message payload
 * as {@code attachmentUrl} when sending the message.
 */
@RestController
@RequestMapping("/api/upload")
@RequiredArgsConstructor
@Slf4j
public class FileUploadController {

    private final FileStorageService fileStorageService;

    @Value("${app.upload.max-size-mb:25}")
    private int maxSizeMb;

    /**
     * Uploads a file and returns its accessible URL.
     *
     * @param file the multipart file to upload
     * @return JSON with the file URL and content type
     */
    @PostMapping
    public ResponseEntity<Map<String, String>> uploadFile(@RequestParam("file") MultipartFile file) {
        if (file.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "File is empty"));
        }

        if (file.getSize() > (long) maxSizeMb * 1024 * 1024) {
            return ResponseEntity.badRequest().body(Map.of("error", "File exceeds maximum size of " + maxSizeMb + "MB"));
        }

        try {
            String originalFilename = file.getOriginalFilename();
            String extension = "";
            if (originalFilename != null && originalFilename.contains(".")) {
                extension = originalFilename.substring(originalFilename.lastIndexOf("."));
            }

            String fileUrl = fileStorageService.uploadFile(file, originalFilename, extension);

            return ResponseEntity.ok(Map.of(
                    "url", fileUrl,
                    "contentType", file.getContentType() != null ? file.getContentType() : "application/octet-stream",
                    "originalName", originalFilename != null ? originalFilename : "uploaded_file"
            ));
        } catch (IOException e) {
            log.error("Failed to upload file", e);
            return ResponseEntity.internalServerError().body(Map.of("error", "Failed to upload file"));
        }
    }

    /**
     * Serves an uploaded file by its stored filename.
     *
     * @param filename the UUID-based filename
     * @return the file bytes with appropriate content type
     */
    @GetMapping("/files/{filename:.+}")
    public ResponseEntity<byte[]> getFile(@PathVariable String filename) {
        if (!fileStorageService.fileExists(filename)) {
            return ResponseEntity.notFound().build();
        }

        try {
            byte[] fileBytes = fileStorageService.getFile(filename);
            
            // Determine content type based on extension
            String contentType = "application/octet-stream";
            if (filename.endsWith(".png")) contentType = "image/png";
            else if (filename.endsWith(".jpg") || filename.endsWith(".jpeg")) contentType = "image/jpeg";
            else if (filename.endsWith(".webp")) contentType = "image/webp";
            else if (filename.endsWith(".mp4")) contentType = "video/mp4";
            else if (filename.endsWith(".webm")) contentType = "audio/webm";
            else if (filename.endsWith(".ogg")) contentType = "audio/ogg";
            else if (filename.endsWith(".mp3")) contentType = "audio/mpeg";
            else if (filename.endsWith(".pdf")) contentType = "application/pdf";

            String disposition = (contentType.startsWith("image/") || contentType.startsWith("video/") || contentType.startsWith("audio/") || contentType.equals("application/pdf")) 
                    ? "inline" : "attachment";

            return ResponseEntity.ok()
                    .header("Content-Type", contentType)
                    .header("Content-Disposition", disposition + "; filename=\"" + filename + "\"")
                    .body(fileBytes);
        } catch (IOException e) {
            log.error("Failed to retrieve file", e);
            return ResponseEntity.internalServerError().build();
        }
    }
}
