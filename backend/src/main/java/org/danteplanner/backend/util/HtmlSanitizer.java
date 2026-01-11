package org.danteplanner.backend.util;

import org.jsoup.Jsoup;
import org.jsoup.safety.Safelist;

/**
 * Utility class for sanitizing HTML and JavaScript from plain text user input.
 *
 * <p>Implements OWASP defense-in-depth strategy: validates input server-side
 * to prevent stored XSS attacks. Frontend must still escape output when rendering.
 *
 * <p>Applied to plain text fields such as planner titles and comment content.
 * Removes all HTML tags while preserving text content.</p>
 *
 * <p>Examples:
 * <ul>
 *   <li>{@code "<script>alert('xss')</script>"} → {@code "alert('xss')"}</li>
 *   <li>{@code "Hello <b>world</b>!"} → {@code "Hello world!"}</li>
 *   <li>{@code "Plain text"} → {@code "Plain text"} (unchanged)</li>
 * </ul>
 *
 * @see <a href="https://cheatsheetseries.owasp.org/cheatsheets/Cross_Site_Scripting_Prevention_Cheat_Sheet.html">OWASP XSS Prevention</a>
 */
public final class HtmlSanitizer {

    private HtmlSanitizer() {
        // Utility class - prevent instantiation
    }

    /**
     * Sanitizes plain text by removing all HTML tags and scripts.
     *
     * <p>Uses Jsoup with {@link Safelist#none()} to strip all HTML elements
     * while preserving the text content. Null or blank input is returned as-is.</p>
     *
     * @param input the plain text to sanitize (may be null or blank)
     * @return sanitized text with HTML tags removed, or original input if null/blank
     */
    public static String sanitize(String input) {
        if (input == null || input.isBlank()) {
            return input;
        }

        // Safelist.none() removes all HTML tags, keeping only text content
        // This prevents stored XSS attacks from HTML injection
        return Jsoup.clean(input, Safelist.none());
    }
}
