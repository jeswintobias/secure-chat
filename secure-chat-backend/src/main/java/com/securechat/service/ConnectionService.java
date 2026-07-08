package com.securechat.service;

import com.securechat.dto.response.ConnectionRequestResponse;
import com.securechat.entity.ConnectionRequest;
import com.securechat.entity.ConnectionRequest.RequestStatus;
import com.securechat.entity.Conversation;
import com.securechat.entity.Conversation.ConversationType;
import com.securechat.entity.User;
import com.securechat.exception.DuplicateResourceException;
import com.securechat.exception.ResourceNotFoundException;
import com.securechat.repository.ConnectionRequestRepository;
import com.securechat.repository.ConversationRepository;
import com.securechat.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Service layer for connection (friend) request management.
 *
 * <p>Handles sending, accepting, and rejecting connection requests.
 * When a request is accepted, a PRIVATE conversation is automatically
 * created between the two users.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ConnectionService {

    private final ConnectionRequestRepository connectionRequestRepository;
    private final ConversationRepository conversationRepository;
    private final UserRepository userRepository;

    /**
     * Sends a connection request from the sender to the target user.
     *
     * <p>Validates:
     * <ul>
     *   <li>Target user exists</li>
     *   <li>Sender is not sending to themselves</li>
     *   <li>No existing request between the two users (in either direction)</li>
     *   <li>No existing private conversation between them</li>
     * </ul>
     *
     * @param senderUsername the username of the sender
     * @param targetUsername the username of the target user
     * @return the created connection request as a DTO
     */
    @Transactional
    public ConnectionRequestResponse sendRequest(String senderUsername, String targetUsername) {
        User sender = userRepository.findByUsername(senderUsername)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + senderUsername));

        User receiver = userRepository.findByUsername(targetUsername)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + targetUsername));

        // Cannot send to yourself
        if (sender.getId().equals(receiver.getId())) {
            throw new IllegalArgumentException("Cannot send a connection request to yourself");
        }

        // Check for existing request in either direction
        Optional<ConnectionRequest> existing = connectionRequestRepository
                .findExistingBetween(sender.getId(), receiver.getId());

        if (existing.isPresent()) {
            ConnectionRequest req = existing.get();
            if (req.getStatus() == RequestStatus.PENDING) {
                throw new DuplicateResourceException("A pending connection request already exists between these users");
            }
            if (req.getStatus() == RequestStatus.ACCEPTED) {
                throw new DuplicateResourceException("You are already connected with this user");
            }
            // If REJECTED, allow re-sending by updating the existing record
            req.setStatus(RequestStatus.PENDING);
            req.setSender(sender);
            req.setReceiver(receiver);
            ConnectionRequest saved = connectionRequestRepository.save(req);
            log.info("Connection request re-sent from '{}' to '{}'", senderUsername, targetUsername);
            return toResponse(saved, null);
        }

        // Check for existing private conversation
        Optional<Conversation> existingConversation = conversationRepository
                .findPrivateConversation(sender.getId(), receiver.getId());
        if (existingConversation.isPresent()) {
            throw new DuplicateResourceException("A private conversation already exists with this user");
        }

        // Create the request
        ConnectionRequest request = ConnectionRequest.builder()
                .sender(sender)
                .receiver(receiver)
                .status(RequestStatus.PENDING)
                .build();

        ConnectionRequest saved = connectionRequestRepository.save(request);
        log.info("Connection request sent from '{}' to '{}'", senderUsername, targetUsername);

        return toResponse(saved, null);
    }

    /**
     * Accepts a pending connection request. Creates a PRIVATE conversation
     * between the two users and adds both as members.
     *
     * @param requestId       the connection request ID
     * @param receiverUsername the username of the receiver (must match the request)
     * @return the accepted request as a DTO, including the new conversation ID
     */
    @Transactional
    public ConnectionRequestResponse acceptRequest(UUID requestId, String receiverUsername) {
        ConnectionRequest request = connectionRequestRepository.findById(requestId)
                .orElseThrow(() -> new ResourceNotFoundException("Connection request not found: " + requestId));

        // Only the receiver can accept
        if (!request.getReceiver().getUsername().equals(receiverUsername)) {
            throw new SecurityException("Only the recipient can accept this request");
        }

        if (request.getStatus() != RequestStatus.PENDING) {
            throw new IllegalStateException("Request is no longer pending (status: " + request.getStatus() + ")");
        }

        // Mark as accepted
        request.setStatus(RequestStatus.ACCEPTED);
        connectionRequestRepository.save(request);

        // Create a PRIVATE conversation between the two users
        Conversation privateChat = Conversation.builder()
                .type(ConversationType.PRIVATE)
                .name(null) // Private chats don't have a name
                .build();

        privateChat.getMembers().add(request.getSender());
        privateChat.getMembers().add(request.getReceiver());

        Conversation savedConversation = conversationRepository.save(privateChat);

        log.info("Connection accepted: '{}' ↔ '{}', conversation created: {}",
                request.getSender().getUsername(),
                request.getReceiver().getUsername(),
                savedConversation.getId());

        return toResponse(request, savedConversation.getId());
    }

    /**
     * Rejects a pending connection request.
     *
     * @param requestId       the connection request ID
     * @param receiverUsername the username of the receiver (must match the request)
     * @return the rejected request as a DTO
     */
    @Transactional
    public ConnectionRequestResponse rejectRequest(UUID requestId, String receiverUsername) {
        ConnectionRequest request = connectionRequestRepository.findById(requestId)
                .orElseThrow(() -> new ResourceNotFoundException("Connection request not found: " + requestId));

        // Only the receiver can reject
        if (!request.getReceiver().getUsername().equals(receiverUsername)) {
            throw new SecurityException("Only the recipient can reject this request");
        }

        if (request.getStatus() != RequestStatus.PENDING) {
            throw new IllegalStateException("Request is no longer pending (status: " + request.getStatus() + ")");
        }

        request.setStatus(RequestStatus.REJECTED);
        connectionRequestRepository.save(request);

        log.info("Connection rejected: '{}' rejected request from '{}'",
                receiverUsername, request.getSender().getUsername());

        return toResponse(request, null);
    }

    /**
     * Returns all pending connection requests received by the given user.
     *
     * @param username the receiver's username
     * @return list of pending connection request DTOs
     */
    @Transactional(readOnly = true)
    public List<ConnectionRequestResponse> getPendingRequests(String username) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + username));

        return connectionRequestRepository
                .findByReceiverIdAndStatusOrderByCreatedAtDesc(user.getId(), RequestStatus.PENDING)
                .stream()
                .map(req -> toResponse(req, null))
                .collect(Collectors.toList());
    }

    /**
     * Returns all connection requests sent by the given user.
     *
     * @param username the sender's username
     * @return list of sent connection request DTOs
     */
    @Transactional(readOnly = true)
    public List<ConnectionRequestResponse> getSentRequests(String username) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + username));

        return connectionRequestRepository
                .findBySenderIdOrderByCreatedAtDesc(user.getId())
                .stream()
                .map(req -> toResponse(req, null))
                .collect(Collectors.toList());
    }

    // ======================== Entity-to-DTO Conversion ========================

    private ConnectionRequestResponse toResponse(ConnectionRequest request, UUID conversationId) {
        return ConnectionRequestResponse.builder()
                .id(request.getId())
                .senderId(request.getSender().getId())
                .senderUsername(request.getSender().getUsername())
                .receiverId(request.getReceiver().getId())
                .receiverUsername(request.getReceiver().getUsername())
                .status(request.getStatus().name())
                .conversationId(conversationId)
                .createdAt(request.getCreatedAt())
                .updatedAt(request.getUpdatedAt())
                .build();
    }
}
