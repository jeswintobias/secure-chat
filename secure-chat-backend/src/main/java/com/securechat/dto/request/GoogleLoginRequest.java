package com.securechat.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * DTO for Google OAuth login requests.
 *
 * <p>Contains the Google ID token obtained from the frontend
 * via Google Identity Services (GIS). The backend verifies this
 * token against Google's servers before issuing an app JWT.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class GoogleLoginRequest {

    @NotBlank(message = "Google ID token is required")
    private String idToken;
}
