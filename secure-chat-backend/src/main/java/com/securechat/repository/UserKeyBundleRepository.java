package com.securechat.repository;

import com.securechat.entity.UserKeyBundle;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for user ECDH public key bundles.
 *
 * <p>Each user has at most one key bundle (the public key uploaded
 * after key generation on the client).
 */
@Repository
public interface UserKeyBundleRepository extends JpaRepository<UserKeyBundle, UUID> {

    /**
     * Finds a user's key bundle by user ID.
     */
    Optional<UserKeyBundle> findByUserId(UUID userId);

    /**
     * Batch fetch key bundles for multiple users (e.g., all members of a group).
     */
    List<UserKeyBundle> findAllByUserIdIn(List<UUID> userIds);
}
