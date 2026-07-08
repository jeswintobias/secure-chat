package com.securechat.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.MalformedJwtException;
import io.jsonwebtoken.UnsupportedJwtException;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import io.jsonwebtoken.security.SecurityException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.util.Date;

/**
 * Centralized JWT token provider responsible for:
 * <ul>
 *   <li>Generating signed JWT tokens with user claims</li>
 *   <li>Extracting and validating claims from incoming tokens</li>
 *   <li>Token expiration verification</li>
 * </ul>
 *
 * Uses HMAC-SHA256 signing. The secret key is loaded from the
 * {@code app.jwt.secret} property (Base64-encoded, env-var backed).
 */
@Component
@Slf4j
public class JwtTokenProvider {

    private final SecretKey signingKey;
    private final long expirationMs;

    /**
     * Constructor-injected configuration — no @Autowired field injection.
     *
     * @param jwtSecret      Base64-encoded HMAC-SHA256 secret
     * @param expirationMs   Token lifetime in milliseconds
     */
    public JwtTokenProvider(
            @Value("${app.jwt.secret}") String jwtSecret,
            @Value("${app.jwt.expiration-ms}") long expirationMs
    ) {
        // Decode the Base64-encoded secret into a SecretKey for HMAC-SHA256 signing
        this.signingKey = Keys.hmacShaKeyFor(Decoders.BASE64.decode(jwtSecret));
        this.expirationMs = expirationMs;
    }

    /**
     * Generates a JWT token from a Spring Security Authentication object.
     *
     * @param authentication the authenticated principal
     * @return a signed JWT string
     */
    public String generateToken(Authentication authentication) {
        UserDetails userDetails = (UserDetails) authentication.getPrincipal();
        return generateToken(userDetails.getUsername());
    }

    /**
     * Generates a JWT token for a given username.
     *
     * @param username the subject claim
     * @return a signed JWT string
     */
    public String generateToken(String username) {
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + expirationMs);

        return Jwts.builder()
                .subject(username)
                .issuedAt(now)
                .expiration(expiryDate)
                .signWith(signingKey)
                .compact();
    }

    /**
     * Extracts the username (subject claim) from a valid JWT.
     *
     * @param token the JWT string
     * @return the username embedded in the token
     */
    public String getUsernameFromToken(String token) {
        Claims claims = Jwts.parser()
                .verifyWith(signingKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
        return claims.getSubject();
    }

    /**
     * Validates the structural integrity, signature, and expiration of a JWT.
     *
     * @param token the JWT string to validate
     * @return true if the token is valid
     */
    public boolean validateToken(String token) {
        try {
            Jwts.parser()
                    .verifyWith(signingKey)
                    .build()
                    .parseSignedClaims(token);
            return true;
        } catch (SecurityException e) {
            log.error("Invalid JWT signature: {}", e.getMessage());
        } catch (MalformedJwtException e) {
            log.error("Malformed JWT token: {}", e.getMessage());
        } catch (ExpiredJwtException e) {
            log.error("Expired JWT token: {}", e.getMessage());
        } catch (UnsupportedJwtException e) {
            log.error("Unsupported JWT token: {}", e.getMessage());
        } catch (IllegalArgumentException e) {
            log.error("JWT claims string is empty: {}", e.getMessage());
        }
        return false;
    }

    /**
     * Returns the configured token expiration in milliseconds.
     */
    public long getExpirationMs() {
        return expirationMs;
    }
}
