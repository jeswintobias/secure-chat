package com.securechat.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

/**
 * DTO representing a reaction event — broadcast to all subscribers
 * when a user adds or removes a reaction.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReactionResponse {

    private UUID messageId;

    private String username;

    private UUID userId;

    private String emoji;

    /**
     * "ADDED" or "REMOVED" — indicates whether the reaction was
     * added or toggled off.
     */
    private String action;
}
