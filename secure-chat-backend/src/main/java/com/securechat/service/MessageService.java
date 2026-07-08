package com.securechat.service;

import com.securechat.dto.response.MessageReadDto;
import com.securechat.dto.response.MessageResponse;
import com.securechat.entity.ChatMessage;
import com.securechat.entity.MessageRead;
import com.securechat.entity.Conversation;
import com.securechat.entity.User;
import com.securechat.exception.ResourceNotFoundException;
import com.securechat.exception.UnsafeUrlException;
import com.securechat.repository.ChatMessageRepository;
import com.securechat.repository.ConversationRepository;
import com.securechat.repository.MessageReadRepository;
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
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Service layer for chat message operations.
 *
 * <p>Handles message creation (with XSS sanitization), paginated retrieval
 * with expiry filtering, and scheduled cleanup of expired ephemeral messages.
 *
 * <p>All methods accept and return DTOs — entity-to-DTO conversion is
 * handled internally by {@link #toMessageResponse(ChatMessage)}.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class MessageService {

    private final ChatMessageRepository messageRepository;
    private final ConversationRepository conversationRepository;
    private final UserRepository userRepository;
    private final MessageReadRepository messageReadRepository;
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
        return sendMessage(conversationId, senderUsername, content, expiryMinutes, null, null);
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
     * @return the created message as a DTO
     */
    @Transactional
    public MessageResponse sendMessage(
            UUID conversationId,
            String senderUsername,
            String content,
            Integer expiryMinutes,
            String attachmentUrl,
            String attachmentType
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

        // === XSS SANITIZATION ===
        String sanitizedContent = (content != null && !content.isBlank())
                ? xssSanitizer.sanitize(content)
                : "";

        // === URL SECURITY PIPELINE ===
        // Scan message content for dangerous/malicious URLs
        UrlScanResult contentScanResult = urlSecurityService.scanMessageContent(sanitizedContent);
        if (contentScanResult.getVerdict() == UrlScanResult.Verdict.BLOCKED) {
            throw new UnsafeUrlException(
                    "Message contains a blocked URL",
                    contentScanResult.getBlockedUrls().stream()
                            .map(f -> new UnsafeUrlException.BlockedUrlDetail(f.url(), f.reason()))
                            .toList()
            );
        }
        // Use processed content (may have warning annotations)
        sanitizedContent = contentScanResult.getProcessedContent();

        // Validate attachment URL if present (external URLs only — internal /api/upload/ paths are trusted)
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
                .build();

        ChatMessage saved = messageRepository.save(message);
        log.debug("Message saved: {} in conversation {} (type: {})", saved.getId(), conversationId, messageType);

        return toMessageResponse(saved);
    }

    /**
     * Retrieves paginated, non-expired messages for a conversation.
     *
     * @param conversationId the conversation to query
     * @param page           zero-based page index
     * @param size           page size (max messages per page)
     * @return a page of MessageResponse DTOs
     */
    @Transactional(readOnly = true)
    public Page<MessageResponse> getConversationHistory(UUID conversationId, int page, int size) {
        // Verify the conversation exists
        if (!conversationRepository.existsById(conversationId)) {
            throw new ResourceNotFoundException("Conversation not found: " + conversationId);
        }

        Pageable pageable = PageRequest.of(page, size);
        Page<ChatMessage> messages = messageRepository.findActiveMessagesByConversationId(
                conversationId,
                Instant.now(),
                pageable
        );

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

        // If already exists, return existing
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

        return MessageResponse.builder()
                .id(message.getId())
                .conversationId(message.getConversation().getId())
                .senderId(message.getSender().getId())
                .senderUsername(message.getSender().getUsername())
                .content(message.getContent())
                .messageType(message.getMessageType().name())
                .createdAt(message.getCreatedAt())
                .expiresAt(message.getExpiresAt())
                .attachmentUrl(message.getAttachmentUrl())
                .attachmentType(message.getAttachmentType())
                .pinned(message.isPinned())
                .pinnedBy(message.getPinnedBy())
                .pinnedAt(message.getPinnedAt())
                .readReceipts(readReceipts)
                .build();
    }
}
