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
 * JPA entity representing a conversation (private or group).
 *
 * This is a unified model: PRIVATE conversations always have exactly 2 members
 * and a NULL name. GROUP conversations have a name, optional referral code, and
 * an optional public key for client-side encryption.
 */
@Entity
@Table(name = "conversations")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Conversation {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Enumerated(EnumType.STRING)
    @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.NAMED_ENUM)
    @Column(name = "type", nullable = false, length = 10)
    private ConversationType type;

    /** Display name — required for GROUP, NULL for PRIVATE. */
    @Column(name = "name", length = 100)
    private String name;

    /** Referral/invite code for GROUP conversations. */
    @Column(name = "referral_code", length = 64)
    private String referralCode;

    /** Group encryption public key, distributed to members for E2E encryption. */
    @Column(name = "public_key", length = 512)
    private String publicKey;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    /**
     * Members of this conversation, managed via the join table.
     * Owning side of the many-to-many relationship.
     */
    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "conversation_members",
            joinColumns = @JoinColumn(name = "conversation_id"),
            inverseJoinColumns = @JoinColumn(name = "user_id")
    )
    @Builder.Default
    private Set<User> members = new HashSet<>();

    /**
     * All messages in this conversation, ordered by creation time.
     */
    @OneToMany(mappedBy = "conversation", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @Builder.Default
    private Set<ChatMessage> messages = new HashSet<>();

    public enum ConversationType {
        PRIVATE, GROUP
    }
}
