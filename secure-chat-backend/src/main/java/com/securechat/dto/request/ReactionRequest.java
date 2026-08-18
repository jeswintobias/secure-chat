package com.securechat.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * REST request body for reacting to a message via POST /api/messages/{messageId}/react.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ReactionRequest {

    @NotBlank
    private String emoji;
}
