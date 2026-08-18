package com.securechat.repository;

import com.securechat.entity.MessageReaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Spring Data JPA repository for {@link MessageReaction} entities.
 *
 * Provides methods for toggling, querying, and batch-loading emoji reactions.
 */
@Repository
public interface MessageReactionRepository extends JpaRepository<MessageReaction, UUID> {

    /**
     * Finds all reactions for a specific message, ordered by creation time.
     *
     * @param messageId the message to query
     * @return list of reactions
     */
    List<MessageReaction> findByMessageIdOrderByCreatedAtAsc(UUID messageId);

    /**
     * Finds a specific reaction by message, user, and emoji.
     * Used for toggle logic (add/remove).
     *
     * @param messageId the message
     * @param userId    the user
     * @param emoji     the emoji
     * @return the existing reaction, if any
     */
    Optional<MessageReaction> findByMessageIdAndUserIdAndEmoji(
            UUID messageId, UUID userId, String emoji);

    /**
     * Batch-loads all reactions for a list of messages.
     * Used for efficient reaction loading in paginated history.
     *
     * @param messageIds the message IDs to query
     * @return list of reactions across all specified messages
     */
    @Query("SELECT mr FROM MessageReaction mr WHERE mr.message.id IN :messageIds")
    List<MessageReaction> findByMessageIdIn(@Param("messageIds") List<UUID> messageIds);

    /**
     * Deletes all reactions for a specific message.
     * Used when a message is deleted for everyone.
     *
     * @param messageId the message whose reactions to remove
     */
    void deleteByMessageId(UUID messageId);
}
