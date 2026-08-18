package com.securechat.repository;

import com.securechat.entity.UserDeletedMessage;
import com.securechat.entity.UserDeletedMessageId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Set;
import java.util.UUID;

/**
 * Spring Data JPA repository for {@link UserDeletedMessage} entities.
 *
 * Tracks per-user "Delete for Me" actions. When a user hides a message
 * from their view, a row is inserted here. The message continues to
 * exist for all other participants.
 */
@Repository
public interface UserDeletedMessageRepository extends JpaRepository<UserDeletedMessage, UserDeletedMessageId> {

    /**
     * Checks whether a specific user has deleted a specific message from their view.
     *
     * @param userId    the user
     * @param messageId the message
     * @return true if the user has hidden this message
     */
    boolean existsByUserIdAndMessageId(UUID userId, UUID messageId);

    /**
     * Returns the set of message IDs that a specific user has deleted from their view
     * within a given conversation. Used to filter messages in history queries.
     *
     * @param userId         the user
     * @param conversationId the conversation
     * @return set of hidden message IDs
     */
    @Query("""
            SELECT udm.messageId FROM UserDeletedMessage udm
            WHERE udm.userId = :userId
              AND udm.messageId IN (
                  SELECT m.id FROM ChatMessage m WHERE m.conversation.id = :conversationId
              )
            """)
    Set<UUID> findDeletedMessageIdsByUserAndConversation(
            @Param("userId") UUID userId,
            @Param("conversationId") UUID conversationId);
}
