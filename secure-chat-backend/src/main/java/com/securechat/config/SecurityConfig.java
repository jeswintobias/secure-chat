package com.securechat.config;

import com.securechat.security.JwtAuthenticationFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;

/**
 * Central Spring Security configuration.
 *
 * <p>Key design decisions:
 * <ul>
 *   <li>Stateless session management — no server-side HTTP sessions.</li>
 *   <li>CSRF disabled because the API is token-authenticated (not cookie-based).</li>
 *   <li>JWT filter inserted before the standard UsernamePasswordAuthenticationFilter.</li>
 *   <li>Auth endpoints (/api/auth/**) and WebSocket endpoint (/ws/**) are publicly accessible.</li>
 * </ul>
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;

    @Value("${app.cors.allowed-origins}")
    private String allowedOrigins;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                // Disable CSRF — API uses JWT bearer tokens, not session cookies
                .csrf(AbstractHttpConfigurer::disable)

                // Enable CORS with the configured origins
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))

                // Stateless session — JWT replaces server-side session state
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS)
                )

                // Return 401 Unauthorized instead of default 403 Forbidden on auth failures
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint((request, response, authException) ->
                                response.sendError(jakarta.servlet.http.HttpServletResponse.SC_UNAUTHORIZED, authException.getMessage())
                        )
                )

                // Endpoint authorization rules
                .authorizeHttpRequests(auth -> auth
                        // Public: authentication endpoints
                        .requestMatchers("/api/auth/**").permitAll()

                        // Public: file retrieval endpoints (filenames are unguessable UUIDs)
                        .requestMatchers("/api/upload/files/**").permitAll()

                        // Public: WebSocket handshake endpoint (auth happens at STOMP level)
                        .requestMatchers("/ws/**").permitAll()

                        // Public: health check / actuator (if added later)
                        .requestMatchers("/actuator/health").permitAll()

                        // Preflight OPTIONS requests must be allowed for CORS
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()

                        // Everything else requires authentication
                        .anyRequest().authenticated()
                )

                // Insert the JWT filter before Spring's default username/password filter
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    /**
     * Exposes the AuthenticationManager bean for use in the AuthService
     * (needed for programmatic authentication during login).
     */
    @Bean
    public AuthenticationManager authenticationManager(
            AuthenticationConfiguration authenticationConfiguration
    ) throws Exception {
        return authenticationConfiguration.getAuthenticationManager();
    }

    /**
     * BCrypt password encoder with default strength (10 rounds).
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /**
     * CORS configuration allowing the Angular dev server and
     * any additional origins specified in application.yml.
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        if ("*".equals(allowedOrigins.trim())) {
            // Wildcard dev mode — accept any origin (patterns API supports credentials)
            configuration.setAllowedOriginPatterns(List.of("*"));
        } else {
            configuration.setAllowedOrigins(
                    Arrays.asList(allowedOrigins.split(","))
            );
        }
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("*"));
        configuration.setAllowCredentials(true);
        configuration.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
