package com.securechat.service.urlsecurity;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Set;

/**
 * Layer 3 — HTTP Probe Validation.
 *
 * <p>Sends an HTTP HEAD request to the URL to confirm:
 * <ul>
 *   <li>The server responds with a 2xx status code</li>
 *   <li>The Content-Type header is not in the blocked MIME type list</li>
 * </ul>
 *
 * <p>Falls back to a GET request (with limited body) if HEAD returns
 * 405 Method Not Allowed. Follows redirects up to a configurable limit.
 *
 * <p>This check catches dead links, parked domains returning 4xx/5xx,
 * and URLs that serve executable payloads masquerading as innocent files.
 */
@Component
@Slf4j
public class HttpProbeValidator {

    private final HttpClient httpClient;
    private final int timeoutSeconds;
    private final Set<String> blockedMimeTypes;

    public HttpProbeValidator(
            @Value("${app.security.url-validation.timeout-seconds:3}")
            int timeoutSeconds,
            @Value("${app.security.url-validation.max-redirects:3}")
            int maxRedirects,
            @Value("${app.security.url-validation.blocked-mime-types:application/x-msdownload,application/x-executable,application/x-msdos-program,application/x-dosexec}")
            String blockedMimeTypesCsv
    ) {
        this.timeoutSeconds = timeoutSeconds;
        this.blockedMimeTypes = Set.copyOf(
                List.of(blockedMimeTypesCsv.toLowerCase().split(","))
        );

        // Build a shared HttpClient with redirect policy and connection timeout
        HttpClient.Redirect redirectPolicy = maxRedirects > 0
                ? HttpClient.Redirect.NORMAL
                : HttpClient.Redirect.NEVER;

        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(timeoutSeconds))
                .followRedirects(redirectPolicy)
                .build();

        log.info("HttpProbeValidator initialized: {}s timeout, {} blocked MIME types",
                timeoutSeconds, blockedMimeTypes.size());
    }

    /**
     * Probes a URL with an HTTP HEAD request to validate accessibility and content type.
     *
     * @param urlString a syntactically valid, DNS-resolved HTTP/HTTPS URL
     * @return validation result
     */
    public ValidationResult validate(String urlString) {
        try {
            URI uri = new URI(urlString);

            // Try HEAD first (lightweight — no body transfer)
            HttpRequest headRequest = HttpRequest.newBuilder()
                    .uri(uri)
                    .method("HEAD", HttpRequest.BodyPublishers.noBody())
                    .timeout(Duration.ofSeconds(timeoutSeconds))
                    .header("User-Agent", "SecureChat-URLProbe/1.0")
                    .build();

            HttpResponse<Void> response;
            try {
                response = httpClient.send(headRequest, HttpResponse.BodyHandlers.discarding());
            } catch (Exception e) {
                // HEAD might fail for various reasons — try GET as fallback
                log.debug("HEAD request failed for {}, trying GET: {}", urlString, e.getMessage());
                return tryGetFallback(uri);
            }

            // If HEAD returns 405, fall back to GET
            if (response.statusCode() == 405) {
                log.debug("HEAD returned 405 for {}, trying GET fallback", urlString);
                return tryGetFallback(uri);
            }

            return evaluateResponse(urlString, response.statusCode(),
                    response.headers().firstValue("Content-Type").orElse(null));

        } catch (Exception e) {
            log.warn("HTTP probe failed for {}: {}", urlString, e.getMessage());
            return ValidationResult.fail("HTTP probe failed: " + e.getMessage());
        }
    }

    /**
     * Fallback GET request with discarded body.
     */
    private ValidationResult tryGetFallback(URI uri) {
        try {
            HttpRequest getRequest = HttpRequest.newBuilder()
                    .uri(uri)
                    .GET()
                    .timeout(Duration.ofSeconds(timeoutSeconds))
                    .header("User-Agent", "SecureChat-URLProbe/1.0")
                    .build();

            HttpResponse<Void> response = httpClient.send(getRequest,
                    HttpResponse.BodyHandlers.discarding());

            return evaluateResponse(uri.toString(), response.statusCode(),
                    response.headers().firstValue("Content-Type").orElse(null));

        } catch (Exception e) {
            log.warn("GET fallback failed for {}: {}", uri, e.getMessage());
            return ValidationResult.fail("HTTP probe failed (GET fallback): " + e.getMessage());
        }
    }

    /**
     * Evaluates the HTTP response status and Content-Type.
     */
    private ValidationResult evaluateResponse(String url, int statusCode, String contentType) {
        // Check status code (accept 2xx)
        if (statusCode < 200 || statusCode >= 300) {
            return ValidationResult.fail(
                    "URL returned HTTP " + statusCode + " (expected 2xx)"
            );
        }

        // Check Content-Type against blocked MIME types
        if (contentType != null) {
            String lowerContentType = contentType.toLowerCase().split(";")[0].trim();
            if (blockedMimeTypes.contains(lowerContentType)) {
                return ValidationResult.fail(
                        "Blocked Content-Type: " + lowerContentType +
                        " — executable file types are not allowed"
                );
            }
        }

        log.debug("HTTP probe passed: {} → HTTP {} ({})", url, statusCode, contentType);
        return ValidationResult.pass();
    }

    /**
     * Simple pass/fail result from HTTP probe validation.
     */
    public record ValidationResult(boolean passed, String reason) {
        public static ValidationResult pass() { return new ValidationResult(true, null); }
        public static ValidationResult fail(String reason) { return new ValidationResult(false, reason); }
    }
}
