package com.securechat.dto.websocket;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;
import java.util.UUID;

/**
 * STOMP payload for roster update events.
 *
 * Broadcast to /topic/conversation/{conversationId}/roster
 * whenever the member list of a group changes (join or removal).
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RosterUpdatePayload {

    private UUID conversationId;

    /** Updated total member count. */
    private int memberCount;

    /** Updated list of member usernames. */
    private List<String> memberUsernames;

    /** The username that triggered this roster change. */
    private String changedUsername;

    /** Type of roster change: JOIN or LEAVE. */
    private RosterEventType eventType;

    public enum RosterEventType {
        JOIN, LEAVE
    }
}
