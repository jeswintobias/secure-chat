package com.securechat.config;

import com.securechat.security.WebSocketAuthInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

/**
 * WebSocket configuration with STOMP protocol support.
 *
 * <p>Architecture:
 * <ul>
 *   <li><b>/ws</b> — STOMP endpoint with SockJS fallback for browsers without native WS.</li>
 *   <li><b>/app</b> — Application destination prefix for @MessageMapping methods.</li>
 *   <li><b>/topic</b> — Broker prefix for pub-sub broadcasts (group messages, typing).</li>
 *   <li><b>/queue</b> — Broker prefix for point-to-point delivery (private messages).</li>
 * </ul>
 *
 * <p>The {@link WebSocketAuthInterceptor} is registered on the inbound client
 * channel to validate JWT tokens during the STOMP CONNECT handshake.
 */
@Configuration
@EnableWebSocketMessageBroker
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final WebSocketAuthInterceptor webSocketAuthInterceptor;

    @Value("${app.websocket.allowed-origins}")
    private String allowedOrigins;

    /**
     * Configures the message broker for routing STOMP messages.
     *
     * - /topic: pub-sub broadcast (e.g., group chat messages)
     * - /queue: point-to-point user queues (e.g., private messages)
     * - /app: prefix for messages routed to @MessageMapping methods
     */
    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        // Enable a simple in-memory broker for /topic and /queue destinations
        registry.enableSimpleBroker("/topic", "/queue");

        // Messages sent to /app/... are routed to @MessageMapping controller methods
        registry.setApplicationDestinationPrefixes("/app");

        // Per-user destinations use /queue as the prefix (e.g., /queue/messages)
        registry.setUserDestinationPrefix("/user");
    }

    /**
     * Registers the STOMP WebSocket endpoint with SockJS fallback.
     *
     * Clients connect via: ws://host:port/ws (or http://host:port/ws via SockJS)
     */
    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        if ("*".equals(allowedOrigins.trim())) {
            // Wildcard dev mode — accept any origin (patterns API supports credentials)
            registry
                    .addEndpoint("/ws")
                    .setAllowedOriginPatterns("*")
                    .withSockJS();
        } else {
            registry
                    .addEndpoint("/ws")
                    .setAllowedOrigins(allowedOrigins.split(","))
                    .withSockJS();
        }
    }

    /**
     * Registers the JWT authentication interceptor on the inbound client channel.
     * This ensures all STOMP CONNECT frames are authenticated before the session
     * is established.
     */
    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(webSocketAuthInterceptor);
    }
}
