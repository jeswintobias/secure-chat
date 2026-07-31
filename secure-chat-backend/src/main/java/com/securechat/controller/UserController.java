package com.securechat.controller;

import com.securechat.dto.response.UserResponse;
import com.securechat.entity.User;
import com.securechat.exception.ResourceNotFoundException;
import com.securechat.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * REST controller for user profile operations.
 *
 * All endpoints require authentication.
 */
@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserRepository userRepository;

    /**
     * Returns the authenticated user's own profile.
     *
     * @param principal the authenticated user
     * @return 200 OK with UserResponse DTO
     */
    @GetMapping("/me")
    public ResponseEntity<UserResponse> getCurrentUser(Principal principal) {
        User user = userRepository.findByUsername(principal.getName())
                .orElseThrow(() ->
                        new ResourceNotFoundException("User not found: " + principal.getName())
                );
        return ResponseEntity.ok(toUserResponse(user));
    }

    /**
     * Searches users by partial username (prefix match, case-insensitive).
     *
     * <p>Returns up to 10 results, excluding the requesting user.
     * Uses a B-tree index with varchar_pattern_ops for efficient prefix lookups.
     *
     * @param query     the search prefix (minimum 1 character)
     * @param principal the authenticated user (excluded from results)
     * @return 200 OK with list of matching UserResponse DTOs
     */
    @GetMapping("/search")
    public ResponseEntity<List<UserResponse>> searchUsers(
            @RequestParam String query,
            Principal principal
    ) {
        if (query == null || query.trim().isEmpty()) {
            return ResponseEntity.ok(List.of());
        }

        String prefix = query.trim().toLowerCase();

        List<UserResponse> results = userRepository.searchByUsernamePrefix(prefix + "%")
                .stream()
                .filter(u -> !u.getUsername().equals(principal.getName())) // exclude self
                .map(this::toUserResponse)
                .collect(Collectors.toList());

        return ResponseEntity.ok(results);
    }

    /**
     * Returns a user's public profile by ID.
     *
     * @param userId the target user's UUID
     * @return 200 OK with UserResponse DTO (no sensitive data)
     */
    @GetMapping("/{userId}")
    public ResponseEntity<UserResponse> getUserById(@PathVariable UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() ->
                        new ResourceNotFoundException("User not found: " + userId)
                );
        return ResponseEntity.ok(toUserResponse(user));
    }

    /**
     * Entity-to-DTO conversion. Deliberately omits password hash and email.
     */
    private UserResponse toUserResponse(User user) {
        return UserResponse.builder()
                .id(user.getId())
                .username(user.getUsername())
                .role(user.getRole().name())
                .online(user.isOnlineStatus())
                .createdAt(user.getCreatedAt())
                .build();
    }
}
