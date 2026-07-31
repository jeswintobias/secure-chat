package com.securechat.service;

import com.securechat.dto.request.LoginRequest;
import com.securechat.dto.request.RegisterRequest;
import com.securechat.dto.response.AuthResponse;
import com.securechat.entity.User;
import com.securechat.exception.DuplicateResourceException;
import com.securechat.repository.UserRepository;
import com.securechat.security.JwtTokenProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link AuthService}.
 *
 * Tests cover the registration and login flows including
 * validation, duplicate detection, and JWT generation.
 */
@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private AuthenticationManager authenticationManager;
    @Mock private JwtTokenProvider jwtTokenProvider;

    @InjectMocks
    private AuthService authService;

    private RegisterRequest validRegisterRequest;
    private LoginRequest validLoginRequest;
    private User testUser;

    @BeforeEach
    void setUp() {
        validRegisterRequest = RegisterRequest.builder()
                .username("testuser")
                .email("test@example.com")
                .password("SecurePass1!")
                .confirmPassword("SecurePass1!")
                .build();

        validLoginRequest = LoginRequest.builder()
                .username("testuser")
                .password("SecurePass1!")
                .build();

        testUser = User.builder()
                .id(UUID.randomUUID())
                .username("testuser")
                .email("test@example.com")
                .passwordHash("$2a$10$hashedpassword")
                .role(User.Role.USER)
                .onlineStatus(false)
                .build();
    }

    // ======================== Registration Tests ========================

    @Test
    @DisplayName("register — happy path: unique user, matching passwords → returns AuthResponse with JWT")
    void register_success() {
        // Arrange
        when(userRepository.existsByUsername("testuser")).thenReturn(false);
        when(userRepository.existsByEmail("test@example.com")).thenReturn(false);
        when(passwordEncoder.encode("SecurePass1!")).thenReturn("$2a$10$hashedpassword");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User u = invocation.getArgument(0);
            u.setId(UUID.randomUUID());
            return u;
        });
        when(jwtTokenProvider.generateToken("testuser")).thenReturn("jwt-token-123");
        when(jwtTokenProvider.getExpirationMs()).thenReturn(86400000L);

        // Act
        AuthResponse response = authService.register(validRegisterRequest);

        // Assert
        assertThat(response.getToken()).isEqualTo("jwt-token-123");
        assertThat(response.getTokenType()).isEqualTo("Bearer");
        assertThat(response.getUsername()).isEqualTo("testuser");
        assertThat(response.getEmail()).isEqualTo("test@example.com");
        assertThat(response.getRole()).isEqualTo("USER");
        assertThat(response.getExpiresIn()).isEqualTo(86400000L);

        // Verify the user was persisted with BCrypt-hashed password
        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());
        User savedUser = userCaptor.getValue();
        assertThat(savedUser.getPasswordHash()).isEqualTo("$2a$10$hashedpassword");
        assertThat(savedUser.getRole()).isEqualTo(User.Role.USER);
        assertThat(savedUser.isOnlineStatus()).isTrue();
    }

    @Test
    @DisplayName("register — duplicate username → throws DuplicateResourceException")
    void register_duplicateUsername_throws() {
        when(userRepository.existsByUsername("testuser")).thenReturn(true);

        assertThatThrownBy(() -> authService.register(validRegisterRequest))
                .isInstanceOf(DuplicateResourceException.class)
                .hasMessageContaining("Username already taken");

        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("register — duplicate email → throws DuplicateResourceException")
    void register_duplicateEmail_throws() {
        when(userRepository.existsByUsername("testuser")).thenReturn(false);
        when(userRepository.existsByEmail("test@example.com")).thenReturn(true);

        assertThatThrownBy(() -> authService.register(validRegisterRequest))
                .isInstanceOf(DuplicateResourceException.class)
                .hasMessageContaining("Email already registered");

        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("register — password mismatch → throws IllegalArgumentException")
    void register_passwordMismatch_throws() {
        RegisterRequest mismatchRequest = RegisterRequest.builder()
                .username("testuser")
                .email("test@example.com")
                .password("SecurePass1!")
                .confirmPassword("DifferentPass2@")
                .build();

        assertThatThrownBy(() -> authService.register(mismatchRequest))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Passwords do not match");

        verify(userRepository, never()).save(any());
    }

    // ======================== Login Tests ========================

    @Test
    @DisplayName("login — valid credentials → AuthenticationManager called, JWT returned")
    void login_success() {
        // Arrange
        Authentication mockAuth = mock(Authentication.class);
        when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
                .thenReturn(mockAuth);
        when(jwtTokenProvider.generateToken(mockAuth)).thenReturn("jwt-login-token");
        when(userRepository.findByUsername("testuser")).thenReturn(Optional.of(testUser));
        when(jwtTokenProvider.getExpirationMs()).thenReturn(86400000L);

        // Act
        AuthResponse response = authService.login(validLoginRequest);

        // Assert
        assertThat(response.getToken()).isEqualTo("jwt-login-token");
        assertThat(response.getUsername()).isEqualTo("testuser");
        assertThat(response.getRole()).isEqualTo("USER");

        verify(authenticationManager).authenticate(any(UsernamePasswordAuthenticationToken.class));
    }
}
