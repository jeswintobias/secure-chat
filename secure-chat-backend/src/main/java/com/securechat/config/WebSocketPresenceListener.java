package com.securechat.config;

import com.securechat.dto.websocket.PresencePayload;
import com.securechat.entity.User;
import com.securechat.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionConnectedEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import java.security.Principal;

/**
 * Listens for WebSocket session lifecycle events to track user online/offline status.
 *
 * <p>When a STOMP session is established ({@link SessionConnectedEvent}), the user's
 * {@code onlineStatus} is set to {@code true} and persisted. When the session ends
 * ({@link SessionDisconnectEvent}), it is set to {@code false}.
 *
 * <p>Each status change is broadcast to {@code /topic/presence} so all connected
 * clients can update their UI in real time.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class WebSocketPresenceListener {

    private final UserRepository userRepository;
    private final SimpMessagingTemplate messagingTemplate;

    /**
     * Fired after a STOMP CONNECTED frame is successfully sent back to the client.
     * Sets the user's online status to {@code true}.
     */
    @EventListener
    public void handleSessionConnected(SessionConnectedEvent event) {
        Principal principal = event.getUser();
        if (principal == null) return;

        String username = principal.getName();
        updateOnlineStatus(username, true);
        log.info("User '{}' is now ONLINE", username);
    }

    /**
     * Fired when a STOMP session disconnects (browser close, network drop, logout).
     * Sets the user's online status to {@code false}.
     */
    @EventListener
    public void handleSessionDisconnect(SessionDisconnectEvent event) {
        Principal principal = event.getUser();
        if (principal == null) return;

        String username = principal.getName();
        updateOnlineStatus(username, false);
        log.info("User '{}' is now OFFLINE", username);
    }

    /**
     * Persists the online status change and broadcasts it to all connected clients.
     */
    private void updateOnlineStatus(String username, boolean online) {
        userRepository.findByUsername(username).ifPresent(user -> {
            user.setOnlineStatus(online);
            userRepository.save(user);

            PresencePayload payload = PresencePayload.builder()
                    .username(username)
                    .online(online)
                    .build();

            messagingTemplate.convertAndSend("/topic/presence", payload);
        });
    }
}
