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

    /** Original filename of the attachment (human-readable). */
    private String originalName;

    /** Whether this message is pinned in its conversation. */
    private boolean pinned;

    /** Username of the admin who pinned this message. */
    private String pinnedBy;

    /** Timestamp when this message was pinned. */
    private Instant pinnedAt;

    /** Whether this message's content is client-side encrypted (E2EE). */
    private boolean encrypted;

    /** Base64-encoded AES-GCM initialization vector. Null for non-encrypted messages. */
    private String iv;

    /** List of read receipts for this message. */
    private List<MessageReadDto> readReceipts;

    /** Whether this message has been edited after sending. */
    private boolean edited;

    /** Timestamp of the most recent edit. Null if never edited. */
    private Instant editedAt;

    /** Whether this message has been deleted for everyone (soft delete). */
    private boolean deleted;

    /** Timestamp of deletion. Null if not deleted. */
    private Instant deletedAt;

    /** Username of the user who deleted this message. */
    private String deletedBy;

    /** Aggregated emoji reactions for this message. */
    private List<ReactionSummary> reactions;
}
