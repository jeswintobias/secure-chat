package com.securechat.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.Map;

/**
 * Standardized error response body for all API errors.
 * Provides a consistent structure for REST and WebSocket error payloads.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiErrorResponse {

    private int status;

    private String error;

    private String message;

    private String path;

    private Instant timestamp;

    /** Field-level validation errors, keyed by field name. */
    private Map<String, String> fieldErrors;
}
