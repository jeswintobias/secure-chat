package com.securechat.dto.websocket;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * WebSocket payload broadcast to {@code /topic/presence} when a user
 * connects or disconnects from the STOMP WebSocket.
 *
 * <p>All connected clients subscribe to this topic to maintain
 * real-time online/offline status in the UI.
 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PresencePayload {

    /** The username whose status changed. */
    private String username;

    /** True if the user just came online, false if they went offline. */
    private boolean online;

    /** Timestamp of when the user went offline (null if online). */
    private java.time.Instant lastSeen;
}
