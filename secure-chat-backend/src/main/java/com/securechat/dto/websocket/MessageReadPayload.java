package com.securechat.dto.websocket;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * Payload sent by clients to indicate they have read a message.
 * Or sent by server to broadcast a read receipt.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MessageReadPayload {
    private UUID messageId;
    private UUID conversationId;
    private UUID userId;
    private String username;
    private Instant readAt;
}
