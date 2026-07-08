package com.securechat.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * DTO representing a group conversation in API responses.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GroupResponse {

    private UUID id;

    private String name;

    /** Conversation type: "GROUP" or "PRIVATE". */
    private String type;

    private String referralCode;

    private int memberCount;

    private List<String> memberUsernames;

    private Instant createdAt;

    private Instant updatedAt;
}
