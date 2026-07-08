package com.securechat.service;

import com.securechat.service.urlsecurity.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * URL Security Pipeline Orchestrator.
 *
 * <p>Extracts all URLs from message content and runs them through a
 * 4-layer validation pipeline:
 * <ol>
 *   <li><b>Syntax</b> — protocol, structure, blocked file extensions</li>
 *   <li><b>DNS</b> — domain resolution + SSRF protection</li>
 *   <li><b>HTTP Probe</b> — live server check + Content-Type validation</li>
 *   <li><b>Safe Browsing</b> — Google API malware/phishing screening</li>
 * </ol>
 *
 * <p>The pipeline is fail-fast for blocking checks (syntax, Safe Browsing)
 * and annotates content for soft failures (DNS, HTTP probe).
 *
 * <p>This service is injected into {@link MessageService} and runs after
 * XSS sanitization but before message persistence.
 */
@Service
@Slf4j
public class UrlSecurityService {

    /**
     * Regex to extract URLs from message text.
     * Matches http:// and https:// URLs, stopping at whitespace, angle brackets, or common punctuation.
     */
    private static final Pattern URL_PATTERN = Pattern.compile(
            "(https?://[^\\s<>\"'`,;\\)\\]]+)",
            Pattern.CASE_INSENSITIVE
    );

    private final boolean enabled;
    private final UrlSyntaxValidator syntaxValidator;
    private final DnsLookupValidator dnsValidator;
    private final HttpProbeValidator httpProbeValidator;
    private final SafeBrowsingValidator safeBrowsingValidator;

    public UrlSecurityService(
            @Value("${app.security.url-validation.enabled:true}")
            boolean enabled,
            UrlSyntaxValidator syntaxValidator,
            DnsLookupValidator dnsValidator,
            HttpProbeValidator httpProbeValidator,
            SafeBrowsingValidator safeBrowsingValidator
    ) {
        this.enabled = enabled;
        this.syntaxValidator = syntaxValidator;
        this.dnsValidator = dnsValidator;
        this.httpProbeValidator = httpProbeValidator;
        this.safeBrowsingValidator = safeBrowsingValidator;

        log.info("UrlSecurityService initialized (enabled: {})", enabled);
    }

    /**
     * Scans message content for URLs and validates each through the security pipeline.
     *
     * @param content the XSS-sanitized message content
     * @return a {@link UrlScanResult} with the verdict and optionally modified content
     */
    public UrlScanResult scanMessageContent(String content) {
        if (!enabled || content == null || content.isBlank()) {
            return UrlScanResult.builder()
                    .processedContent(content)
                    .build();
        }

        List<String> urls = extractUrls(content);
        if (urls.isEmpty()) {
            return UrlScanResult.builder()
                    .processedContent(content)
                    .build();
        }

        log.debug("URL security scan: found {} URL(s) in message", urls.size());

        UrlScanResult.Builder resultBuilder = UrlScanResult.builder();
        String processedContent = content;

        // ── Layer 1: Syntax Validation (blocking) ──
        for (String url : urls) {
            UrlSyntaxValidator.ValidationResult syntaxResult = syntaxValidator.validate(url);
            if (!syntaxResult.passed()) {
                resultBuilder.addBlocked(url, syntaxResult.reason());
                log.warn("URL blocked (syntax): {} — {}", url, syntaxResult.reason());
            }
        }

        // If any URL was blocked at syntax level, reject immediately
        UrlScanResult earlyCheck = resultBuilder.processedContent(processedContent).build();
        if (earlyCheck.getVerdict() == UrlScanResult.Verdict.BLOCKED) {
            return earlyCheck;
        }

        // ── Layer 2: DNS Lookup (warning) ──
        for (String url : urls) {
            DnsLookupValidator.ValidationResult dnsResult = dnsValidator.validate(url);
            if (!dnsResult.passed()) {
                // DNS failure for SSRF (private IP) is a blocking failure
                if (dnsResult.reason().contains("SSRF")) {
                    resultBuilder.addBlocked(url, dnsResult.reason());
                    log.warn("URL blocked (DNS/SSRF): {} — {}", url, dnsResult.reason());
                } else {
                    resultBuilder.addWarned(url, dnsResult.reason());
                    processedContent = annotateUrl(processedContent, url, "⚠️ [unverified link]");
                    log.info("URL warned (DNS): {} — {}", url, dnsResult.reason());
                }
            }
        }

        // Check again after DNS for SSRF blocks
        UrlScanResult dnsCheck = resultBuilder.processedContent(processedContent).build();
        if (dnsCheck.getVerdict() == UrlScanResult.Verdict.BLOCKED) {
            return dnsCheck;
        }

        // ── Layer 3: HTTP Probe (warning for non-SSRF URLs that passed DNS) ──
        final UrlScanResult dnsCheckFinal = dnsCheck;
        List<String> dnsPassedUrls = urls.stream()
                .filter(url -> !dnsCheckFinal.getWarnedUrls().stream()
                        .anyMatch(w -> w.url().equals(url)))
                .toList();

        for (String url : dnsPassedUrls) {
            HttpProbeValidator.ValidationResult probeResult = httpProbeValidator.validate(url);
            if (!probeResult.passed()) {
                // Blocked MIME type is a hard block
                if (probeResult.reason().contains("Blocked Content-Type")) {
                    resultBuilder.addBlocked(url, probeResult.reason());
                    log.warn("URL blocked (HTTP probe): {} — {}", url, probeResult.reason());
                } else {
                    resultBuilder.addWarned(url, probeResult.reason());
                    processedContent = annotateUrl(processedContent, url, "⚠️ [unverified link]");
                    log.info("URL warned (HTTP probe): {} — {}", url, probeResult.reason());
                }
            }
        }

        // Check again for HTTP probe blocks
        UrlScanResult probeCheck = resultBuilder.processedContent(processedContent).build();
        if (probeCheck.getVerdict() == UrlScanResult.Verdict.BLOCKED) {
            return probeCheck;
        }

        // ── Layer 4: Google Safe Browsing (blocking) ──
        final UrlScanResult probeCheckFinal = probeCheck;
        List<String> urlsForSafeBrowsing = urls.stream()
                .filter(url -> probeCheckFinal.getBlockedUrls().stream()
                        .noneMatch(b -> b.url().equals(url)))
                .toList();

        if (!urlsForSafeBrowsing.isEmpty()) {
            Map<String, String> threats = safeBrowsingValidator.checkUrls(urlsForSafeBrowsing);
            for (Map.Entry<String, String> entry : threats.entrySet()) {
                resultBuilder.addBlocked(entry.getKey(), entry.getValue());
                log.warn("URL blocked (Safe Browsing): {} — {}", entry.getKey(), entry.getValue());
            }
        }

        return resultBuilder.processedContent(processedContent).build();
    }

    /**
     * Validates a single URL (e.g., an attachmentUrl from file upload).
     * Only runs syntax + DNS checks (no HTTP probe or Safe Browsing for internal URLs).
     *
     * @param url the URL to validate
     * @return a {@link UrlScanResult}
     */
    public UrlScanResult validateSingleUrl(String url) {
        if (!enabled || url == null || url.isBlank()) {
            return UrlScanResult.builder().processedContent(url).build();
        }

        // Internal upload URLs (from our own server) are trusted
        if (url.startsWith("/api/upload/")) {
            return UrlScanResult.builder().processedContent(url).build();
        }

        UrlScanResult.Builder builder = UrlScanResult.builder().processedContent(url);

        UrlSyntaxValidator.ValidationResult syntaxResult = syntaxValidator.validate(url);
        if (!syntaxResult.passed()) {
            builder.addBlocked(url, syntaxResult.reason());
            return builder.build();
        }

        DnsLookupValidator.ValidationResult dnsResult = dnsValidator.validate(url);
        if (!dnsResult.passed()) {
            if (dnsResult.reason().contains("SSRF")) {
                builder.addBlocked(url, dnsResult.reason());
            } else {
                builder.addWarned(url, dnsResult.reason());
            }
        }

        return builder.build();
    }

    /**
     * Extracts all HTTP/HTTPS URLs from text content.
     */
    List<String> extractUrls(String content) {
        List<String> urls = new ArrayList<>();
        Matcher matcher = URL_PATTERN.matcher(content);
        while (matcher.find()) {
            String url = matcher.group(1);
            // Strip trailing punctuation that's likely not part of the URL
            url = url.replaceAll("[.,:;!?]+$", "");
            urls.add(url);
        }
        return urls;
    }

    /**
     * Annotates a URL in the message content with a warning label.
     */
    private String annotateUrl(String content, String url, String annotation) {
        return content.replace(url, url + " " + annotation);
    }
}
