package com.securechat.repository;

import com.securechat.entity.ConversationKeyBundle;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for per-member encrypted group conversation key bundles.
 *
 * <p>Each entry represents one member's wrapped copy of a group's
 * AES-256-GCM symmetric key. Only used for GROUP conversations.
 */
@Repository
public interface ConversationKeyBundleRepository
        extends JpaRepository<ConversationKeyBundle, ConversationKeyBundle.ConversationKeyBundleId> {

    /**
     * Finds a specific user's key bundle for a conversation.
     */
    Optional<ConversationKeyBundle> findByConversationIdAndUserId(UUID conversationId, UUID userId);

    /**
     * Finds all key bundles for a conversation (all members' wrapped keys).
     */
    List<ConversationKeyBundle> findAllByConversationId(UUID conversationId);

    /**
     * Deletes all key bundles for a conversation (used during key rotation).
     */
    void deleteAllByConversationId(UUID conversationId);
}
