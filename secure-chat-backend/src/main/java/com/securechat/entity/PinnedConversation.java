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
 * JPA entity representing a per-user pinned conversation.
 *
 * This is separate from the conversation_members join table to avoid
 * disrupting the existing @ManyToMany mapping. A user can pin any
 * conversation they are a member of; pinning is a personal preference.
 */
@Entity
@Table(name = "pinned_conversations")
@IdClass(PinnedConversation.PinnedConversationId.class)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PinnedConversation {

    @Id
    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Id
    @Column(name = "conversation_id", nullable = false)
    private UUID conversationId;

    @CreationTimestamp
    @Column(name = "pinned_at", nullable = false, updatable = false)
    private Instant pinnedAt;

    /**
     * Composite primary key class for PinnedConversation.
     */
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PinnedConversationId implements Serializable {
        private UUID userId;
        private UUID conversationId;

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            PinnedConversationId that = (PinnedConversationId) o;
            return userId.equals(that.userId) && conversationId.equals(that.conversationId);
        }

        @Override
        public int hashCode() {
            return 31 * userId.hashCode() + conversationId.hashCode();
        }
    }
}
