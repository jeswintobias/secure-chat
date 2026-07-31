package com.securechat.service;

import com.securechat.dto.request.LoginRequest;
import com.securechat.dto.request.RegisterRequest;
import com.securechat.dto.response.AuthResponse;
import com.securechat.entity.User;
import com.securechat.exception.DuplicateResourceException;
import com.securechat.repository.UserRepository;
import com.securechat.security.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service handling user registration and authentication.
 *
 * <p>Responsibilities:
 * <ul>
 *   <li>User registration with uniqueness validation and password hashing.</li>
 *   <li>User login with credential verification and JWT generation.</li>
 * </ul>
 *
 * <p>All methods return DTOs — never raw entities.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtTokenProvider jwtTokenProvider;

    /**
     * Registers a new user account.
     *
     * <p>Validates that the username and email are unique, confirms the passwords
     * match, hashes the password with BCrypt, persists the user, and returns
     * a JWT for immediate login.
     *
     * @param request the registration request DTO
     * @return an AuthResponse containing the JWT and user metadata
     * @throws DuplicateResourceException if username or email already exists
     * @throws IllegalArgumentException if passwords don't match
     */
    @Transactional
    public AuthResponse register(RegisterRequest request) {
        // Validate password confirmation
        if (!request.getPassword().equals(request.getConfirmPassword())) {
            throw new IllegalArgumentException("Passwords do not match");
        }

        // Check username uniqueness
        if (userRepository.existsByUsername(request.getUsername())) {
            throw new DuplicateResourceException("Username already taken: " + request.getUsername());
        }

        // Check email uniqueness
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new DuplicateResourceException("Email already registered: " + request.getEmail());
        }

        // Build and persist the new user with hashed password.
        // onlineStatus is set to true because the user auto-logs-in after
        // registration and immediately connects to WebSocket. This avoids
        // a race window where other users could see them as offline.
        User user = User.builder()
                .username(request.getUsername())
                .email(request.getEmail())
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .role(User.Role.USER)
                .onlineStatus(true)
                .build();

        userRepository.save(user);
        log.info("User registered successfully: {}", user.getUsername());

        // Generate JWT for immediate post-registration login
        String token = jwtTokenProvider.generateToken(user.getUsername());

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
     * Authenticates a user and returns a JWT.
     *
     * <p>Delegates credential verification to Spring Security's
     * {@link AuthenticationManager}, then generates a signed JWT.
     *
     * @param request the login request DTO
     * @return an AuthResponse containing the JWT and user metadata
     */
    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest request) {
        // Spring Security handles credential verification (including BCrypt comparison)
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                        request.getUsername(),
                        request.getPassword()
                )
        );

        String token = jwtTokenProvider.generateToken(authentication);

        // Look up the user for role information
        User user = userRepository.findByUsername(request.getUsername())
                .orElseThrow(() -> new RuntimeException("User not found after authentication"));

        log.info("User logged in: {}", request.getUsername());

        return AuthResponse.builder()
                .token(token)
                .tokenType("Bearer")
                .username(user.getUsername())
                .email(user.getEmail())
                .role(user.getRole().name())
                .expiresIn(jwtTokenProvider.getExpirationMs())
                .build();
    }
}
