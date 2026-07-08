package com.securechat.config;

import com.securechat.dto.websocket.WebSocketErrorPayload;
import com.securechat.exception.ResourceNotFoundException;
import com.securechat.exception.UnsafeUrlException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.MessageExceptionHandler;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.annotation.SendToUser;
import org.springframework.web.bind.annotation.ControllerAdvice;

import java.security.Principal;
import java.time.Instant;
import java.util.List;

/**
 * Global exception handler for STOMP {@code @MessageMapping} methods.
 *
 * <p>Spring's {@code @RestControllerAdvice} does <b>not</b> intercept
 * exceptions thrown in WebSocket message handlers. This class fills that
 * gap by catching exceptions and sending structured error payloads to
 * the originating user via {@code /user/queue/errors}.
 *
 * <p>The frontend subscribes to {@code /user/queue/errors} to display
 * error toasts (e.g., "Your message was blocked because it contains a
 * malicious URL").
 *
 * @see com.securechat.exception.GlobalExceptionHandler for REST-side handling
 */
@ControllerAdvice
@Slf4j
@RequiredArgsConstructor
public class WebSocketExceptionHandler {

    private final SimpMessagingTemplate messagingTemplate;

    // ==================== Unsafe URL (blocked by security pipeline) ====================

    /**
     * Handles {@link UnsafeUrlException} — the message contained a URL blocked
     * by the 4-layer URL security pipeline (malware, phishing, SSRF, blocked extension).
     *
     * <p>Sends the blocked URL details back to the sender so the frontend can
     * display which URL was rejected and why.
     */
    @MessageExceptionHandler(UnsafeUrlException.class)
    @SendToUser("/queue/errors")
    public WebSocketErrorPayload handleUnsafeUrl(UnsafeUrlException ex, Principal principal) {
        String username = principal != null ? principal.getName() : "unknown";
        log.warn("Blocked message from {} — unsafe URL: {}", username, ex.getMessage());

        List<WebSocketErrorPayload.BlockedUrlDetail> details = ex.getBlockedUrls().stream()
                .map(d -> WebSocketErrorPayload.BlockedUrlDetail.builder()
                        .url(d.url())
                        .reason(d.reason())
                        .build())
                .toList();

        return WebSocketErrorPayload.builder()
                .errorType("UNSAFE_URL")
                .message(ex.getMessage())
                .details(details)
                .timestamp(Instant.now())
                .build();
    }

    // ==================== Resource not found ====================

    /**
     * Handles missing conversations/users referenced in WebSocket messages.
     */
    @MessageExceptionHandler(ResourceNotFoundException.class)
    @SendToUser("/queue/errors")
    public WebSocketErrorPayload handleNotFound(ResourceNotFoundException ex, Principal principal) {
        String username = principal != null ? principal.getName() : "unknown";
        log.warn("WebSocket resource not found for {}: {}", username, ex.getMessage());

        return WebSocketErrorPayload.builder()
                .errorType("NOT_FOUND")
                .message(ex.getMessage())
                .timestamp(Instant.now())
                .build();
    }

    // ==================== Illegal argument (e.g., not a member) ====================

    @MessageExceptionHandler(IllegalArgumentException.class)
    @SendToUser("/queue/errors")
    public WebSocketErrorPayload handleIllegalArgument(IllegalArgumentException ex, Principal principal) {
        String username = principal != null ? principal.getName() : "unknown";
        log.warn("WebSocket bad request from {}: {}", username, ex.getMessage());

        return WebSocketErrorPayload.builder()
                .errorType("BAD_REQUEST")
                .message(ex.getMessage())
                .timestamp(Instant.now())
                .build();
    }

    // ==================== Security exception (e.g., non-admin pin attempt) ====================

    @MessageExceptionHandler(SecurityException.class)
    @SendToUser("/queue/errors")
    public WebSocketErrorPayload handleSecurityException(SecurityException ex, Principal principal) {
        String username = principal != null ? principal.getName() : "unknown";
        log.warn("WebSocket security violation from {}: {}", username, ex.getMessage());

        return WebSocketErrorPayload.builder()
                .errorType("FORBIDDEN")
                .message(ex.getMessage())
                .timestamp(Instant.now())
                .build();
    }

    // ==================== Catch-all ====================

    /**
     * Catch-all for unexpected exceptions in WebSocket handlers.
     * Logs the full stack trace but returns a generic message to the client.
     */
    @MessageExceptionHandler(Exception.class)
    @SendToUser("/queue/errors")
    public WebSocketErrorPayload handleGenericException(Exception ex, Principal principal) {
        String username = principal != null ? principal.getName() : "unknown";
        log.error("Unhandled WebSocket exception for {}: ", username, ex);

        return WebSocketErrorPayload.builder()
                .errorType("INTERNAL_ERROR")
                .message("An unexpected error occurred. Please try again.")
                .timestamp(Instant.now())
                .build();
    }
}
