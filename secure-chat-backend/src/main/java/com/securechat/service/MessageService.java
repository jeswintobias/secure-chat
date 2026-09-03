package com.securechat.service;

import com.securechat.dto.response.MessageReadDto;
import com.securechat.dto.response.MessageResponse;
import com.securechat.dto.response.ReactionResponse;
import com.securechat.dto.response.ReactionSummary;
import com.securechat.entity.ChatMessage;
import com.securechat.entity.MessageReaction;
import com.securechat.entity.MessageRead;
import com.securechat.entity.Conversation;
import com.securechat.entity.User;
import com.securechat.entity.UserDeletedMessage;
import com.securechat.exception.ResourceNotFoundException;
import com.securechat.exception.UnsafeUrlException;
import com.securechat.repository.ChatMessageRepository;
import com.securechat.repository.ConversationRepository;
import com.securechat.repository.MessageReactionRepository;
import com.securechat.repository.MessageReadRepository;
import com.securechat.repository.UserDeletedMessageRepository;
import com.securechat.repository.UserRepository;
import com.securechat.service.urlsecurity.UrlScanResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;


import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Service layer for chat message operations.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class MessageService {

    /** Whitelist of allowed reaction emojis. */
    private static final Set<String> ALLOWED_EMOJIS = Set.of(
            "\uD83D\uDC4D", "\u2764\uFE0F", "\uD83D\uDE02", "\uD83D\uDE2E",
            "\uD83D\uDE22", "\uD83D\uDE4F", "\uD83C\uDF89", "\uD83D\uDD25",
            "\uD83D\uDC4E", "\uD83E\uDD14"
    );

    private final ChatMessageRepository messageRepository;
    private final ConversationRepository conversationRepository;
    private final UserRepository userRepository;
    private final MessageReadRepository messageReadRepository;
    private final MessageReactionRepository reactionRepository;
    private final UserDeletedMessageRepository userDeletedMessageRepository;
    private final XssSanitizer xssSanitizer;
    private final UrlSecurityService urlSecurityService;

    /**
     * Persists a new message in a conversation after XSS sanitization.
     *
     * @param conversationId the target conversation
     * @param senderUsername the authenticated sender's username
     * @param content        the raw message content (will be sanitized)
     * @param expiryMinutes  optional ephemeral expiry (null or 0 = no expiry)
     * @return the created message as a DTO
     * @throws ResourceNotFoundException if the conversation or user is not found
     */
    @Transactional
    public MessageResponse sendMessage(
            UUID conversationId,
            String senderUsername,
            String content,
            Integer expiryMinutes
    ) {
        return sendMessage(conversationId, senderUsername, content, expiryMinutes, null, null, null, false, null);
    }

    /**
     * Persists a new message with optional attachment in a conversation.
     *
     * @param conversationId the target conversation
     * @param senderUsername the authenticated sender's username
     * @param content        the raw message content (will be sanitized)
     * @param expiryMinutes  optional ephemeral expiry (null or 0 = no expiry)
     * @param attachmentUrl  optional URL to an uploaded file
     * @param attachmentType optional MIME type of the attachment
     * @param originalName   optional original filename
     * @return the created message as a DTO
     */
    @Transactional
    public MessageResponse sendMessage(
            UUID conversationId,
            String senderUsername,
            String content,
            Integer expiryMinutes,
            String attachmentUrl,
            String attachmentType,
            String originalName
    ) {
        return sendMessage(conversationId, senderUsername, content, expiryMinutes,
                attachmentUrl, attachmentType, originalName, false, null);
    }

    /**
     * Persists a new message with optional attachment and optional E2EE encryption.
     *
     * <p>When {@code encrypted} is true, XSS sanitization and URL security scanning
     * are skipped because the content is ciphertext (Base64). Client-side sanitization
     * is mandatory after decryption.
     *
     * @param conversationId the target conversation
     * @param senderUsername the authenticated sender's username
     * @param content        the message content (plaintext or ciphertext)
     * @param expiryMinutes  optional ephemeral expiry (null or 0 = no expiry)
     * @param attachmentUrl  optional URL to an uploaded file
     * @param attachmentType optional MIME type of the attachment
     * @param originalName   optional original filename
     * @param encrypted      whether the content is client-side encrypted
     * @param iv             Base64-encoded AES-GCM initialization vector (required when encrypted=true)
     * @return the created message as a DTO
     */
    @Transactional
    public MessageResponse sendMessage(
            UUID conversationId,
            String senderUsername,
            String content,
            Integer expiryMinutes,
            String attachmentUrl,
            String attachmentType,
            String originalName,
            boolean encrypted,
            String iv
    ) {
        Conversation conversation = conversationRepository.findById(conversationId)
                .orElseThrow(() ->
                        new ResourceNotFoundException("Conversation not found: " + conversationId)
                );

        User sender = userRepository.findByUsername(senderUsername)
                .orElseThrow(() ->
                        new ResourceNotFoundException("User not found: " + senderUsername)
                );

        // Verify the sender is a member of the conversation
        boolean isMember = conversation.getMembers().stream()
                .anyMatch(m -> m.getId().equals(sender.getId()));
        if (!isMember) {
            throw new IllegalArgumentException(
                    "User " + senderUsername + " is not a member of conversation " + conversationId
            );
        }

        String sanitizedContent;

        if (encrypted) {
            // === E2EE: Skip XSS/URL scanning for encrypted content ===
            sanitizedContent = (content != null) ? content : "";
        } else {
            // === XSS SANITIZATION (plaintext messages only) ===
            sanitizedContent = (content != null && !content.isBlank())
                    ? xssSanitizer.sanitize(content)
                    : "";

            // === URL SECURITY PIPELINE (plaintext messages only) ===
            UrlScanResult contentScanResult = urlSecurityService.scanMessageContent(sanitizedContent);
            if (contentScanResult.getVerdict() == UrlScanResult.Verdict.BLOCKED) {
                throw new UnsafeUrlException(
                        "Message contains a blocked URL",
                        contentScanResult.getBlockedUrls().stream()
                                .map(f -> new UnsafeUrlException.BlockedUrlDetail(f.url(), f.reason()))
                                .toList()
                );
            }
            sanitizedContent = contentScanResult.getProcessedContent();
        }

        // Validate attachment URL if present
        if (attachmentUrl != null && !attachmentUrl.isBlank()) {
            UrlScanResult attachmentScanResult = urlSecurityService.validateSingleUrl(attachmentUrl);
            if (attachmentScanResult.getVerdict() == UrlScanResult.Verdict.BLOCKED) {
                throw new UnsafeUrlException(
                        "Attachment URL is blocked",
                        attachmentScanResult.getBlockedUrls().stream()
                                .map(f -> new UnsafeUrlException.BlockedUrlDetail(f.url(), f.reason()))
                                .toList()
                );
            }
        }

        // Compute expiry timestamp if ephemeral
        Instant expiresAt = null;
        if (expiryMinutes != null && expiryMinutes > 0) {
            expiresAt = Instant.now().plusSeconds(expiryMinutes * 60L);
        }

        // Determine message type from attachment
        ChatMessage.MessageType messageType = ChatMessage.MessageType.TEXT;
        if (attachmentUrl != null && !attachmentUrl.isBlank()) {
            if (attachmentType != null && attachmentType.startsWith("image/")) {
                messageType = ChatMessage.MessageType.IMAGE;
            } else if (attachmentType != null && attachmentType.startsWith("audio/")) {
                messageType = ChatMessage.MessageType.AUDIO;
            } else if (attachmentType != null && attachmentType.startsWith("video/")) {
                messageType = ChatMessage.MessageType.VIDEO;
            } else {
                messageType = ChatMessage.MessageType.FILE;
            }
        }

        ChatMessage message = ChatMessage.builder()
                .conversation(conversation)
                .sender(sender)
                .content(sanitizedContent)
                .messageType(messageType)
                .expiresAt(expiresAt)
                .attachmentUrl(attachmentUrl)
                .attachmentType(attachmentType)
                .originalName(originalName)
                .encrypted(encrypted)
                .iv(iv)
                .build();

        ChatMessage saved = messageRepository.save(message);
        log.debug("Message saved: {} in conversation {} (type: {})", saved.getId(), conversationId, messageType);

        return toMessageResponse(saved);
    }

    /**
     * Retrieves paginated, non-expired messages for a conversation.
     * Filters out messages that the requesting user has individually deleted ("Delete for Me").
     *
     * @param conversationId the conversation to query
     * @param page           zero-based page index
     * @param size           page size (max messages per page)
     * @return a page of MessageResponse DTOs
     */
    @Transactional(readOnly = true)
    public Page<MessageResponse> getConversationHistory(UUID conversationId, int page, int size) {
        return getConversationHistory(conversationId, page, size, null);
    }

    /**
     * Retrieves paginated, non-expired messages for a conversation.
     * Filters out messages that the requesting user has individually deleted ("Delete for Me").
     *
     * @param conversationId the conversation to query
     * @param page           zero-based page index
     * @param size           page size (max messages per page)
     * @param username       the requesting user's username (null = no per-user filtering)
     * @return a page of MessageResponse DTOs
     */
    @Transactional(readOnly = true)
    public Page<MessageResponse> getConversationHistory(UUID conversationId, int page, int size, String username) {
        if (!conversationRepository.existsById(conversationId)) {
            throw new ResourceNotFoundException("Conversation not found: " + conversationId);
        }

        Pageable pageable = PageRequest.of(page, size);
        Page<ChatMessage> messages = messageRepository.findActiveMessagesByConversationId(
                conversationId,
                Instant.now(),
                pageable
        );

        // Filter out messages the user has individually deleted ("Delete for Me")
        if (username != null) {
            User user = userRepository.findByUsername(username).orElse(null);
            if (user != null) {
                Set<UUID> deletedIds = userDeletedMessageRepository
                        .findDeletedMessageIdsByUserAndConversation(user.getId(), conversationId);
                if (!deletedIds.isEmpty()) {
                    List<MessageResponse> filtered = messages.getContent().stream()
                            .filter(msg -> !deletedIds.contains(msg.getId()))
                            .map(this::toMessageResponse)
                            .collect(Collectors.toList());
                    return new org.springframework.data.domain.PageImpl<>(
                            filtered, pageable, messages.getTotalElements());
                }
            }
        }

        return messages.map(this::toMessageResponse);
    }

    /**
     * Retrieves all pinned messages for a conversation.
     *
     * @param conversationId the conversation to query
     * @return list of pinned MessageResponse DTOs
     */
    @Transactional(readOnly = true)
    public List<MessageResponse> getPinnedMessages(UUID conversationId) {
        if (!conversationRepository.existsById(conversationId)) {
            throw new ResourceNotFoundException("Conversation not found: " + conversationId);
        }

        return messageRepository.findPinnedMessagesByConversationId(conversationId).stream()
                .map(this::toMessageResponse)
                .collect(Collectors.toList());
    }

    /**
     * Pins a message in its conversation. Only callable by ADMIN users.
     *
     * @param messageId the message to pin
     * @param username  the admin username performing the pin
     * @return the updated message as a DTO
     */
    @Transactional
    public MessageResponse pinMessage(UUID messageId, String username) {
        ChatMessage message = messageRepository.findById(messageId)
                .orElseThrow(() -> new ResourceNotFoundException("Message not found: " + messageId));

        message.setPinned(true);
        message.setPinnedBy(username);
        message.setPinnedAt(Instant.now());

        ChatMessage saved = messageRepository.save(message);
        log.info("Message {} pinned by {}", messageId, username);
        return toMessageResponse(saved);
    }

    /**
     * Unpins a message. Only callable by ADMIN users.
     *
     * @param messageId the message to unpin
     * @return the updated message as a DTO
     */
    @Transactional
    public MessageResponse unpinMessage(UUID messageId) {
        ChatMessage message = messageRepository.findById(messageId)
                .orElseThrow(() -> new ResourceNotFoundException("Message not found: " + messageId));

        message.setPinned(false);
        message.setPinnedBy(null);
        message.setPinnedAt(null);

        ChatMessage saved = messageRepository.save(message);
        log.info("Message {} unpinned", messageId);
        return toMessageResponse(saved);
    }

    /**
     * Scheduled task: purges expired ephemeral messages from the database.
     * Runs every 5 minutes to keep the messages table clean.
     */
    @Scheduled(fixedRate = 300_000) // 5 minutes
    @Transactional
    public void purgeExpiredMessages() {
        int deleted = messageRepository.deleteExpiredMessages(Instant.now());
        if (deleted > 0) {
            log.info("Purged {} expired messages", deleted);
        }
    }

    /**
     * Marks a message as read by the given user.
     *
     * @param messageId the message ID
     * @param username  the username of the reader
     * @return a MessageReadDto representing the read receipt
     */
    @Transactional
    public MessageReadDto markAsRead(UUID messageId, String username) {
        ChatMessage message = messageRepository.findById(messageId)
                .orElseThrow(() -> new ResourceNotFoundException("Message not found: " + messageId));

        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + username));

        if (!user.isReadReceiptsEnabled()) {
            return null;
        }

        if (!messageReadRepository.existsByMessageIdAndUserId(messageId, user.getId())) {
            MessageRead read = MessageRead.builder()
                    .message(message)
                    .user(user)
                    .build();
            messageReadRepository.save(read);

            return MessageReadDto.builder()
                    .userId(user.getId())
                    .username(user.getUsername())
                    .readAt(read.getReadAt())
                    .build();
        }

        return messageReadRepository.findByMessageId(messageId).stream()
                .filter(r -> r.getUser().getId().equals(user.getId()))
                .findFirst()
                .map(r -> MessageReadDto.builder()
                        .userId(r.getUser().getId())
                        .username(r.getUser().getUsername())
                        .readAt(r.getReadAt())
                        .build())
                .orElse(null);
    }

    /**
     * Bulk-marks all unread messages in a conversation as read by the given user.
     *
     * @param conversationId the conversation ID
     * @param username       the username of the reader
     * @return a list of new MessageReadDto receipts (for WebSocket broadcast)
     */
    @Transactional
    public List<MessageReadDto> markConversationAsRead(UUID conversationId, String username) {
        if (!conversationRepository.existsById(conversationId)) {
            throw new ResourceNotFoundException("Conversation not found: " + conversationId);
        }

        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + username));

        if (!user.isReadReceiptsEnabled()) {
            return List.of();
        }

        List<ChatMessage> unreadMessages = messageRepository.findUnreadMessagesByConversationIdAndUserId(
                conversationId, user.getId()
        );

        if (unreadMessages.isEmpty()) {
            return List.of();
        }

        Instant now = Instant.now();
        List<MessageRead> newReads = unreadMessages.stream()
                .map(msg -> MessageRead.builder()
                        .message(msg)
                        .user(user)
                        .readAt(now)
                        .build())
                .collect(Collectors.toList());

        messageReadRepository.saveAll(newReads);

        return newReads.stream()
                .map(r -> MessageReadDto.builder()
                        .userId(r.getUser().getId())
                        .username(r.getUser().getUsername())
                        .readAt(r.getReadAt())
                        .build())
                .collect(Collectors.toList());
    }

    // ════════════════════════════════════════════════════════════
    // Message Editing
    // ════════════════════════════════════════════════════════════

    /**
     * Edits an existing message's content. Sender-only authorization.
     *
     * <p>For encrypted messages, the client must re-encrypt the new content
     * with a fresh IV (AES-GCM IV reuse = catastrophic). The new IV replaces
     * the old one.
     *
     * @param messageId   the message to edit
     * @param username    the authenticated user's username
     * @param newContent  the new message content (plaintext or ciphertext)
     * @param encrypted   whether the new content is encrypted
     * @param iv          new Base64-encoded IV (required when encrypted=true)
     * @return the updated message as a DTO
     */
    @Transactional
    public MessageResponse editMessage(UUID messageId, String username,
                                       String newContent, boolean encrypted, String iv) {
        ChatMessage message = messageRepository.findById(messageId)
                .orElseThrow(() -> new ResourceNotFoundException("Message not found: " + messageId));

        // Authorization: only the sender can edit
        if (!message.getSender().getUsername().equals(username)) {
            throw new SecurityException("Only the sender can edit their message");
        }

        // Cannot edit deleted messages
        if (message.isDeleted()) {
            throw new IllegalArgumentException("Cannot edit a deleted message");
        }

        String sanitizedContent;
        if (encrypted) {
            // E2EE: skip sanitization for ciphertext
            sanitizedContent = (newContent != null) ? newContent : "";
        } else {
            // Plaintext: apply XSS sanitization + URL security scanning
            sanitizedContent = (newContent != null && !newContent.isBlank())
                    ? xssSanitizer.sanitize(newContent)
                    : "";

            UrlScanResult contentScanResult = urlSecurityService.scanMessageContent(sanitizedContent);
            if (contentScanResult.getVerdict() == UrlScanResult.Verdict.BLOCKED) {
                throw new UnsafeUrlException(
                        "Edited message contains a blocked URL",
                        contentScanResult.getBlockedUrls().stream()
                                .map(f -> new UnsafeUrlException.BlockedUrlDetail(f.url(), f.reason()))
                                .toList()
                );
            }
            sanitizedContent = contentScanResult.getProcessedContent();
        }

        message.setContent(sanitizedContent);
        message.setEdited(true);
        message.setEditedAt(Instant.now());

        // Update encryption metadata if re-encrypted
        if (encrypted) {
            message.setEncrypted(true);
            message.setIv(iv);  // Fresh IV — CRITICAL for AES-GCM security
        }

        ChatMessage saved = messageRepository.save(message);
        log.info("Message {} edited by {}", messageId, username);
        return toMessageResponse(saved);
    }

    // ════════════════════════════════════════════════════════════
    // Message Deletion (Soft Delete — "Delete for Everyone")
    // ════════════════════════════════════════════════════════════

    /**
     * Soft-deletes a message for all participants.
     * Wipes content and attachment data but preserves the row.
     *
     * @param messageId the message to delete
     * @param username  the authenticated user's username
     * @return the updated message as a DTO
     */
    @Transactional
    public MessageResponse deleteMessage(UUID messageId, String username) {
        ChatMessage message = messageRepository.findById(messageId)
                .orElseThrow(() -> new ResourceNotFoundException("Message not found: " + messageId));

        // Authorization: only the sender can delete
        if (!message.getSender().getUsername().equals(username)) {
            throw new SecurityException("Only the sender can delete their message");
        }

        // Already deleted — idempotent
        if (message.isDeleted()) {
            return toMessageResponse(message);
        }

        // Soft delete: wipe content, keep row
        message.setDeleted(true);
        message.setDeletedAt(Instant.now());
        message.setDeletedBy(username);
        message.setContent("");           // Wipe actual content
        message.setAttachmentUrl(null);
        message.setAttachmentType(null);
        message.setOriginalName(null);
        message.setEncrypted(false);
        message.setIv(null);

        ChatMessage saved = messageRepository.save(message);
        log.info("Message {} deleted by {}", messageId, username);
        return toMessageResponse(saved);
    }

    /**
     * Deletes a message for a single user only ("Delete for Me").
     * Inserts a row into the user_deleted_messages table so the message
     * is hidden from this user's view but remains visible to everyone else.
     *
     * @param messageId the message to hide
     * @param username  the authenticated user's username
     */
    @Transactional
    public void deleteMessageForMe(UUID messageId, String username) {
        if (!messageRepository.existsById(messageId)) {
            throw new ResourceNotFoundException("Message not found: " + messageId);
        }

        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + username));

        // Idempotent — don't insert if already deleted for this user
        if (userDeletedMessageRepository.existsByUserIdAndMessageId(user.getId(), messageId)) {
            return;
        }

        UserDeletedMessage deletion = UserDeletedMessage.builder()
                .userId(user.getId())
                .messageId(messageId)
                .build();
        userDeletedMessageRepository.save(deletion);
        log.info("Message {} deleted for user {}", messageId, username);
    }

    // ════════════════════════════════════════════════════════════
    // Message Reactions
    // ════════════════════════════════════════════════════════════

    /**
     * Toggles an emoji reaction on a message.
     * If the user already reacted with this emoji, the reaction is removed.
     * Otherwise, it is added.
     *
     * @param messageId the message to react to
     * @param username  the authenticated user's username
     * @param emoji     the emoji to toggle
     * @return a ReactionResponse indicating whether the reaction was ADDED or REMOVED
     */
    @Transactional
    public ReactionResponse toggleReaction(UUID messageId, String username, String emoji) {
        ChatMessage message = messageRepository.findById(messageId)
                .orElseThrow(() -> new ResourceNotFoundException("Message not found: " + messageId));

        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + username));

        // Cannot react to deleted messages
        if (message.isDeleted()) {
            throw new IllegalArgumentException("Cannot react to a deleted message");
        }

        // Validate emoji against whitelist
        if (!ALLOWED_EMOJIS.contains(emoji)) {
            throw new IllegalArgumentException("Invalid reaction emoji: " + emoji);
        }

        // Toggle: if reaction exists, remove it; otherwise, add it
        Optional<MessageReaction> existing = reactionRepository
                .findByMessageIdAndUserIdAndEmoji(messageId, user.getId(), emoji);

        if (existing.isPresent()) {
            reactionRepository.delete(existing.get());
            log.debug("Reaction {} removed from message {} by {}", emoji, messageId, username);
            return ReactionResponse.builder()
                    .messageId(messageId)
                    .username(username)
                    .userId(user.getId())
                    .emoji(emoji)
                    .action("REMOVED")
                    .build();
        } else {
            MessageReaction reaction = MessageReaction.builder()
                    .message(message)
                    .user(user)
                    .emoji(emoji)
                    .build();
            reactionRepository.save(reaction);
            log.debug("Reaction {} added to message {} by {}", emoji, messageId, username);
            return ReactionResponse.builder()
                    .messageId(messageId)
                    .username(username)
                    .userId(user.getId())
                    .emoji(emoji)
                    .action("ADDED")
                    .build();
        }
    }

    /**
     * Converts a ChatMessage entity to a MessageResponse DTO.
     * This is the ONLY place where entity-to-DTO mapping occurs for messages.
     *
     * @param message the entity to convert
     * @return the DTO representation
     */
    public MessageResponse toMessageResponse(ChatMessage message) {
        List<MessageReadDto> readReceipts = messageReadRepository.findByMessageId(message.getId())
                .stream()
                .map(r -> MessageReadDto.builder()
                        .userId(r.getUser().getId())
                        .username(r.getUser().getUsername())
                        .readAt(r.getReadAt())
                        .build())
                .collect(Collectors.toList());

        // Aggregate reactions by emoji
        List<ReactionSummary> reactions = reactionRepository
                .findByMessageIdOrderByCreatedAtAsc(message.getId())
                .stream()
                .collect(Collectors.groupingBy(MessageReaction::getEmoji))
                .entrySet().stream()
                .map(e -> ReactionSummary.builder()
                        .emoji(e.getKey())
                        .count(e.getValue().size())
                        .usernames(e.getValue().stream()
                                .map(r -> r.getUser().getUsername())
                                .toList())
                        .build())
                .toList();

        // For deleted messages, ensure content is empty (defense-in-depth)
        String content = message.isDeleted() ? "" : message.getContent();

        return MessageResponse.builder()
                .id(message.getId())
                .conversationId(message.getConversation().getId())
                .senderId(message.getSender().getId())
                .senderUsername(message.getSender().getUsername())
                .content(content)
                .messageType(message.getMessageType().name())
                .createdAt(message.getCreatedAt())
                .expiresAt(message.getExpiresAt())
                .attachmentUrl(message.isDeleted() ? null : message.getAttachmentUrl())
                .attachmentType(message.isDeleted() ? null : message.getAttachmentType())
                .originalName(message.isDeleted() ? null : message.getOriginalName())
                .pinned(message.isPinned())
                .pinnedBy(message.getPinnedBy())
                .pinnedAt(message.getPinnedAt())
                .encrypted(message.isDeleted() ? false : message.isEncrypted())
                .iv(message.isDeleted() ? null : message.getIv())
                .readReceipts(readReceipts)
                .edited(message.isEdited())
                .editedAt(message.getEditedAt())
                .deleted(message.isDeleted())
                .deletedAt(message.getDeletedAt())
                .deletedBy(message.getDeletedBy())
                .reactions(reactions)
                .build();
    }
}
