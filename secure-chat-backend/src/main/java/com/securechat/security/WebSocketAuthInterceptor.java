package com.securechat.security;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.NonNull;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.stereotype.Component;

import java.security.Principal;
import java.util.List;

/**
 * STOMP-level channel interceptor for WebSocket connection authentication.
 *
 * <p>Intercepts the initial STOMP CONNECT frame and validates the JWT token
 * provided in the {@code Authorization} native header. If valid, the user
 * principal is attached to the STOMP session, making it available in all
 * subsequent message mappings via {@code @MessageMapping}.
 *
 * <p>If the token is missing or invalid, the connection is rejected by
 * throwing an {@code IllegalArgumentException}, which Spring translates
 * into a STOMP ERROR frame sent back to the client.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class WebSocketAuthInterceptor implements ChannelInterceptor {

    private final JwtTokenProvider jwtTokenProvider;
    private final UserDetailsService userDetailsService;

    /**
     * Intercepts messages before they are sent to the channel.
     * Only processes STOMP CONNECT commands for authentication.
     */
    @Override
    public Message<?> preSend(@NonNull Message<?> message, @NonNull MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(
                message, StompHeaderAccessor.class
        );

        if (accessor == null) {
            return message;
        }

        // Only authenticate on the initial CONNECT frame
        if (StompCommand.CONNECT.equals(accessor.getCommand())) {
            authenticateConnection(accessor);
        }

        return message;
    }

    /**
     * Extracts and validates the JWT from STOMP native headers,
     * then sets the authenticated principal on the session.
     *
     * @param accessor the STOMP header accessor for the CONNECT frame
     * @throws IllegalArgumentException if authentication fails (triggers STOMP ERROR)
     */
    private void authenticateConnection(StompHeaderAccessor accessor) {
        // The client sends the token as a STOMP native header:
        // CONNECT
        // Authorization: Bearer <token>
        List<String> authHeaders = accessor.getNativeHeader("Authorization");

        if (authHeaders == null || authHeaders.isEmpty()) {
            log.warn("WebSocket CONNECT rejected: missing Authorization header");
            throw new IllegalArgumentException("Missing Authorization header in STOMP CONNECT");
        }

        String bearerToken = authHeaders.getFirst();

        if (bearerToken == null || !bearerToken.startsWith("Bearer ")) {
            log.warn("WebSocket CONNECT rejected: malformed Authorization header");
            throw new IllegalArgumentException("Malformed Authorization header — expected 'Bearer <token>'");
        }

        String token = bearerToken.substring(7);

        if (!jwtTokenProvider.validateToken(token)) {
            log.warn("WebSocket CONNECT rejected: invalid or expired JWT");
            throw new IllegalArgumentException("Invalid or expired JWT token");
        }

        // Token is valid — attach the user principal to the STOMP session
        String username = jwtTokenProvider.getUsernameFromToken(token);
        UserDetails userDetails = userDetailsService.loadUserByUsername(username);

        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(
                        userDetails,
                        null,
                        userDetails.getAuthorities()
                );

        accessor.setUser(authentication);

        log.info("WebSocket CONNECT authenticated for user: {}", username);
    }
}
