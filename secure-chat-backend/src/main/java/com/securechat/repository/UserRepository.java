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
            ORDER BY username
            LIMIT 10
            """, nativeQuery = true)
    List<User> searchByUsernamePrefix(@Param("pattern") String pattern);
}
