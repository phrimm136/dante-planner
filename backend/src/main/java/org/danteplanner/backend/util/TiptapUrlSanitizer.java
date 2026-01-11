package org.danteplanner.backend.util;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;

import java.util.Set;

/**
 * Utility class for sanitizing URLs in Tiptap JSON content.
 *
 * <p>Implements OWASP URL validation: blocks dangerous protocols (javascript:, data:, etc.)
 * in rich text editor link and image URLs to prevent XSS attacks.</p>
 *
 * <p>Defense-in-depth: Frontend validates on input, backend validates on storage
 * to prevent API abuse that bypasses frontend.</p>
 *
 * @see <a href="https://cheatsheetseries.owasp.org/cheatsheets/Cross_Site_Scripting_Prevention_Cheat_Sheet.html">OWASP XSS Prevention</a>
 */
@Slf4j
public final class TiptapUrlSanitizer {

    /**
     * Dangerous URL protocols that can execute JavaScript or expose sensitive data.
     * Based on OWASP recommendations.
     */
    private static final Set<String> BLOCKED_PROTOCOLS = Set.of(
            "javascript:",
            "data:",
            "vbscript:",
            "file:",
            "about:"
    );

    /**
     * Safe URL protocols allowed for links and images.
     * Based on OWASP safe URL schemes.
     */
    private static final Set<String> ALLOWED_PROTOCOLS = Set.of(
            "http:",
            "https:",
            "ftp:",
            "ftps:",
            "mailto:",
            "tel:",
            "sms:"
    );

    /**
     * Placeholder URL used when dangerous URLs are detected and removed.
     */
    private static final String SAFE_PLACEHOLDER = "#";

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private TiptapUrlSanitizer() {
        // Utility class - prevent instantiation
    }

    /**
     * Sanitizes URLs in a Tiptap JSON string.
     *
     * <p>Convenience method that parses JSON, sanitizes, and returns sanitized JSON string.
     * If input is invalid JSON, returns input unchanged and logs warning.
     *
     * @param jsonString the Tiptap JSON string to sanitize
     * @return sanitized JSON string, or original input if parsing fails
     */
    public static String sanitizeJson(String jsonString) {
        if (jsonString == null || jsonString.isBlank()) {
            return jsonString;
        }

        try {
            JsonNode root = OBJECT_MAPPER.readTree(jsonString);
            sanitize(root);
            return OBJECT_MAPPER.writeValueAsString(root);
        } catch (JsonProcessingException e) {
            log.warn("Failed to parse Tiptap JSON for sanitization, returning unchanged: {}", e.getMessage());
            return jsonString;
        }
    }

    /**
     * Recursively sanitizes URLs in Tiptap JSON content.
     *
     * <p>Traverses the JSON tree and sanitizes:
     * <ul>
     *   <li>Link marks: {@code marks[].attrs.href}</li>
     *   <li>Image nodes: {@code attrs.src}</li>
     * </ul>
     *
     * <p>Modifies the JSON in-place. Dangerous URLs are replaced with "#".
     *
     * @param node the Tiptap JSON node to sanitize (doc, paragraph, text, etc.)
     */
    public static void sanitize(JsonNode node) {
        if (node == null || !node.isObject()) {
            return;
        }

        ObjectNode objNode = (ObjectNode) node;

        // Sanitize image node src attribute
        if ("image".equals(objNode.path("type").asText(null))) {
            JsonNode attrs = objNode.get("attrs");
            if (attrs != null && attrs.isObject()) {
                JsonNode src = attrs.get("src");
                if (src != null && src.isTextual()) {
                    String sanitized = sanitizeUrl(src.asText());
                    ((ObjectNode) attrs).put("src", sanitized);
                }
            }
        }

        // Sanitize link marks in text nodes
        JsonNode marks = objNode.get("marks");
        if (marks != null && marks.isArray()) {
            for (JsonNode mark : marks) {
                if (mark.isObject() && "link".equals(mark.path("type").asText(null))) {
                    JsonNode attrs = mark.get("attrs");
                    if (attrs != null && attrs.isObject()) {
                        JsonNode href = attrs.get("href");
                        if (href != null && href.isTextual()) {
                            String sanitized = sanitizeUrl(href.asText());
                            ((ObjectNode) attrs).put("href", sanitized);
                        }
                    }
                }
            }
        }

        // Recursively sanitize child content nodes
        JsonNode content = objNode.get("content");
        if (content != null && content.isArray()) {
            for (JsonNode child : content) {
                sanitize(child);
            }
        }
    }

    /**
     * Sanitizes a single URL by validating its protocol.
     *
     * <p>OWASP-compliant URL validation:
     * <ul>
     *   <li>Blocks dangerous protocols (javascript:, data:, vbscript:, etc.)</li>
     *   <li>Allows safe protocols (http:, https:, mailto:, etc.)</li>
     *   <li>Allows relative URLs (/, /path, //domain.com)</li>
     *   <li>Allows plain domains without protocol</li>
     * </ul>
     *
     * @param url the URL to sanitize
     * @return original URL if safe, "#" if dangerous
     */
    private static String sanitizeUrl(String url) {
        if (url == null || url.isBlank()) {
            return url;
        }

        String trimmed = url.trim().toLowerCase();

        // Block dangerous protocols (OWASP recommendation)
        for (String blocked : BLOCKED_PROTOCOLS) {
            if (trimmed.startsWith(blocked)) {
                log.warn("Blocked dangerous URL protocol in Tiptap content: {}", url);
                return SAFE_PLACEHOLDER;
            }
        }

        // Allow safe protocols
        for (String allowed : ALLOWED_PROTOCOLS) {
            if (trimmed.startsWith(allowed)) {
                return url; // Return original (preserve case)
            }
        }

        // Allow relative URLs (/, /path, //domain.com)
        if (trimmed.startsWith("/")) {
            return url;
        }

        // Allow plain domains without protocol (example.com, google.com/path)
        if (!trimmed.contains(":")) {
            return url;
        }

        // Unknown protocol - block for safety (OWASP fail-safe default)
        log.warn("Blocked unknown URL protocol in Tiptap content: {}", url);
        return SAFE_PLACEHOLDER;
    }
}
