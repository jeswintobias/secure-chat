package com.securechat.dto.websocket;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

/**
 * STOMP payload for message delete operations over WebSocket.
 *
 * Sent by clients to /app/chat.delete/{conversationId}.
 * The conversationId is extracted from the STOMP destination path.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MessageDeletePayload {

    @NotNull
    private UUID messageId;

    /**
     * Deletion mode:
     * - "EVERYONE" = soft delete for all participants
     * - "ME"       = hide only for the requesting user
     */
    private String mode;
}
