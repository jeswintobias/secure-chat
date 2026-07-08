package com.securechat.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

/**
 * DTO for sending a chat message (used by both REST and WebSocket endpoints).
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ChatMessageRequest {

    @NotNull(message = "Conversation ID is required")
    private UUID conversationId;

    @NotBlank(message = "Message content is required")
    @Size(max = 10000, message = "Message content must not exceed 10,000 characters")
    private String content;

    /**
     * Optional: message expiry in minutes.
     * If null or zero, the message is non-ephemeral (persists indefinitely).
     */
    @Min(value = 0, message = "Expiry minutes must be non-negative")
    private Integer expiryMinutes;
}
