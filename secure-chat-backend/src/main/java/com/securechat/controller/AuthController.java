package com.securechat.controller;

import com.securechat.dto.request.GoogleLoginRequest;
import com.securechat.dto.request.LoginRequest;
import com.securechat.dto.request.RegisterRequest;
import com.securechat.dto.response.AuthResponse;
import com.securechat.service.AuthService;
import com.securechat.service.GoogleAuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller for authentication endpoints.
 *
 * <p>All endpoints under {@code /api/auth/**} are publicly accessible
 * (configured in {@link com.securechat.config.SecurityConfig}).
 */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final GoogleAuthService googleAuthService;

    /**
     * Registers a new user account and returns a JWT for immediate login.
     *
     * @param request the validated registration request
     * @return 201 Created with AuthResponse containing JWT
     */
    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest request) {
        AuthResponse response = authService.register(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * Authenticates a user and returns a JWT.
     *
     * @param request the validated login request
     * @return 200 OK with AuthResponse containing JWT
     */
    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        AuthResponse response = authService.login(request);
        return ResponseEntity.ok(response);
    }

    /**
     * Authenticates a user via Google OAuth and returns a JWT.
     *
     * <p>Accepts a Google ID token obtained from Google Identity Services
     * on the frontend. Verifies the token, finds or creates the user,
     * and returns an app JWT for subsequent authenticated requests.
     *
     * @param request the validated Google login request containing the ID token
     * @return 200 OK with AuthResponse containing JWT
     */
    @PostMapping("/google")
    public ResponseEntity<AuthResponse> googleLogin(@Valid @RequestBody GoogleLoginRequest request) {
        AuthResponse response = googleAuthService.authenticateGoogleUser(request.getIdToken());
        return ResponseEntity.ok(response);
    }
}
