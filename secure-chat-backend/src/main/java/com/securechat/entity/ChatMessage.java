package com.securechat.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

/**
 * JPA entity representing a single chat message within a conversation.
 *
 * Supports ephemeral (self-destructing) messages via the optional
 * {@code expiresAt} field. Messages with a non-null expiry are filtered
 * out of query results after their expiration timestamp.
 *
 * Supports file/image attachments via {@code attachmentUrl} and {@code attachmentType}.
 * Supports message pinning (per-group, ADMIN-only) via {@code pinned}, {@code pinnedBy}, {@code pinnedAt}.
 */
@Entity
@Table(name = "messages")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ChatMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    /** The conversation this message belongs to. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "conversation_id", nullable = false)
    private Conversation conversation;

    /** The user who sent this message. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "sender_id", nullable = false)
    private User sender;

    /** Message content — stored as received (may be client-encrypted). */
    @Column(name = "content", nullable = false, columnDefinition = "TEXT")
    private String content;

    @Enumerated(EnumType.STRING)
    @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.NAMED_ENUM)
    @Column(name = "message_type", nullable = false, length = 10)
    @Builder.Default
    private MessageType messageType = MessageType.TEXT;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    /**
     * Optional expiration timestamp for ephemeral messages.
     * NULL indicates the message does not expire.
     */
    @Column(name = "expires_at")
    private Instant expiresAt;

    /** URL/path to uploaded file attachment. NULL for text-only messages. */
    @Column(name = "attachment_url", columnDefinition = "TEXT")
    private String attachmentUrl;

    /** MIME type of the attachment (e.g. image/png, application/pdf). */
    @Column(name = "attachment_type", length = 100)
    private String attachmentType;

    /** Whether this message is pinned in its conversation. */
    @Column(name = "pinned", nullable = false)
    @Builder.Default
    private boolean pinned = false;

    /** Username of the admin who pinned this message. */
    @Column(name = "pinned_by", length = 50)
    private String pinnedBy;

    /** Timestamp when this message was pinned. */
    @Column(name = "pinned_at")
    private Instant pinnedAt;

    /**
     * Checks whether this message has passed its expiration time.
     *
     * @return true if the message has an expiry set and that expiry is in the past
     */
    public boolean isExpired() {
        return expiresAt != null && Instant.now().isAfter(expiresAt);
    }

    public enum MessageType {
        TEXT, SYSTEM, IMAGE, FILE
    }
}
