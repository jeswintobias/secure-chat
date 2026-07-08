package com.securechat.service.urlsecurity;

import java.util.ArrayList;
import java.util.List;

/**
 * Immutable result from the URL security pipeline scan.
 *
 * <p>Carries the verdict (SAFE / WARNED / BLOCKED), the optionally modified
 * message content, and details about any flagged URLs.
 */
public class UrlScanResult {

    public enum Verdict {
        /** All URLs passed every check. */
        SAFE,
        /** Some URLs couldn't be verified (DNS/HTTP failure) — content annotated with warnings. */
        WARNED,
        /** One or more URLs are dangerous (malware, phishing, blocked extension) — message rejected. */
        BLOCKED
    }

    private final Verdict verdict;
    private final String processedContent;
    private final List<FlaggedUrl> blockedUrls;
    private final List<FlaggedUrl> warnedUrls;

    private UrlScanResult(Verdict verdict, String processedContent,
                          List<FlaggedUrl> blockedUrls, List<FlaggedUrl> warnedUrls) {
        this.verdict = verdict;
        this.processedContent = processedContent;
        this.blockedUrls = List.copyOf(blockedUrls);
        this.warnedUrls = List.copyOf(warnedUrls);
    }

    public Verdict getVerdict() { return verdict; }
    public String getProcessedContent() { return processedContent; }
    public List<FlaggedUrl> getBlockedUrls() { return blockedUrls; }
    public List<FlaggedUrl> getWarnedUrls() { return warnedUrls; }

    // ── Builder ──

    public static Builder builder() { return new Builder(); }

    public static class Builder {
        private String processedContent;
        private final List<FlaggedUrl> blocked = new ArrayList<>();
        private final List<FlaggedUrl> warned = new ArrayList<>();

        public Builder processedContent(String content) {
            this.processedContent = content;
            return this;
        }

        public Builder addBlocked(String url, String reason) {
            blocked.add(new FlaggedUrl(url, reason));
            return this;
        }

        public Builder addWarned(String url, String reason) {
            warned.add(new FlaggedUrl(url, reason));
            return this;
        }

        public UrlScanResult build() {
            Verdict verdict;
            if (!blocked.isEmpty()) {
                verdict = Verdict.BLOCKED;
            } else if (!warned.isEmpty()) {
                verdict = Verdict.WARNED;
            } else {
                verdict = Verdict.SAFE;
            }
            return new UrlScanResult(verdict, processedContent, blocked, warned);
        }
    }

    /**
     * A URL that was flagged by the security pipeline, with the reason.
     */
    public record FlaggedUrl(String url, String reason) {}
}
