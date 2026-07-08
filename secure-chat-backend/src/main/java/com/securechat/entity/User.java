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
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * JPA entity representing a registered user in the Secure Chat System.
 *
 * Maps to the {@code users} table. Passwords are stored as BCrypt hashes;
 * plain-text credentials are never persisted.
 */
@Entity
@Table(name = "users")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "username", nullable = false, unique = true, length = 50)
    private String username;

    @Column(name = "email", nullable = false, unique = true, length = 255)
    private String email;

    @Column(name = "password_hash", nullable = false, length = 255)
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.NAMED_ENUM)
    @Column(name = "role", nullable = false, length = 10)
    @Builder.Default
    private Role role = Role.USER;

    @Column(name = "online_status", nullable = false)
    @Builder.Default
    private boolean onlineStatus = false;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    /**
     * Conversations this user participates in.
     * Mapped via the {@code conversation_members} join table.
     */
    @ManyToMany(mappedBy = "members", fetch = FetchType.LAZY)
    @Builder.Default
    private Set<Conversation> conversations = new HashSet<>();

    /**
     * User roles within the system.
     */
    public enum Role {
        USER, ADMIN
    }
}
