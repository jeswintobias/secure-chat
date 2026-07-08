package com.securechat.controller;

import com.securechat.dto.response.ConnectionRequestResponse;
import com.securechat.service.ConnectionService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * REST controller for connection (friend) request management.
 *
 * <p>Endpoints:
 * <ul>
 *   <li>{@code POST /api/connections/send} — Send a connection request</li>
 *   <li>{@code POST /api/connections/{id}/accept} — Accept a pending request</li>
 *   <li>{@code POST /api/connections/{id}/reject} — Reject a pending request</li>
 *   <li>{@code GET  /api/connections/pending} — List incoming pending requests</li>
 *   <li>{@code GET  /api/connections/sent} — List sent requests</li>
 * </ul>
 *
 * All endpoints require authentication.
 */
@RestController
@RequestMapping("/api/connections")
@RequiredArgsConstructor
public class ConnectionController {

    private final ConnectionService connectionService;
    private final SimpMessagingTemplate messagingTemplate;

    /**
     * Sends a connection request to another user.
     *
     * @param body      JSON body containing "targetUsername"
     * @param principal the authenticated sender
     * @return 201 Created with the connection request DTO
     */
    @PostMapping("/send")
    public ResponseEntity<ConnectionRequestResponse> sendRequest(
            @RequestBody Map<String, String> body,
            Principal principal
    ) {
        String targetUsername = body.get("targetUsername");
        if (targetUsername == null || targetUsername.isBlank()) {
            throw new IllegalArgumentException("targetUsername is required");
        }

        ConnectionRequestResponse response = connectionService.sendRequest(
                principal.getName(), targetUsername.trim()
        );

        // Send real-time WebSocket notification to the receiver
        messagingTemplate.convertAndSendToUser(
                response.getReceiverUsername(),
                "/queue/connection-requests",
                response
        );

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * Accepts a pending connection request.
     *
     * @param requestId the connection request ID
     * @param principal the authenticated receiver
     * @return 200 OK with the updated request DTO (includes conversationId)
     */
    @PostMapping("/{requestId}/accept")
    public ResponseEntity<ConnectionRequestResponse> acceptRequest(
            @PathVariable UUID requestId,
            Principal principal
    ) {
        ConnectionRequestResponse response = connectionService.acceptRequest(requestId, principal.getName());

        // Notify the sender that their request was accepted
        messagingTemplate.convertAndSendToUser(
                response.getSenderUsername(),
                "/queue/connection-requests",
                response
        );

        return ResponseEntity.ok(response);
    }

    /**
     * Rejects a pending connection request.
     *
     * @param requestId the connection request ID
     * @param principal the authenticated receiver
     * @return 200 OK with the updated request DTO
     */
    @PostMapping("/{requestId}/reject")
    public ResponseEntity<ConnectionRequestResponse> rejectRequest(
            @PathVariable UUID requestId,
            Principal principal
    ) {
        ConnectionRequestResponse response = connectionService.rejectRequest(requestId, principal.getName());
        return ResponseEntity.ok(response);
    }

    /**
     * Returns all pending incoming connection requests for the authenticated user.
     *
     * @param principal the authenticated user
     * @return 200 OK with list of pending request DTOs
     */
    @GetMapping("/pending")
    public ResponseEntity<List<ConnectionRequestResponse>> getPendingRequests(Principal principal) {
        List<ConnectionRequestResponse> pending = connectionService.getPendingRequests(principal.getName());
        return ResponseEntity.ok(pending);
    }

    /**
     * Returns all connection requests sent by the authenticated user.
     *
     * @param principal the authenticated user
     * @return 200 OK with list of sent request DTOs
     */
    @GetMapping("/sent")
    public ResponseEntity<List<ConnectionRequestResponse>> getSentRequests(Principal principal) {
        List<ConnectionRequestResponse> sent = connectionService.getSentRequests(principal.getName());
        return ResponseEntity.ok(sent);
    }
}
