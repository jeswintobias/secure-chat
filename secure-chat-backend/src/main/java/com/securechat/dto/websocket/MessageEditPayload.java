package com.securechat.dto.websocket;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

/**
 * STOMP payload for message edit operations over WebSocket.
 *
 * Sent by clients to /app/chat.edit/{conversationId}.
 * The conversationId is extracted from the STOMP destination path.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MessageEditPayload {

    @NotNull
    private UUID messageId;

    @Size(max = 10000, message = "Message content must not exceed 10,000 characters")
    private String content;

    /** Whether the new content is client-side encrypted (E2EE). */
    private boolean encrypted;

    /** Base64-encoded AES-GCM initialization vector. Required when encrypted=true. */
    private String iv;
}
