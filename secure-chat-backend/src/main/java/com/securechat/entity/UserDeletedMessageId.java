package com.securechat.entity;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

/**
 * Composite primary key class for {@link UserDeletedMessage}.
 *
 * Required by JPA when using {@code @IdClass} with a composite key.
 */
public class UserDeletedMessageId implements Serializable {

    private UUID userId;
    private UUID messageId;

    public UserDeletedMessageId() {}

    public UserDeletedMessageId(UUID userId, UUID messageId) {
        this.userId = userId;
        this.messageId = messageId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof UserDeletedMessageId that)) return false;
        return Objects.equals(userId, that.userId) && Objects.equals(messageId, that.messageId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(userId, messageId);
    }
}
