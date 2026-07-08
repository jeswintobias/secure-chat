package com.securechat.repository;

import com.securechat.entity.PinnedConversation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

/**
 * Spring Data JPA repository for {@link PinnedConversation} entities.
 *
 * Provides queries for per-user conversation pinning.
 */
@Repository
public interface PinnedConversationRepository
        extends JpaRepository<PinnedConversation, PinnedConversation.PinnedConversationId> {

    /** Returns all pinned conversation IDs for a user. */
    List<PinnedConversation> findByUserId(UUID userId);

    /** Checks if a specific conversation is pinned by a user. */
    boolean existsByUserIdAndConversationId(UUID userId, UUID conversationId);

    /** Deletes a pin for a specific user and conversation. */
    void deleteByUserIdAndConversationId(UUID userId, UUID conversationId);
}
