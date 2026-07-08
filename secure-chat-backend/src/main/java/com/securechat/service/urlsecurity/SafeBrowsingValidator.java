package com.securechat.service.urlsecurity;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.*;

/**
 * Layer 4 — Google Safe Browsing API Integration.
 *
 * <p>Checks URLs against Google's Safe Browsing v4 Lookup API to detect:
 * <ul>
 *   <li>MALWARE — sites hosting malicious software</li>
 *   <li>SOCIAL_ENGINEERING — phishing sites</li>
 *   <li>UNWANTED_SOFTWARE — deceptive software downloads</li>
 *   <li>POTENTIALLY_HARMFUL_APPLICATION — apps that may harm devices</li>
 * </ul>
 *
 * <p><b>Graceful degradation:</b> If no API key is configured or the API is
 * unreachable, this validator logs a warning and passes the URL through.
 * It will never block messages just because the Safe Browsing API is down.
 *
 * <p>API documentation: <a href="https://developers.google.com/safe-browsing/v4/lookup-api">
 * Google Safe Browsing v4 Lookup API</a>
 */
@Component
@Slf4j
public class SafeBrowsingValidator {

    private static final String API_URL = "https://safebrowsing.googleapis.com/v4/threatMatches:find";
    private static final List<String> THREAT_TYPES = List.of(
            "MALWARE", "SOCIAL_ENGINEERING", "UNWANTED_SOFTWARE", "POTENTIALLY_HARMFUL_APPLICATION"
    );
    private static final List<String> PLATFORM_TYPES = List.of("ANY_PLATFORM");
    private static final List<String> THREAT_ENTRY_TYPES = List.of("URL");
    private static final String CLIENT_ID = "securechat-system";
    private static final String CLIENT_VERSION = "1.0.0";

    private final String apiKey;
    private final boolean enabled;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final int timeoutSeconds;

    public SafeBrowsingValidator(
            @Value("${app.security.safe-browsing-api-key:}")
            String apiKey,
            @Value("${app.security.url-validation.timeout-seconds:3}")
            int timeoutSeconds
    ) {
        this.apiKey = apiKey;
        this.timeoutSeconds = timeoutSeconds;
        this.enabled = apiKey != null && !apiKey.isBlank();
        this.objectMapper = new ObjectMapper();

        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(timeoutSeconds))
                .build();

        if (enabled) {
            log.info("SafeBrowsingValidator initialized with API key (key length: {})", apiKey.length());
        } else {
            log.warn("SafeBrowsingValidator DISABLED — no API key configured. " +
                     "Set app.security.safe-browsing-api-key or SAFE_BROWSING_API_KEY env var to enable.");
        }
    }

    /**
     * Checks a batch of URLs against Google Safe Browsing.
     *
     * @param urls the list of URLs to check
     * @return a map of URL → threat type for any flagged URLs (empty map = all safe)
     */
    public Map<String, String> checkUrls(List<String> urls) {
        if (!enabled || urls.isEmpty()) {
            return Collections.emptyMap();
        }

        try {
            String requestBody = buildRequestBody(urls);
            String apiUrl = API_URL + "?key=" + apiKey;

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(apiUrl))
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                    .header("Content-Type", "application/json")
                    .timeout(Duration.ofSeconds(timeoutSeconds))
                    .build();

            HttpResponse<String> response = httpClient.send(request,
                    HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                log.warn("Safe Browsing API returned HTTP {}: {}", response.statusCode(), response.body());
                return Collections.emptyMap(); // Graceful degradation
            }

            return parseResponse(response.body());

        } catch (Exception e) {
            log.warn("Safe Browsing API call failed (graceful degradation — URLs will pass): {}", e.getMessage());
            return Collections.emptyMap();
        }
    }

    /**
     * Builds the JSON request body for the Safe Browsing v4 API.
     */
    private String buildRequestBody(List<String> urls) throws Exception {
        ObjectNode root = objectMapper.createObjectNode();

        // Client info
        ObjectNode clientNode = objectMapper.createObjectNode();
        clientNode.put("clientId", CLIENT_ID);
        clientNode.put("clientVersion", CLIENT_VERSION);
        root.set("client", clientNode);

        // Threat info
        ObjectNode threatInfo = objectMapper.createObjectNode();

        // Threat types
        ArrayNode threatTypes = objectMapper.createArrayNode();
        THREAT_TYPES.forEach(threatTypes::add);
        threatInfo.set("threatTypes", threatTypes);

        // Platform types
        ArrayNode platformTypes = objectMapper.createArrayNode();
        PLATFORM_TYPES.forEach(platformTypes::add);
        threatInfo.set("platformTypes", platformTypes);

        // Threat entry types
        ArrayNode threatEntryTypes = objectMapper.createArrayNode();
        THREAT_ENTRY_TYPES.forEach(threatEntryTypes::add);
        threatInfo.set("threatEntryTypes", threatEntryTypes);

        // Threat entries (the URLs)
        ArrayNode threatEntries = objectMapper.createArrayNode();
        for (String url : urls) {
            ObjectNode entry = objectMapper.createObjectNode();
            entry.put("url", url);
            threatEntries.add(entry);
        }
        threatInfo.set("threatEntries", threatEntries);

        root.set("threatInfo", threatInfo);

        return objectMapper.writeValueAsString(root);
    }

    /**
     * Parses the Safe Browsing API response to extract flagged URLs.
     *
     * @param responseBody the raw JSON response
     * @return map of URL → threat type description
     */
    private Map<String, String> parseResponse(String responseBody) throws Exception {
        Map<String, String> flagged = new HashMap<>();

        JsonNode root = objectMapper.readTree(responseBody);
        JsonNode matches = root.get("matches");

        if (matches == null || !matches.isArray() || matches.isEmpty()) {
            return flagged; // No threats found
        }

        for (JsonNode match : matches) {
            String threatType = match.has("threatType") ? match.get("threatType").asText() : "UNKNOWN";
            JsonNode threat = match.get("threat");
            if (threat != null && threat.has("url")) {
                String url = threat.get("url").asText();
                String description = switch (threatType) {
                    case "MALWARE" -> "Malware detected — this site distributes malicious software";
                    case "SOCIAL_ENGINEERING" -> "Phishing detected — this site impersonates a legitimate entity";
                    case "UNWANTED_SOFTWARE" -> "Unwanted software — this site distributes deceptive software";
                    case "POTENTIALLY_HARMFUL_APPLICATION" -> "Harmful application — this site may harm your device";
                    default -> "Security threat detected: " + threatType;
                };
                flagged.put(url, description);
                log.warn("Safe Browsing flagged URL: {} — {}", url, threatType);
            }
        }

        return flagged;
    }

    /**
     * Returns whether the Safe Browsing integration is active.
     */
    public boolean isEnabled() {
        return enabled;
    }
}
