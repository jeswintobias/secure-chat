package com.securechat.repository;

import com.securechat.entity.ChatMessage;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Spring Data JPA repository for {@link ChatMessage} entities.
 *
 * Provides paginated message retrieval with automatic expiry filtering
 * and a bulk-delete method for expired message cleanup.
 */
@Repository
public interface ChatMessageRepository extends JpaRepository<ChatMessage, UUID> {

    /**
     * Fetches non-expired messages for a conversation, ordered newest-first.
     * Messages with a NULL expires_at are treated as non-ephemeral (always included).
     *
     * @param conversationId the conversation to query
     * @param now            the current instant, used for expiry comparison
     * @param pageable       pagination and sort parameters
     * @return a page of active (non-expired) messages
     */
    @Query("""
            SELECT m FROM ChatMessage m
            WHERE m.conversation.id = :conversationId
              AND (m.expiresAt IS NULL OR m.expiresAt > :now)
            ORDER BY m.createdAt DESC
            """)
    Page<ChatMessage> findActiveMessagesByConversationId(
            @Param("conversationId") UUID conversationId,
            @Param("now") Instant now,
            Pageable pageable
    );

    /**
     * Bulk-deletes all messages whose expiry timestamp has passed.
     * Intended to be called by a scheduled cleanup task.
     *
     * @param now the current instant
     * @return the number of messages deleted
     */
    @Modifying
    @Query("DELETE FROM ChatMessage m WHERE m.expiresAt IS NOT NULL AND m.expiresAt <= :now")
    int deleteExpiredMessages(@Param("now") Instant now);

    /**
     * Fetches all pinned messages for a conversation, ordered by pin time.
     *
     * @param conversationId the conversation to query
     * @return list of pinned messages
     */
    @Query("""
            SELECT m FROM ChatMessage m
            WHERE m.conversation.id = :conversationId
              AND m.pinned = true
            ORDER BY m.pinnedAt DESC
            """)
    List<ChatMessage> findPinnedMessagesByConversationId(
            @Param("conversationId") UUID conversationId
    );

    /**
     * Finds all unread messages in a conversation for a specific user.
     * Excludes messages sent by the user themselves.
     *
     * @param conversationId the conversation
     * @param userId         the reader's user ID
     * @return list of unread messages
     */
    @Query("""
            SELECT m FROM ChatMessage m
            WHERE m.conversation.id = :conversationId
              AND m.sender.id != :userId
              AND NOT EXISTS (
                  SELECT 1 FROM MessageRead mr
                  WHERE mr.message.id = m.id AND mr.user.id = :userId
              )
            """)
    List<ChatMessage> findUnreadMessagesByConversationIdAndUserId(
            @Param("conversationId") UUID conversationId,
            @Param("userId") UUID userId
    );
}
