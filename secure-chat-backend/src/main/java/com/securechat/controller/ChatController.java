package com.securechat.controller;

import com.securechat.dto.request.MessageEditRequest;
import com.securechat.dto.request.ReactionRequest;
import com.securechat.dto.response.MessageReadDto;
import com.securechat.dto.response.MessageResponse;
import com.securechat.dto.response.ReactionResponse;
import com.securechat.dto.websocket.MessageDeletePayload;
import com.securechat.dto.websocket.MessageEditPayload;
import com.securechat.dto.websocket.MessageReadPayload;
import com.securechat.dto.websocket.ReactionPayload;
import com.securechat.dto.websocket.TypingIndicatorPayload;
import com.securechat.dto.websocket.WebSocketMessagePayload;
import com.securechat.entity.User;
import com.securechat.exception.ResourceNotFoundException;
import com.securechat.repository.UserRepository;
import com.securechat.service.MessageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;
import java.util.List;
import java.util.UUID;

/**
 * Combined REST + WebSocket controller for chat messaging.
 *
 * <p><b>WebSocket (STOMP) message flow:</b>
 * <ol>
 *   <li>Client sends a message to {@code /app/chat.send/{conversationId}}</li>
 *   <li>This controller receives it via {@code @MessageMapping}</li>
 *   <li>Content is sanitized, persisted, and converted to a DTO</li>
 *   <li>The DTO is broadcast to {@code /topic/conversation/{conversationId}}</li>
 * </ol>
 *
 * <p><b>REST endpoints:</b>
 * <ul>
 *   <li>{@code GET /api/conversations/{id}/messages} — paginated history</li>
 *   <li>{@code GET /api/conversations/{id}/messages/pinned} — pinned messages</li>
 *   <li>{@code POST /api/messages/{id}/pin} — pin a message (ADMIN only)</li>
 *   <li>{@code POST /api/messages/{id}/unpin} — unpin a message (ADMIN only)</li>
 *   <li>{@code PUT /api/messages/{id}} — edit a message (sender only, 20-min window)</li>
 *   <li>{@code DELETE /api/messages/{id}} — delete a message</li>
 *   <li>{@code POST /api/messages/{id}/react} — toggle reaction</li>
 * </ul>
 */
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@Slf4j
public class ChatController {

    private final MessageService messageService;
    private final SimpMessagingTemplate messagingTemplate;
    private final UserRepository userRepository;

    // ======================== WebSocket (STOMP) Endpoints ========================

    /**
     * Handles incoming chat messages over STOMP WebSocket.
     *
     * <p>The authenticated user's principal (set by {@link com.securechat.security.WebSocketAuthInterceptor})
     * is used to identify the sender — the client cannot spoof the sender identity.
     *
     * @param conversationId the target conversation (from the destination path)
     * @param payload        the message content, optional expiry, and optional attachment
     * @param principal      the authenticated user (from the STOMP session)
     */
    @MessageMapping("/chat.send/{conversationId}")
    public void sendMessage(
            @DestinationVariable UUID conversationId,
            @Payload WebSocketMessagePayload payload,
            Principal principal
    ) {
        String senderUsername = principal.getName();
        log.debug("WebSocket message from {} to conversation {}", senderUsername, conversationId);

        // Persist the message (XSS sanitization happens inside the service for non-encrypted messages)
        MessageResponse messageResponse = messageService.sendMessage(
                conversationId,
                senderUsername,
                payload.getContent(),
                payload.getExpiryMinutes(),
                payload.getAttachmentUrl(),
                payload.getAttachmentType(),
                payload.getOriginalName(),
                payload.isEncrypted(),
                payload.getIv()
        );

        // Broadcast the sanitized message to all subscribers of this conversation
        messagingTemplate.convertAndSend(
                "/topic/conversation/" + conversationId,
                messageResponse
        );
    }

    /**
     * Handles typing indicator events over STOMP WebSocket.
     *
     * <p>Broadcasts a typing status to all subscribers of the conversation.
     * This is a fire-and-forget event — no persistence needed.
     *
     * @param conversationId the conversation where the user is typing
     * @param principal      the authenticated user
     */
    @MessageMapping("/chat.typing/{conversationId}")
    public void handleTyping(
            @DestinationVariable UUID conversationId,
            @Payload TypingIndicatorPayload payload,
            Principal principal
    ) {
        // Override the username from the payload with the authenticated principal
        // to prevent spoofing of typing indicators
        payload.setUsername(principal.getName());
        payload.setConversationId(conversationId);

        messagingTemplate.convertAndSend(
                "/topic/conversation/" + conversationId + "/typing",
                payload
        );
    }

    /**
     * Handles read receipt events over STOMP WebSocket.
     *
     * @param conversationId the conversation where the message was read
     * @param payload        the read receipt payload
     * @param principal      the authenticated user
     */
    @MessageMapping("/chat.read/{conversationId}")
    public void handleReadReceipt(
            @DestinationVariable UUID conversationId,
            @Payload MessageReadPayload payload,
            Principal principal
    ) {
        String username = principal.getName();
        log.debug("User {} read message {} in conversation {}", username, payload.getMessageId(), conversationId);

        MessageReadDto readDto = messageService.markAsRead(payload.getMessageId(), username);
        
        if (readDto != null) {
            payload.setUserId(readDto.getUserId());
            payload.setUsername(readDto.getUsername());
            payload.setReadAt(readDto.getReadAt());
            payload.setConversationId(conversationId);

            messagingTemplate.convertAndSend(
                    "/topic/conversation/" + conversationId + "/read",
                    payload
            );
        }
    }

    // ======================== REST Endpoints ========================

    /**
     * Retrieves paginated message history for a conversation.
     *
     * <p>Messages are ordered newest-first and automatically filtered
     * to exclude expired ephemeral messages.
     *
     * @param conversationId the conversation to query
     * @param page           zero-based page index (default: 0)
     * @param size           page size (default: 50, max practical: ~200)
     * @return a page of MessageResponse DTOs
     */
    @GetMapping("/conversations/{conversationId}/messages")
    public ResponseEntity<Page<MessageResponse>> getConversationHistory(
            @PathVariable UUID conversationId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size,
            Principal principal
    ) {
        String username = principal != null ? principal.getName() : null;
        Page<MessageResponse> messages = messageService.getConversationHistory(conversationId, page, size, username);
        return ResponseEntity.ok(messages);
    }

    /**
     * Marks all unread messages in a conversation as read by the authenticated user.
     * Broadcasts the read receipts via WebSocket.
     *
     * @param conversationId the conversation
     * @param principal      the authenticated user
     * @return 200 OK
     */
    @PostMapping("/conversations/{conversationId}/read")
    public ResponseEntity<Void> markConversationAsRead(
            @PathVariable UUID conversationId,
            Principal principal
    ) {
        String username = principal.getName();
        List<MessageReadDto> newReads = messageService.markConversationAsRead(conversationId, username);

        // Broadcast each new read receipt via WebSocket
        for (MessageReadDto readDto : newReads) {
            MessageReadPayload payload = MessageReadPayload.builder()
                    .conversationId(conversationId)
                    .userId(readDto.getUserId())
                    .username(readDto.getUsername())
                    .readAt(readDto.getReadAt())
                    .build();
            
            messagingTemplate.convertAndSend(
                    "/topic/conversation/" + conversationId + "/read",
                    payload
            );
        }

        return ResponseEntity.ok().build();
    }

    /**
     * Retrieves all pinned messages for a conversation.
     *
     * @param conversationId the conversation to query
     * @return list of pinned MessageResponse DTOs
     */
    @GetMapping("/conversations/{conversationId}/messages/pinned")
    public ResponseEntity<List<MessageResponse>> getPinnedMessages(
            @PathVariable UUID conversationId
    ) {
        List<MessageResponse> pinned = messageService.getPinnedMessages(conversationId);
        return ResponseEntity.ok(pinned);
    }

    /**
     * Pins a message. Only users with ADMIN role can pin messages.
     *
     * @param messageId the message to pin
     * @param principal the authenticated user
     * @return the updated MessageResponse
     */
    @PostMapping("/messages/{messageId}/pin")
    public ResponseEntity<MessageResponse> pinMessage(
            @PathVariable UUID messageId,
            Principal principal
    ) {
        enforceAdminRole(principal.getName());
        MessageResponse response = messageService.pinMessage(messageId, principal.getName());
        return ResponseEntity.ok(response);
    }

    /**
     * Unpins a message. Only users with ADMIN role can unpin messages.
     *
     * @param messageId the message to unpin
     * @param principal the authenticated user
     * @return the updated MessageResponse
     */
    @PostMapping("/messages/{messageId}/unpin")
    public ResponseEntity<MessageResponse> unpinMessage(
            @PathVariable UUID messageId,
            Principal principal
    ) {
        enforceAdminRole(principal.getName());
        MessageResponse response = messageService.unpinMessage(messageId);
        return ResponseEntity.ok(response);
    }

    // ======================== Message Edit (WebSocket + REST) ========================

    /**
     * Handles message edit events over STOMP WebSocket.
     * Broadcasts the edited message to all subscribers of the conversation.
     *
     * @param conversationId the conversation containing the message
     * @param payload        the edit payload (messageId, new content, encryption metadata)
     * @param principal      the authenticated user
     */
    @MessageMapping("/chat.edit/{conversationId}")
    public void editMessage(
            @DestinationVariable UUID conversationId,
            @Payload MessageEditPayload payload,
            Principal principal
    ) {
        String username = principal.getName();
        log.debug("Edit request from {} for message {} in conversation {}",
                username, payload.getMessageId(), conversationId);

        MessageResponse response = messageService.editMessage(
                payload.getMessageId(), username,
                payload.getContent(), payload.isEncrypted(), payload.getIv());

        messagingTemplate.convertAndSend(
                "/topic/conversation/" + conversationId + "/edit",
                response);
    }

    /**
     * Edits a message via REST (for retry/offline scenarios).
     *
     * @param messageId the message to edit
     * @param request   the edit request body
     * @param principal the authenticated user
     * @return the updated MessageResponse
     */
    @PutMapping("/messages/{messageId}")
    public ResponseEntity<MessageResponse> editMessageRest(
            @PathVariable UUID messageId,
            @RequestBody MessageEditRequest request,
            Principal principal
    ) {
        MessageResponse response = messageService.editMessage(
                messageId, principal.getName(),
                request.getContent(), request.isEncrypted(), request.getIv());
        return ResponseEntity.ok(response);
    }

    // ======================== Message Delete (WebSocket + REST) ========================

    /**
     * Handles message delete events over STOMP WebSocket.
     * Supports two modes via payload.mode:
     * - "EVERYONE" = soft delete for all participants
     * - "ME" = hide only for the requesting user
     *
     * @param conversationId the conversation containing the message
     * @param payload        the delete payload (messageId, mode)
     * @param principal      the authenticated user
     */
    @MessageMapping("/chat.delete/{conversationId}")
    public void deleteMessage(
            @DestinationVariable UUID conversationId,
            @Payload MessageDeletePayload payload,
            Principal principal
    ) {
        String username = principal.getName();
        log.debug("Delete request from {} for message {} in conversation {} (mode: {})",
                username, payload.getMessageId(), conversationId, payload.getMode());

        if ("ME".equalsIgnoreCase(payload.getMode())) {
            // "Delete for Me" — hide only for the requesting user, no broadcast
            messageService.deleteMessageForMe(payload.getMessageId(), username);
        } else {
            // "Delete for Everyone" — soft delete and broadcast to all
            MessageResponse response = messageService.deleteMessage(
                    payload.getMessageId(), username);
            messagingTemplate.convertAndSend(
                    "/topic/conversation/" + conversationId + "/delete",
                    response);
        }
    }

    /**
     * Deletes a message via REST.
     *
     * @param messageId the message to delete
     * @param mode      deletion mode: "EVERYONE" or "ME" (default: EVERYONE)
     * @param principal the authenticated user
     * @return the updated MessageResponse (for EVERYONE mode) or 204 No Content (for ME mode)
     */
    @DeleteMapping("/messages/{messageId}")
    public ResponseEntity<MessageResponse> deleteMessageRest(
            @PathVariable UUID messageId,
            @RequestParam(defaultValue = "EVERYONE") String mode,
            Principal principal
    ) {
        if ("ME".equalsIgnoreCase(mode)) {
            messageService.deleteMessageForMe(messageId, principal.getName());
            return ResponseEntity.noContent().build();
        }
        MessageResponse response = messageService.deleteMessage(
                messageId, principal.getName());
        return ResponseEntity.ok(response);
    }

    // ======================== Message Reactions (WebSocket + REST) ========================

    /**
     * Handles reaction toggle events over STOMP WebSocket.
     * Broadcasts the reaction change to all subscribers.
     *
     * @param conversationId the conversation containing the message
     * @param payload        the reaction payload (messageId, emoji)
     * @param principal      the authenticated user
     */
    @MessageMapping("/chat.react/{conversationId}")
    public void reactToMessage(
            @DestinationVariable UUID conversationId,
            @Payload ReactionPayload payload,
            Principal principal
    ) {
        String username = principal.getName();
        log.debug("Reaction {} from {} on message {} in conversation {}",
                payload.getEmoji(), username, payload.getMessageId(), conversationId);

        ReactionResponse response = messageService.toggleReaction(
                payload.getMessageId(), username, payload.getEmoji());

        messagingTemplate.convertAndSend(
                "/topic/conversation/" + conversationId + "/reaction",
                response);
    }

    /**
     * Toggles a reaction via REST.
     *
     * @param messageId the message to react to
     * @param request   the reaction request (emoji)
     * @param principal the authenticated user
     * @return the ReactionResponse indicating ADDED or REMOVED
     */
    @PostMapping("/messages/{messageId}/react")
    public ResponseEntity<ReactionResponse> reactToMessageRest(
            @PathVariable UUID messageId,
            @RequestBody ReactionRequest request,
            Principal principal
    ) {
        ReactionResponse response = messageService.toggleReaction(
                messageId, principal.getName(), request.getEmoji());
        return ResponseEntity.ok(response);
    }

    // ======================== Internal Helpers ========================

    /**
     * Enforces that the authenticated user has ADMIN role.
     * Throws an exception if the user is not an admin.
     */
    private void enforceAdminRole(String username) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + username));
        if (user.getRole() != User.Role.ADMIN) {
            throw new SecurityException("Only ADMIN users can pin/unpin messages");
        }
    }
}
