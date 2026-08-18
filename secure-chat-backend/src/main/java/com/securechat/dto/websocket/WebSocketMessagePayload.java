package com.securechat.dto.websocket;

import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * STOMP payload for incoming chat messages over WebSocket.
 *
 * This is the inbound frame payload — the conversationId is extracted
 * from the STOMP destination path (e.g., /app/chat.send/{conversationId}).
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WebSocketMessagePayload {

    @Size(max = 10000, message = "Message content must not exceed 10,000 characters")
    private String content;

    /**
     * Optional expiry in minutes. Zero or null = non-ephemeral.
     */
    private Integer expiryMinutes;

    /** URL of an uploaded attachment (set after file upload via REST). */
    private String attachmentUrl;

    /** MIME type of the attachment. */
    private String attachmentType;

    /** Original filename of the attachment. */
    private String originalName;

    /** Whether the content is client-side encrypted (E2EE). */
    private boolean encrypted;

    /** Base64-encoded AES-GCM initialization vector. Required when encrypted=true. */
    private String iv;
}
