package com.securechat.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * DTO representing a user profile in API responses.
 * Omits sensitive data (password hash, email) by design.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserResponse {

    private UUID id;

    private String username;

    private String role;

    private boolean online;

    private Instant lastSeen;

    private String lastSeenPrivacy;

    private Boolean readReceiptsEnabled;

    private boolean isDeleted;

    private Instant createdAt;
}
