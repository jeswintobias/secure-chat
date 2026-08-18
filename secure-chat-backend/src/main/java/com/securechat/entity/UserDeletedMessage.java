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
 * JPA entity tracking per-user message deletion ("Delete for Me").
 *
 * When a user chooses "Delete for Me", a row is inserted here.
 * The message still exists for all other participants — only the
 * requesting user's view filters it out.
 *
 * Uses a composite primary key of (user_id, message_id).
 */
@Entity
@Table(name = "user_deleted_messages")
@IdClass(UserDeletedMessageId.class)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserDeletedMessage {

    /** The user who deleted this message from their view. */
    @Id
    @Column(name = "user_id", nullable = false)
    private UUID userId;

    /** The message that was hidden. */
    @Id
    @Column(name = "message_id", nullable = false)
    private UUID messageId;

    @CreationTimestamp
    @Column(name = "deleted_at", nullable = false, updatable = false)
    private Instant deletedAt;
}
