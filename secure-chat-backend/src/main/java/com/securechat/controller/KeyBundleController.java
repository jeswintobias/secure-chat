package com.securechat.controller;

import com.securechat.entity.ConversationKeyBundle;
import com.securechat.entity.UserKeyBundle;
import com.securechat.service.KeyBundleService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.Map;
import java.util.UUID;

/**
 * REST controller for E2EE key bundle management.
 *
 * <p>Endpoints:
 * <ul>
 *   <li>{@code PUT  /api/keys/me} — Upload/update the current user's ECDH public key</li>
 *   <li>{@code GET  /api/keys/user/{userId}} — Get a specific user's public key</li>
 *   <li>{@code GET  /api/keys/conversation/{id}} — Get all members' public keys</li>
 *   <li>{@code GET  /api/keys/conversation/{id}/bundle} — Get my encrypted group key</li>
 *   <li>{@code PUT  /api/keys/conversation/{id}/bundle} — Upload group key bundles</li>
 * </ul>
 *
 * <p>All endpoints require authentication.
 */
@RestController
@RequestMapping("/api/keys")
@RequiredArgsConstructor
public class KeyBundleController {

    private final KeyBundleService keyBundleService;

    /**
     * Uploads or updates the authenticated user's ECDH public key.
     *
     * @param body      JSON body containing "publicKey" (Base64-encoded JWK)
     * @param principal the authenticated user
     * @return 200 OK with the saved key bundle details
     */
    @PutMapping("/me")
    public ResponseEntity<Map<String, Object>> uploadPublicKey(
            @RequestBody Map<String, String> body,
            Principal principal
    ) {
        String publicKey = body.get("publicKey");
        if (publicKey == null || publicKey.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "publicKey is required"));
        }

        UserKeyBundle saved = keyBundleService.uploadPublicKey(principal.getName(), publicKey);
        return ResponseEntity.ok(Map.of(
                "userId", saved.getUserId(),
                "keyAlgorithm", saved.getKeyAlgorithm(),
                "createdAt", saved.getCreatedAt().toString()
        ));
    }

    /**
     * Returns a specific user's ECDH public key.
     *
     * @param userId the target user's UUID
     * @return 200 OK with the public key (JWK format)
     */
    @GetMapping("/user/{userId}")
    public ResponseEntity<Map<String, Object>> getUserPublicKey(@PathVariable UUID userId) {
        UserKeyBundle bundle = keyBundleService.getUserPublicKey(userId);
        return ResponseEntity.ok(Map.of(
                "userId", bundle.getUserId(),
                "publicKey", bundle.getPublicKey(),
                "keyAlgorithm", bundle.getKeyAlgorithm()
        ));
    }

    /**
     * Returns public keys for all members of a conversation.
     * The caller must be a member of the conversation.
     *
     * @param conversationId the conversation UUID
     * @param principal      the authenticated user
     * @return 200 OK with map of userId → public key (JWK)
     */
    @GetMapping("/conversation/{conversationId}")
    public ResponseEntity<Map<UUID, String>> getConversationMemberKeys(
            @PathVariable UUID conversationId,
            Principal principal
    ) {
        Map<UUID, String> keys = keyBundleService.getConversationMemberKeys(
                conversationId, principal.getName()
        );
        return ResponseEntity.ok(keys);
    }

    /**
     * Returns the authenticated user's encrypted group key for a conversation.
     *
     * @param conversationId the conversation UUID
     * @param principal      the authenticated user
     * @return 200 OK with the encrypted key and version
     */
    @GetMapping("/conversation/{conversationId}/bundle")
    public ResponseEntity<Map<String, Object>> getMyKeyBundle(
            @PathVariable UUID conversationId,
            Principal principal
    ) {
        ConversationKeyBundle bundle = keyBundleService.getMyConversationKeyBundle(
                conversationId, principal.getName()
        );
        return ResponseEntity.ok(Map.of(
                "conversationId", bundle.getConversationId(),
                "encryptedKey", bundle.getEncryptedKey(),
                "keyVersion", bundle.getKeyVersion()
        ));
    }

    /**
     * Uploads encrypted group key bundles for members of a conversation.
     * The caller must be a member of the conversation.
     *
     * <p>Request body: map of userId (UUID string) → encrypted key (Base64).
     *
     * @param conversationId the conversation UUID
     * @param bundles        map of member userId → wrapped key
     * @param principal      the authenticated user
     * @return 200 OK
     */
    @PutMapping("/conversation/{conversationId}/bundle")
    public ResponseEntity<Void> uploadKeyBundles(
            @PathVariable UUID conversationId,
            @RequestBody Map<UUID, String> bundles,
            Principal principal
    ) {
        keyBundleService.uploadConversationKeyBundles(
                conversationId, principal.getName(), bundles
        );
        return ResponseEntity.ok().build();
    }
}
