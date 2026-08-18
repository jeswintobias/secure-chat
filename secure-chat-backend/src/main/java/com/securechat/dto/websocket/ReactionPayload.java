package com.securechat.dto.websocket;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

/**
 * STOMP payload for message reaction operations over WebSocket.
 *
 * Sent by clients to /app/chat.react/{conversationId}.
 * Toggle behavior: if the reaction already exists, it is removed.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReactionPayload {

    @NotNull
    private UUID messageId;

    @NotBlank
    private String emoji;
}
