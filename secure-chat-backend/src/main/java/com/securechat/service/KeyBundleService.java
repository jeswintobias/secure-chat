package com.securechat.service;

import com.securechat.entity.Conversation;
import com.securechat.entity.ConversationKeyBundle;
import com.securechat.entity.User;
import com.securechat.entity.UserKeyBundle;
import com.securechat.exception.ResourceNotFoundException;
import com.securechat.repository.ConversationKeyBundleRepository;
import com.securechat.repository.ConversationRepository;
import com.securechat.repository.UserKeyBundleRepository;
import com.securechat.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Service layer for E2EE key bundle management.
 *
 * <p>Handles:
 * <ul>
 *   <li>User public key upload and retrieval</li>
 *   <li>Conversation (group) encrypted key bundle CRUD</li>
 *   <li>Membership validation before serving key material</li>
 * </ul>
 *
 * <p>The server never sees private keys or plaintext message content.
 * It acts as a relay for public keys and wrapped (encrypted) group keys.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class KeyBundleService {

    private final UserKeyBundleRepository userKeyBundleRepository;
    private final ConversationKeyBundleRepository conversationKeyBundleRepository;
    private final UserRepository userRepository;
    private final ConversationRepository conversationRepository;

    // ======================== User Key Bundle Operations ========================

    /**
     * Uploads or updates the authenticated user's ECDH public key.
     *
     * @param username  the authenticated user's username
     * @param publicKey the Base64-encoded public key in JWK format
     * @return the saved UserKeyBundle
     */
    @Transactional
    public UserKeyBundle uploadPublicKey(String username, String publicKey) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + username));

        UserKeyBundle bundle = userKeyBundleRepository.findByUserId(user.getId())
                .orElse(UserKeyBundle.builder()
                        .userId(user.getId())
                        .build());

        bundle.setPublicKey(publicKey);
        UserKeyBundle saved = userKeyBundleRepository.save(bundle);
        log.info("Public key uploaded for user '{}'", username);
        return saved;
    }

    /**
     * Retrieves a user's public key by user ID.
     *
     * @param userId the target user's UUID
     * @return the user's public key bundle
     * @throws ResourceNotFoundException if no key bundle exists
     */
    @Transactional(readOnly = true)
    public UserKeyBundle getUserPublicKey(UUID userId) {
        return userKeyBundleRepository.findByUserId(userId)
                .orElseThrow(() ->
                        new ResourceNotFoundException("No key bundle found for user: " + userId));
    }

    /**
     * Retrieves public keys for all members of a conversation.
     * Validates that the requesting user is a member of the conversation.
     *
     * @param conversationId the conversation UUID
     * @param username       the authenticated user's username
     * @return map of userId → public key (JWK string)
     */
    @Transactional(readOnly = true)
    public Map<UUID, String> getConversationMemberKeys(UUID conversationId, String username) {
        Conversation conversation = conversationRepository.findById(conversationId)
                .orElseThrow(() ->
                        new ResourceNotFoundException("Conversation not found: " + conversationId));

        // Verify the caller is a member
        User caller = userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + username));

        boolean isMember = conversation.getMembers().stream()
                .anyMatch(m -> m.getId().equals(caller.getId()));
        if (!isMember) {
            throw new SecurityException("User is not a member of this conversation");
        }

        List<UUID> memberIds = conversation.getMembers().stream()
                .map(User::getId)
                .collect(Collectors.toList());

        return userKeyBundleRepository.findAllByUserIdIn(memberIds).stream()
                .collect(Collectors.toMap(
                        UserKeyBundle::getUserId,
                        UserKeyBundle::getPublicKey
                ));
    }

    // ======================== Conversation Key Bundle Operations ========================

    /**
     * Retrieves the authenticated user's encrypted group key for a conversation.
     *
     * @param conversationId the conversation UUID
     * @param username       the authenticated user's username
     * @return the encrypted key bundle
     */
    @Transactional(readOnly = true)
    public ConversationKeyBundle getMyConversationKeyBundle(UUID conversationId, String username) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + username));

        return conversationKeyBundleRepository
                .findByConversationIdAndUserId(conversationId, user.getId())
                .orElseThrow(() ->
                        new ResourceNotFoundException(
                                "No key bundle found for user " + username +
                                " in conversation " + conversationId));
    }

    /**
     * Uploads encrypted group key bundles for all specified members.
     * Typically called by the group creator or an existing member adding a new member.
     *
     * @param conversationId the conversation UUID
     * @param username       the authenticated user performing the upload
     * @param bundles        map of userId → encrypted key (Base64)
     */
    @Transactional
    public void uploadConversationKeyBundles(
            UUID conversationId,
            String username,
            Map<UUID, String> bundles
    ) {
        Conversation conversation = conversationRepository.findById(conversationId)
                .orElseThrow(() ->
                        new ResourceNotFoundException("Conversation not found: " + conversationId));

        // Verify the caller is a member
        User caller = userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + username));

        boolean isMember = conversation.getMembers().stream()
                .anyMatch(m -> m.getId().equals(caller.getId()));
        if (!isMember) {
            throw new SecurityException("User is not a member of this conversation");
        }

        // Determine the current max key version
        List<ConversationKeyBundle> existing =
                conversationKeyBundleRepository.findAllByConversationId(conversationId);
        int nextVersion = existing.stream()
                .mapToInt(ConversationKeyBundle::getKeyVersion)
                .max()
                .orElse(0) + 1;

        for (Map.Entry<UUID, String> entry : bundles.entrySet()) {
            UUID memberId = entry.getKey();
            String encryptedKey = entry.getValue();

            ConversationKeyBundle bundle = conversationKeyBundleRepository
                    .findByConversationIdAndUserId(conversationId, memberId)
                    .orElse(ConversationKeyBundle.builder()
                            .conversationId(conversationId)
                            .userId(memberId)
                            .build());

            bundle.setEncryptedKey(encryptedKey);
            bundle.setKeyVersion(nextVersion);
            conversationKeyBundleRepository.save(bundle);
        }

        log.info("Uploaded {} key bundles for conversation {} (version {})",
                bundles.size(), conversationId, nextVersion);
    }

    /**
     * Checks whether a user has uploaded their public key.
     *
     * @param userId the user's UUID
     * @return true if a key bundle exists
     */
    @Transactional(readOnly = true)
    public boolean hasPublicKey(UUID userId) {
        return userKeyBundleRepository.findByUserId(userId).isPresent();
    }
}
