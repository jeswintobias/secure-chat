package com.securechat.dto.response;

import lombok.*;

import java.time.Instant;
import java.util.UUID;

/**
 * DTO representing a connection request in API responses.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ConnectionRequestResponse {

    private UUID id;

    /** The user who initiated the request. */
    private UUID senderId;
    private String senderUsername;

    /** The user who received the request. */
    private UUID receiverId;
    private String receiverUsername;

    /** Current status: PENDING, ACCEPTED, or REJECTED. */
    private String status;

    /** The conversation ID created upon acceptance (null if still pending). */
    private UUID conversationId;

    private Instant createdAt;
    private Instant updatedAt;
}
