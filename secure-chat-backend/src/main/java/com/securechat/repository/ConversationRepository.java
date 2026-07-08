package com.securechat.repository;

import com.securechat.entity.Conversation;
import com.securechat.entity.Conversation.ConversationType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Spring Data JPA repository for {@link Conversation} entities.
 */
@Repository
public interface ConversationRepository extends JpaRepository<Conversation, UUID> {

    /**
     * Finds all conversations a user is a member of.
     */
    @Query("""
            SELECT c FROM Conversation c
            JOIN c.members m
            WHERE m.id = :userId
            ORDER BY c.updatedAt DESC
            """)
    List<Conversation> findAllByMemberId(@Param("userId") UUID userId);

    /**
     * Finds an existing PRIVATE conversation between exactly two users.
     * Used to prevent duplicate private conversations.
     */
    @Query("""
            SELECT c FROM Conversation c
            WHERE c.type = 'PRIVATE'
              AND (SELECT COUNT(m) FROM c.members m WHERE m.id IN (:userA, :userB)) = 2
              AND (SELECT COUNT(m) FROM c.members m) = 2
            """)
    Optional<Conversation> findPrivateConversation(
            @Param("userA") UUID userAId,
            @Param("userB") UUID userBId
    );

    /**
     * Finds a GROUP conversation by its referral code.
     */
    Optional<Conversation> findByReferralCodeAndType(String referralCode, ConversationType type);

    /**
     * Finds all GROUP conversations.
     */
    List<Conversation> findAllByType(ConversationType type);
}
