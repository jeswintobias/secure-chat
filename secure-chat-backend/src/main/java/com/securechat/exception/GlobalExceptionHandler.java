package com.securechat.exception;

import com.securechat.dto.response.ApiErrorResponse;
import com.securechat.exception.UnsafeUrlException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;

import java.time.Instant;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Global exception handler for all REST controllers.
 *
 * Translates domain exceptions into consistent {@link ApiErrorResponse} payloads
 * with appropriate HTTP status codes. This ensures the API never leaks stack traces
 * or internal details to the client.
 */
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    // ==================== 400 Bad Request ====================

    /**
     * Handles bean validation failures (e.g., @NotBlank, @Email violations).
     * Returns a map of field-level error messages.
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiErrorResponse> handleValidationExceptions(
            MethodArgumentNotValidException ex,
            WebRequest request
    ) {
        Map<String, String> fieldErrors = ex.getBindingResult().getFieldErrors().stream()
                .collect(Collectors.toMap(
                        FieldError::getField,
                        fieldError -> fieldError.getDefaultMessage() != null
                                ? fieldError.getDefaultMessage()
                                : "Invalid value",
                        (existing, replacement) -> existing  // Keep first error per field
                ));

        ApiErrorResponse response = ApiErrorResponse.builder()
                .status(HttpStatus.BAD_REQUEST.value())
                .error("Validation Failed")
                .message("One or more fields have validation errors")
                .path(request.getDescription(false))
                .timestamp(Instant.now())
                .fieldErrors(fieldErrors)
                .build();

        return ResponseEntity.badRequest().body(response);
    }

    /**
     * Handles IllegalArgumentException (e.g., passwords don't match, invalid input).
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiErrorResponse> handleIllegalArgument(
            IllegalArgumentException ex,
            WebRequest request
    ) {
        return buildErrorResponse(HttpStatus.BAD_REQUEST, "Bad Request", ex.getMessage(), request);
    }

    // ==================== 401 Unauthorized ====================

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ApiErrorResponse> handleAuthenticationException(
            AuthenticationException ex,
            WebRequest request
    ) {
        return buildErrorResponse(HttpStatus.UNAUTHORIZED, "Authentication Failed", ex.getMessage(), request);
    }

    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<ApiErrorResponse> handleBadCredentials(
            BadCredentialsException ex,
            WebRequest request
    ) {
        return buildErrorResponse(HttpStatus.UNAUTHORIZED, "Invalid Credentials",
                "Username or password is incorrect", request);
    }

    // ==================== 403 Forbidden ====================

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiErrorResponse> handleAccessDenied(
            AccessDeniedException ex,
            WebRequest request
    ) {
        return buildErrorResponse(HttpStatus.FORBIDDEN, "Access Denied", ex.getMessage(), request);
    }

    @ExceptionHandler(SecurityException.class)
    public ResponseEntity<ApiErrorResponse> handleSecurityException(
            SecurityException ex,
            WebRequest request
    ) {
        return buildErrorResponse(HttpStatus.FORBIDDEN, "Forbidden", ex.getMessage(), request);
    }

    @ExceptionHandler(InvalidReferralCodeException.class)
    public ResponseEntity<ApiErrorResponse> handleInvalidReferralCode(
            InvalidReferralCodeException ex,
            WebRequest request
    ) {
        return buildErrorResponse(HttpStatus.FORBIDDEN, "Invalid Referral Code", ex.getMessage(), request);
    }

    // ==================== 404 Not Found ====================

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleResourceNotFound(
            ResourceNotFoundException ex,
            WebRequest request
    ) {
        return buildErrorResponse(HttpStatus.NOT_FOUND, "Resource Not Found", ex.getMessage(), request);
    }

    // ==================== 409 Conflict ====================

    @ExceptionHandler(DuplicateResourceException.class)
    public ResponseEntity<ApiErrorResponse> handleDuplicateResource(
            DuplicateResourceException ex,
            WebRequest request
    ) {
        return buildErrorResponse(HttpStatus.CONFLICT, "Duplicate Resource", ex.getMessage(), request);
    }

    // ==================== 422 Unprocessable Entity ====================

    /**
     * Handles UnsafeUrlException — a message contained a URL blocked by the
     * URL security pipeline (malware, phishing, blocked extension, SSRF, etc.).
     */
    @ExceptionHandler(UnsafeUrlException.class)
    public ResponseEntity<ApiErrorResponse> handleUnsafeUrl(
            UnsafeUrlException ex,
            WebRequest request
    ) {
        // Build a detailed message listing the blocked URLs
        StringBuilder details = new StringBuilder(ex.getMessage());
        if (ex.getBlockedUrls() != null && !ex.getBlockedUrls().isEmpty()) {
            details.append(": ");
            for (UnsafeUrlException.BlockedUrlDetail detail : ex.getBlockedUrls()) {
                details.append("[").append(detail.url()).append(" — ").append(detail.reason()).append("] ");
            }
        }

        log.warn("Blocked unsafe URL(s): {}", details);
        return buildErrorResponse(
                HttpStatus.UNPROCESSABLE_ENTITY,
                "Unsafe URL Detected",
                details.toString().trim(),
                request
        );
    }

    // ==================== 500 Internal Server Error ====================

    /**
     * Catch-all handler for unexpected exceptions.
     * Logs the full stack trace but returns a generic message to the client.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorResponse> handleAllUncaughtExceptions(
            Exception ex,
            WebRequest request
    ) {
        log.error("Unhandled exception: ", ex);
        return buildErrorResponse(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "Internal Server Error",
                "An unexpected error occurred. Please try again later.",
                request
        );
    }

    // ==================== Helper ====================

    private ResponseEntity<ApiErrorResponse> buildErrorResponse(
            HttpStatus status,
            String error,
            String message,
            WebRequest request
    ) {
        ApiErrorResponse response = ApiErrorResponse.builder()
                .status(status.value())
                .error(error)
                .message(message)
                .path(request.getDescription(false))
                .timestamp(Instant.now())
                .build();
        return ResponseEntity.status(status).body(response);
    }
}
