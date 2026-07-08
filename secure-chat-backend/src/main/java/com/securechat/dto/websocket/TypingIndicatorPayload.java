package com.securechat.dto.websocket;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

/**
 * STOMP payload for typing indicator events.
 *
 * Broadcast to all members of a conversation to show
 * real-time "user is typing..." feedback.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TypingIndicatorPayload {

    private UUID conversationId;

    private String username;

    private boolean typing;
}
