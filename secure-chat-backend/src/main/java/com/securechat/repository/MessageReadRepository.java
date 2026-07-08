package com.securechat.repository;

import com.securechat.entity.MessageRead;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

/**
 * Spring Data JPA repository for {@link MessageRead} entities.
 */
@Repository
public interface MessageReadRepository extends JpaRepository<MessageRead, UUID> {

    /**
     * Finds all read receipts for a specific message.
     *
     * @param messageId the ID of the message
     * @return a list of read receipts
     */
    List<MessageRead> findByMessageId(UUID messageId);

    /**
     * Finds read receipts for a list of message IDs.
     * Useful for batch fetching read receipts for a page of messages.
     *
     * @param messageIds list of message IDs
     * @return a list of read receipts
     */
    List<MessageRead> findByMessageIdIn(List<UUID> messageIds);

    /**
     * Checks if a user has already read a specific message.
     *
     * @param messageId the ID of the message
     * @param userId    the ID of the user
     * @return true if a read receipt exists
     */
    boolean existsByMessageIdAndUserId(UUID messageId, UUID userId);
}
