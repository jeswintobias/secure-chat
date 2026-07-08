package com.securechat.dto.websocket;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.List;

/**
 * STOMP error payload sent to {@code /user/queue/errors} when a
 * {@code @MessageMapping} handler throws an exception.
 *
 * <p>This is the WebSocket equivalent of the REST {@code ApiErrorResponse}.
 * Since {@code @RestControllerAdvice} does not intercept STOMP exceptions,
 * the {@link com.securechat.config.WebSocketExceptionHandler} catches them
 * and sends this payload to the originating user's error queue.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WebSocketErrorPayload {

    /** Error category (e.g., "UNSAFE_URL", "VALIDATION_ERROR", "INTERNAL_ERROR"). */
    private String errorType;

    /** Human-readable error message. */
    private String message;

    /** Optional list of blocked URL details (only for UNSAFE_URL errors). */
    private List<BlockedUrlDetail> details;

    /** ISO-8601 timestamp of when the error occurred. */
    @Builder.Default
    private Instant timestamp = Instant.now();

    /**
     * Detail about a single blocked URL — mirrors
     * {@link com.securechat.exception.UnsafeUrlException.BlockedUrlDetail}.
     */
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class BlockedUrlDetail {
        private String url;
        private String reason;
    }
}
