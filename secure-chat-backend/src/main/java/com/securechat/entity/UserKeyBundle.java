package com.securechat.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.UUID;

/**
 * JPA entity storing a user's ECDH public key for E2EE.
 *
 * <p>The corresponding private key never leaves the client browser.
 * Only the public key is uploaded to the server so that other users
 * can derive shared secrets (for PRIVATE chats) or wrap group keys
 * (for GROUP chats).
 *
 * <p>Maps to the {@code user_key_bundles} table.
 */
@Entity
@Table(name = "user_key_bundles")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserKeyBundle {

    @Id
    @Column(name = "user_id", updatable = false, nullable = false)
    private UUID userId;

    /**
     * Base64-encoded ECDH P-256 public key in JWK (JSON Web Key) format.
     */
    @Column(name = "public_key", nullable = false, columnDefinition = "TEXT")
    private String publicKey;

    /**
     * Algorithm identifier (e.g., "ECDH-P256").
     */
    @Column(name = "key_algorithm", nullable = false, length = 50)
    @Builder.Default
    private String keyAlgorithm = "ECDH-P256";

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
