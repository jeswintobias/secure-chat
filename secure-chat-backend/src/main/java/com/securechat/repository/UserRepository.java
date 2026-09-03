package com.securechat.repository;

import com.securechat.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Spring Data JPA repository for {@link User} entities.
 */
@Repository
public interface UserRepository extends JpaRepository<User, UUID> {

    Optional<User> findByUsername(String username);

    Optional<User> findByEmail(String email);

    boolean existsByUsername(String username);

    boolean existsByEmail(String email);

    Optional<User> findByProviderId(String providerId);

    /**
     * Searches for users whose username starts with the given prefix (case-insensitive).
     *
     * <p>Uses a native query with LOWER() and LIKE to leverage the
     * {@code idx_users_username_pattern} B-tree index with {@code varchar_pattern_ops}.
     * Results are limited to 10 to prevent excessive data retrieval.
     *
     * <p>The caller must append the {@code %} wildcard to the pattern before calling
     * (e.g. {@code "kio%"}). This avoids JDBC parameter-binding issues with the
     * {@code ||} concatenation operator inside native queries.
     *
     * @param pattern the LIKE pattern to search for (lowercase prefix + '%')
     * @return up to 10 matching users
     */
    @Query(value = """
            SELECT * FROM users
            WHERE LOWER(username) LIKE :pattern
              AND is_deleted = false
            ORDER BY username
            LIMIT 10
            """, nativeQuery = true)
    List<User> searchByUsernamePrefix(@Param("pattern") String pattern);

    /**
     * Finds all users who share at least one conversation with the given user.
     *
     * <p>Used by the {@code GET /api/users/presence} endpoint to seed the
     * frontend presence map on initial load, so that users who were already
     * online before the current user connected appear with the correct status.
     *
     * @param userId the current user's UUID
     * @return all users who are co-members in any conversation with the given user
     */
    @Query("""
            SELECT DISTINCT u FROM User u
            JOIN u.conversations c
            JOIN c.members m
            WHERE m.id = :userId AND u.id <> :userId
            """)
    List<User> findContactsByUserId(@Param("userId") UUID userId);
}
