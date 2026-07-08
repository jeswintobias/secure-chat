package com.securechat.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * DTO representing a chat message in API responses and WebSocket broadcasts.
 * Never exposes the raw {@code ChatMessage} entity.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MessageResponse {

    private UUID id;

    private UUID conversationId;

    private String senderUsername;

    private UUID senderId;

    private String content;

    private String messageType;

    private Instant createdAt;

    private Instant expiresAt;

    /** URL/path to uploaded file attachment. Null for text-only messages. */
    private String attachmentUrl;

    /** MIME type of the attachment (e.g. image/png, application/pdf). */
    private String attachmentType;

    /** Whether this message is pinned in its conversation. */
    private boolean pinned;

    /** Username of the admin who pinned this message. */
    private String pinnedBy;

    /** Timestamp when this message was pinned. */
    private Instant pinnedAt;

    /** List of read receipts for this message. */
    private List<MessageReadDto> readReceipts;
}
