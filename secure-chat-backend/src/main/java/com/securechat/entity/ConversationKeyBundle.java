package com.securechat.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.io.Serializable;
import java.time.Instant;
import java.util.UUID;

/**
 * JPA entity representing a per-member encrypted copy of a group conversation's
 * AES-256-GCM symmetric key.
 *
 * <p>For GROUP conversations, the group creator generates a random AES key and
 * wraps (encrypts) it with each member's ECDH-derived wrapping key. Each row
 * stores one member's copy of the wrapped key.
 *
 * <p>For PRIVATE conversations, this table is unused — keys are derived
 * directly via ECDH between the two members.
 *
 * <p>Maps to the {@code conversation_key_bundles} table with composite PK
 * {@code (conversationId, userId)}.
 */
@Entity
@Table(name = "conversation_key_bundles")
@IdClass(ConversationKeyBundle.ConversationKeyBundleId.class)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ConversationKeyBundle {

    @Id
    @Column(name = "conversation_id", nullable = false)
    private UUID conversationId;

    @Id
    @Column(name = "user_id", nullable = false)
    private UUID userId;

    /**
     * Base64-encoded AES-256-GCM key, wrapped (encrypted) with the user's
     * ECDH-derived wrapping key. Only the user's client can unwrap this.
     */
    @Column(name = "encrypted_key", nullable = false, columnDefinition = "TEXT")
    private String encryptedKey;

    /**
     * Key version — incremented when the group key is rotated.
     */
    @Column(name = "key_version", nullable = false)
    @Builder.Default
    private int keyVersion = 1;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    /**
     * Composite primary key class for ConversationKeyBundle.
     */
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ConversationKeyBundleId implements Serializable {
        private UUID conversationId;
        private UUID userId;

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            ConversationKeyBundleId that = (ConversationKeyBundleId) o;
            return conversationId.equals(that.conversationId) && userId.equals(that.userId);
        }

        @Override
        public int hashCode() {
            return 31 * conversationId.hashCode() + userId.hashCode();
        }
    }
}
