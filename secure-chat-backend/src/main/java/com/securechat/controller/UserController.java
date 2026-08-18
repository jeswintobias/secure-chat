package com.securechat.controller;

import com.securechat.dto.response.UserResponse;
import com.securechat.entity.User;
import com.securechat.exception.ResourceNotFoundException;
import com.securechat.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import com.securechat.dto.request.UpdateSettingsRequest;

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
     * Returns the online/offline status of all users who share
     * at least one conversation with the authenticated user.
     *
     * <p>Called once by the frontend on initial load to seed the
     * presence map. After that, real-time WebSocket events keep
     * the map up to date.
     *
     * @param principal the authenticated user
     * @return 200 OK with a Map of username → online (boolean)
     */
    @GetMapping("/presence")
    public ResponseEntity<java.util.Map<String, Boolean>> getContactsPresence(Principal principal) {
        User user = userRepository.findByUsername(principal.getName())
                .orElseThrow(() ->
                        new ResourceNotFoundException("User not found: " + principal.getName())
                );

        java.util.Map<String, Boolean> presenceMap = userRepository.findContactsByUserId(user.getId())
                .stream()
                .collect(Collectors.toMap(User::getUsername, User::isOnlineStatus));

        return ResponseEntity.ok(presenceMap);
    }

    /**
     * Updates privacy settings for the current user.
     */
    @PatchMapping("/me/settings")
    public ResponseEntity<UserResponse> updateSettings(
            @RequestBody UpdateSettingsRequest request,
            Principal principal
    ) {
        User user = userRepository.findByUsername(principal.getName())
                .orElseThrow(() ->
                        new ResourceNotFoundException("User not found")
                );

        if (request.getLastSeenPrivacy() != null) {
            user.setLastSeenPrivacy(User.PrivacySetting.valueOf(request.getLastSeenPrivacy().toUpperCase()));
        }
        if (request.getReadReceiptsEnabled() != null) {
            user.setReadReceiptsEnabled(request.getReadReceiptsEnabled());
        }

        userRepository.save(user);
        return ResponseEntity.ok(toUserResponse(user));
    }

    /**
     * Soft deletes the current user's account.
     */
    @DeleteMapping("/me")
    public ResponseEntity<Void> deleteAccount(Principal principal) {
        User user = userRepository.findByUsername(principal.getName())
                .orElseThrow(() ->
                        new ResourceNotFoundException("User not found")
                );

        user.setDeleted(true);
        // We also want to clear their email/password to truly "delete" them 
        // while preserving the id for orphaned messages, but let's stick to simple soft delete flag.
        userRepository.save(user);

        return ResponseEntity.noContent().build();
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
                .lastSeen(user.getLastSeen())
                .lastSeenPrivacy(user.getLastSeenPrivacy() != null ? user.getLastSeenPrivacy().name() : null)
                .readReceiptsEnabled(user.isReadReceiptsEnabled())
                .isDeleted(user.isDeleted())
                .createdAt(user.getCreatedAt())
                .build();
    }
}
