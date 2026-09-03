package com.securechat.service;

import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.securechat.dto.response.AuthResponse;
import com.securechat.entity.User;
import com.securechat.repository.UserRepository;
import com.securechat.security.JwtTokenProvider;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.Collections;
import java.util.Optional;
import java.util.UUID;

/**
 * Service handling Google OAuth authentication.
 *
 * <p>Responsibilities:
 * <ul>
 *   <li>Verifying Google ID tokens using {@link GoogleIdTokenVerifier}.</li>
 *   <li>Finding or creating users based on verified Google identity.</li>
 *   <li>Linking Google accounts with existing email-based accounts.</li>
 *   <li>Deriving unique usernames from email prefixes for new Google users.</li>
 * </ul>
 *
 * <p>All methods return DTOs — never raw entities.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class GoogleAuthService {

    private final UserRepository userRepository;
    private final JwtTokenProvider jwtTokenProvider;

    @Value("${app.google.client-id}")
    private String googleClientId;

    private GoogleIdTokenVerifier verifier;

    @PostConstruct
    void init() {
        verifier = new GoogleIdTokenVerifier.Builder(
                new NetHttpTransport(), GsonFactory.getDefaultInstance())
                .setAudience(Collections.singletonList(googleClientId))
                .setAcceptableTimeSkewSeconds(120) // Allow 2 minutes of clock skew
                .build();
    }

    /**
     * Authenticates a user via a Google ID token.
     *
     * <p>Flow:
     * <ol>
     *   <li>Verify the ID token with Google's servers (checks signature, audience, expiry).</li>
     *   <li>Extract user info (email, name, picture, subject ID) from the token payload.</li>
     *   <li>Find or create the user in the database.</li>
     *   <li>Generate and return an app JWT.</li>
     * </ol>
     *
     * @param idTokenString the Google ID token from the frontend
     * @return an AuthResponse containing the app JWT and user metadata
     * @throws IllegalArgumentException if the token is invalid or verification fails
     */
    @Transactional
    public AuthResponse authenticateGoogleUser(String idTokenString) {
        GoogleIdToken idToken = verifyToken(idTokenString);
        GoogleIdToken.Payload payload = idToken.getPayload();

        String googleSubjectId = payload.getSubject();
        String email = payload.getEmail();
        String name = (String) payload.get("name");
        String pictureUrl = (String) payload.get("picture");

        User user = findOrCreateUser(googleSubjectId, email, name, pictureUrl);

        String token = jwtTokenProvider.generateToken(user.getUsername());
        log.info("Google user authenticated: {} ({})", user.getUsername(), email);

        return AuthResponse.builder()
                .token(token)
                .tokenType("Bearer")
                .username(user.getUsername())
                .email(user.getEmail())
                .role(user.getRole().name())
                .expiresIn(jwtTokenProvider.getExpirationMs())
                .build();
    }

    /**
     * Verifies a Google ID token string and returns the parsed token.
     *
     * @param idTokenString the raw ID token
     * @return the verified GoogleIdToken
     * @throws IllegalArgumentException if verification fails
     */
    private GoogleIdToken verifyToken(String idTokenString) {
        try {
            GoogleIdToken idToken = verifier.verify(idTokenString);
            if (idToken == null) {
                throw new IllegalArgumentException("Invalid Google ID token");
            }
            return idToken;
        } catch (GeneralSecurityException | IOException e) {
            log.error("Google ID token verification failed: {}", e.getMessage());
            throw new IllegalArgumentException("Failed to verify Google ID token", e);
        }
    }

    /**
     * Finds an existing user or creates a new one based on Google identity.
     *
     * <p>Resolution order:
     * <ol>
     *   <li>Match by provider_id + GOOGLE → return existing Google user.</li>
     *   <li>Match by email → link the Google identity to the existing account.</li>
     *   <li>No match → create a brand-new user.</li>
     * </ol>
     */
    private User findOrCreateUser(String googleSubjectId, String email, String name, String pictureUrl) {
        // 1. Check for existing Google-linked user
        Optional<User> googleUser = userRepository.findByProviderId(googleSubjectId);
        if (googleUser.isPresent()) {
            User existing = googleUser.get();
            // Update profile picture if it changed
            if (pictureUrl != null && !pictureUrl.equals(existing.getProfilePictureUrl())) {
                existing.setProfilePictureUrl(pictureUrl);
                userRepository.save(existing);
            }
            return existing;
        }

        // 2. Check for existing user with same email (account linking)
        Optional<User> emailUser = userRepository.findByEmail(email);
        if (emailUser.isPresent()) {
            User existing = emailUser.get();
            existing.setAuthProvider(User.AuthProvider.GOOGLE);
            existing.setProviderId(googleSubjectId);
            if (pictureUrl != null) {
                existing.setProfilePictureUrl(pictureUrl);
            }
            userRepository.save(existing);
            log.info("Linked Google account to existing user: {}", existing.getUsername());
            return existing;
        }

        // 3. Create new user
        String username = deriveUniqueUsername(email, name);
        User newUser = User.builder()
                .username(username)
                .email(email)
                .authProvider(User.AuthProvider.GOOGLE)
                .providerId(googleSubjectId)
                .profilePictureUrl(pictureUrl)
                .role(User.Role.USER)
                .onlineStatus(true)
                .build();

        userRepository.save(newUser);
        log.info("Created new Google user: {} ({})", username, email);
        return newUser;
    }

    /**
     * Derives a unique username from the email prefix or Google display name.
     *
     * <p>Strategy: uses the part before '@' in the email. If that username is
     * already taken, appends a random suffix (e.g., {@code john_a3f2}).
     *
     * @param email the user's email
     * @param name  the user's Google display name (fallback)
     * @return a unique username
     */
    private String deriveUniqueUsername(String email, String name) {
        // Extract prefix from email
        String base = email.contains("@") ? email.substring(0, email.indexOf('@')) : email;

        // Clean: only keep alphanumeric, dots, underscores, hyphens; truncate to 42 chars
        base = base.replaceAll("[^a-zA-Z0-9._-]", "").toLowerCase();
        if (base.length() < 3) {
            // Fallback to name or a random string
            base = (name != null && name.length() >= 3)
                    ? name.replaceAll("[^a-zA-Z0-9._-]", "").toLowerCase()
                    : "user";
        }
        if (base.length() > 42) {
            base = base.substring(0, 42);
        }

        // Check if base username is available
        if (!userRepository.existsByUsername(base)) {
            return base;
        }

        // Append a short random suffix until unique
        for (int i = 0; i < 10; i++) {
            String suffix = UUID.randomUUID().toString().substring(0, 4);
            String candidate = base + "_" + suffix;
            if (candidate.length() > 50) {
                candidate = candidate.substring(0, 50);
            }
            if (!userRepository.existsByUsername(candidate)) {
                return candidate;
            }
        }

        // Extremely unlikely fallback
        return base + "_" + UUID.randomUUID().toString().substring(0, 8);
    }
}
