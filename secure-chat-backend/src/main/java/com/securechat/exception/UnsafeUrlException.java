package com.securechat.exception;

import java.util.List;

/**
 * Thrown when a message contains a URL that was blocked by the URL security pipeline.
 *
 * <p>This exception results in a 422 Unprocessable Entity response with details
 * about which URL(s) were blocked and why.
 */
public class UnsafeUrlException extends RuntimeException {

    private final List<BlockedUrlDetail> blockedUrls;

    public UnsafeUrlException(String message, List<BlockedUrlDetail> blockedUrls) {
        super(message);
        this.blockedUrls = List.copyOf(blockedUrls);
    }

    public List<BlockedUrlDetail> getBlockedUrls() {
        return blockedUrls;
    }

    /**
     * Detail about a single blocked URL.
     */
    public record BlockedUrlDetail(String url, String reason) {}
}
