package com.securechat.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Thrown when a group join attempt uses an invalid referral code.
 * Maps to HTTP 403 Forbidden.
 */
@ResponseStatus(HttpStatus.FORBIDDEN)
public class InvalidReferralCodeException extends RuntimeException {

    public InvalidReferralCodeException(String message) {
        super(message);
    }
}
