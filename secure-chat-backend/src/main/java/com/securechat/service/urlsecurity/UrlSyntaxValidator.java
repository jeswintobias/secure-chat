package com.securechat.service.urlsecurity;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Set;

/**
 * Layer 1 — URL Syntax Validation.
 *
 * <p>Validates the structural correctness of a URL:
 * <ul>
 *   <li>Must parse as a valid {@link URI}</li>
 *   <li>Must use HTTP or HTTPS protocol only (blocks javascript:, data:, ftp:, etc.)</li>
 *   <li>Must have a non-empty host</li>
 *   <li>File extension must not be in the blocked list (e.g., .exe, .bat, .ps1)</li>
 * </ul>
 *
 * <p>This is a fast, zero-network-call check that runs first to reject
 * obviously malformed or dangerous URLs before more expensive checks.
 */
@Component
@Slf4j
public class UrlSyntaxValidator {

    private static final Set<String> ALLOWED_SCHEMES = Set.of("http", "https");

    private final Set<String> blockedExtensions;

    public UrlSyntaxValidator(
            @Value("${app.security.url-validation.blocked-extensions:.exe,.bat,.cmd,.scr,.msi,.ps1,.vbs,.dll,.com,.jar,.sh}")
            String blockedExtensionsCsv
    ) {
        this.blockedExtensions = Set.copyOf(
                List.of(blockedExtensionsCsv.toLowerCase().split(","))
        );
        log.info("UrlSyntaxValidator initialized with {} blocked extensions", blockedExtensions.size());
    }

    /**
     * Validates a single URL string.
     *
     * @param urlString the raw URL extracted from message content
     * @return a {@link ValidationResult} with pass/fail and reason
     */
    public ValidationResult validate(String urlString) {
        // 1. Parse as URI
        URI uri;
        try {
            uri = new URI(urlString);
        } catch (URISyntaxException e) {
            return ValidationResult.fail("Malformed URL syntax: " + e.getMessage());
        }

        // 2. Scheme must be http or https
        String scheme = uri.getScheme();
        if (scheme == null || !ALLOWED_SCHEMES.contains(scheme.toLowerCase())) {
            return ValidationResult.fail(
                    "Blocked protocol: " + (scheme != null ? scheme : "none") +
                    " — only HTTP/HTTPS allowed"
            );
        }

        // 3. Must have a host
        String host = uri.getHost();
        if (host == null || host.isBlank()) {
            return ValidationResult.fail("URL has no host");
        }

        // 4. Check file extension against blocked list
        String path = uri.getPath();
        if (path != null && !path.isBlank()) {
            String lowerPath = path.toLowerCase();
            int lastDot = lowerPath.lastIndexOf('.');
            if (lastDot >= 0) {
                String extension = lowerPath.substring(lastDot);
                if (blockedExtensions.contains(extension)) {
                    return ValidationResult.fail(
                            "Blocked file extension: " + extension +
                            " — executable/script file types are not allowed"
                    );
                }
            }
        }

        return ValidationResult.pass();
    }

    /**
     * Simple pass/fail result from syntax validation.
     */
    public record ValidationResult(boolean passed, String reason) {
        public static ValidationResult pass() { return new ValidationResult(true, null); }
        public static ValidationResult fail(String reason) { return new ValidationResult(false, reason); }
    }
}
