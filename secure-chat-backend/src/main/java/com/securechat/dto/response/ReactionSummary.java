package com.securechat.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

/**
 * DTO representing an aggregated emoji reaction on a message.
 *
 * Groups all reactions of the same emoji together with a count
 * and the list of usernames who reacted.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReactionSummary {

    /** The emoji (e.g., "👍", "❤️"). */
    private String emoji;

    /** Number of users who reacted with this emoji. */
    private int count;

    /** Usernames of the users who reacted with this emoji. */
    private List<String> usernames;
}
