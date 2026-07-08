package com.securechat.service;

import org.jsoup.Jsoup;
import org.jsoup.safety.Safelist;
import org.springframework.stereotype.Component;

/**
 * XSS (Cross-Site Scripting) sanitization utility.
 *
 * <p>Uses JSoup's HTML cleaner with a strict whitelist to strip all
 * potentially dangerous HTML tags, attributes, and JavaScript from
 * user-supplied text content.
 *
 * <p>This component is applied to all incoming message content before
 * persistence or broadcasting, providing defense-in-depth alongside
 * Angular's built-in output encoding.
 *
 * <p>Design note: We use {@link Safelist#none()} which strips ALL HTML,
 * leaving only plain text. This is intentionally strict — the chat system
 * does not need inline HTML rendering.
 */
@Component
public class XssSanitizer {

    /**
     * Strips all HTML tags and attributes from the input string.
     *
     * @param input the raw user-supplied text
     * @return sanitized plain text with all HTML removed
     */
    public String sanitize(String input) {
        if (input == null || input.isBlank()) {
            return input;
        }

        // Safelist.none() allows NO HTML elements — pure text output.
        // This prevents all forms of XSS including:
        //   - <script>alert('xss')</script>
        //   - <img onerror="...">
        //   - <a href="javascript:...">
        //   - Event handler attributes (onclick, onload, etc.)
        return Jsoup.clean(input, Safelist.none());
    }
}
