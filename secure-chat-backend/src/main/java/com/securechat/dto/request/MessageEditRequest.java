package com.securechat.dto.request;

import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * REST request body for editing a message via PUT /api/messages/{messageId}.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class MessageEditRequest {

    @Size(max = 10000, message = "Message content must not exceed 10,000 characters")
    private String content;

    /** Whether the new content is client-side encrypted (E2EE). */
    private boolean encrypted;

    /** Base64-encoded AES-GCM initialization vector. Required when encrypted=true. */
    private String iv;
}
