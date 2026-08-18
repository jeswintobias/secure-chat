package com.securechat.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

/**
 * JPA entity representing a single emoji reaction on a chat message.
 *
 * Each user can react with a specific emoji only once per message.
 * Reacting with the same emoji again removes the reaction (toggle behavior).
 */
@Entity
@Table(name = "message_reactions",
       uniqueConstraints = @UniqueConstraint(
           name = "uq_message_reactions_message_user_emoji",
           columnNames = {"message_id", "user_id", "emoji"}
       ))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MessageReaction {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    /** The message this reaction belongs to. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "message_id", nullable = false)
    private ChatMessage message;

    /** The user who reacted. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    /** The emoji used for this reaction (e.g., "👍", "❤️"). */
    @Column(name = "emoji", nullable = false, length = 20)
    private String emoji;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
