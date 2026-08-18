package com.securechat.service.storage;

import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;

/**
 * Strategy interface for file storage.
 * Implementations can provide local filesystem storage, AWS S3, MinIO, etc.
 */
public interface FileStorageService {

    /**
     * Uploads a file and returns its access URL.
     *
     * @param file the multipart file to upload
     * @param originalFilename the original filename
     * @param extension the file extension
     * @return the accessible URL for the uploaded file
     * @throws IOException if storage fails
     */
    String uploadFile(MultipartFile file, String originalFilename, String extension) throws IOException;

    /**
     * Retrieves the file content by filename.
     * Note: Cloud storage providers might return a presigned URL instead, 
     * but for this unified interface, we return bytes to maintain compatibility 
     * with the existing FileUploadController /api/upload/files endpoint.
     *
     * @param filename the stored filename
     * @return the byte array of the file
     * @throws IOException if retrieval fails or file does not exist
     */
    byte[] getFile(String filename) throws IOException;

    /**
     * Checks if a file exists.
     * 
     * @param filename the stored filename
     * @return true if the file exists
     */
    boolean fileExists(String filename);
}
