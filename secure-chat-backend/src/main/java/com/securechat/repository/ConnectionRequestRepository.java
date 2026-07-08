package com.securechat.repository;

import com.securechat.entity.ConnectionRequest;
import com.securechat.entity.ConnectionRequest.RequestStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Spring Data JPA repository for {@link ConnectionRequest} entities.
 */
@Repository
public interface ConnectionRequestRepository extends JpaRepository<ConnectionRequest, UUID> {

    /**
     * Finds all pending requests received by a user, ordered newest-first.
     * Uses the partial index on (receiver_id, status) WHERE status = 'PENDING'.
     */
    List<ConnectionRequest> findByReceiverIdAndStatusOrderByCreatedAtDesc(UUID receiverId, RequestStatus status);

    /**
     * Finds all requests sent by a user, ordered newest-first.
     */
    List<ConnectionRequest> findBySenderIdOrderByCreatedAtDesc(UUID senderId);

    /**
     * Checks if a connection request already exists between two users (in either direction).
     * This prevents duplicate or conflicting requests.
     */
    @Query("""
            SELECT cr FROM ConnectionRequest cr
            WHERE (cr.sender.id = :userA AND cr.receiver.id = :userB)
               OR (cr.sender.id = :userB AND cr.receiver.id = :userA)
            """)
    Optional<ConnectionRequest> findExistingBetween(@Param("userA") UUID userA, @Param("userB") UUID userB);
}
