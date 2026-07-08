package com.securechat.service.urlsecurity;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;

/**
 * Layer 2 — DNS Lookup Validation.
 *
 * <p>Verifies that the domain in a URL actually resolves to a live IP address.
 * Also blocks URLs that resolve to private/loopback addresses to prevent
 * Server-Side Request Forgery (SSRF) attacks.
 *
 * <p>Blocked IP ranges (SSRF protection):
 * <ul>
 *   <li>127.0.0.0/8 (loopback)</li>
 *   <li>10.0.0.0/8 (private class A)</li>
 *   <li>172.16.0.0/12 (private class B)</li>
 *   <li>192.168.0.0/16 (private class C)</li>
 *   <li>::1 (IPv6 loopback)</li>
 *   <li>0.0.0.0 (unspecified)</li>
 *   <li>169.254.0.0/16 (link-local)</li>
 * </ul>
 */
@Component
@Slf4j
public class DnsLookupValidator {

    private final int timeoutSeconds;

    public DnsLookupValidator(
            @Value("${app.security.url-validation.timeout-seconds:3}")
            int timeoutSeconds
    ) {
        this.timeoutSeconds = timeoutSeconds;
        log.info("DnsLookupValidator initialized with {}s timeout", timeoutSeconds);
    }

    /**
     * Validates that the URL's domain resolves and does not point to a private IP.
     *
     * @param urlString a syntactically valid HTTP/HTTPS URL
     * @return validation result
     */
    public ValidationResult validate(String urlString) {
        String host;
        try {
            URI uri = new URI(urlString);
            host = uri.getHost();
            if (host == null || host.isBlank()) {
                return ValidationResult.fail("URL has no host for DNS lookup");
            }
        } catch (Exception e) {
            return ValidationResult.fail("Cannot parse URL for DNS lookup: " + e.getMessage());
        }

        // Resolve DNS
        InetAddress address;
        try {
            // InetAddress.getByName() performs a DNS lookup.
            // Note: Java's DNS resolution doesn't have a built-in timeout on the lookup itself,
            // but the system-level DNS resolver typically times out in 5-30 seconds.
            // We rely on the system timeout here; the HTTP probe layer provides its own timeout.
            address = InetAddress.getByName(host);
        } catch (UnknownHostException e) {
            return ValidationResult.fail("Domain does not resolve: " + host);
        }

        // SSRF protection: block private/loopback/link-local addresses
        if (isPrivateOrReserved(address)) {
            return ValidationResult.fail(
                    "Domain " + host + " resolves to private/reserved IP " + address.getHostAddress() +
                    " — possible SSRF attack"
            );
        }

        log.debug("DNS lookup passed: {} → {}", host, address.getHostAddress());
        return ValidationResult.pass();
    }

    /**
     * Checks if an IP address is private, loopback, link-local, or otherwise reserved.
     */
    private boolean isPrivateOrReserved(InetAddress address) {
        return address.isLoopbackAddress()      // 127.0.0.0/8, ::1
            || address.isSiteLocalAddress()      // 10.0.0.0/8, 172.16.0.0/12, 192.168.0.0/16
            || address.isLinkLocalAddress()      // 169.254.0.0/16
            || address.isAnyLocalAddress()        // 0.0.0.0, ::
            || address.isMulticastAddress();      // 224.0.0.0/4
    }

    /**
     * Simple pass/fail result from DNS validation.
     */
    public record ValidationResult(boolean passed, String reason) {
        public static ValidationResult pass() { return new ValidationResult(true, null); }
        public static ValidationResult fail(String reason) { return new ValidationResult(false, reason); }
    }
}
