package org.danteplanner.backend.shared.util;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;

import java.util.Set;
import java.util.Locale;

@Slf4j
public final class TiptapUrlSanitizer {

    private static final Set<String> BLOCKED_PROTOCOLS = Set.of(
            "javascript:",
            "data:",
            "vbscript:",
            "file:",
            "about:"
    );

    private static final Set<String> ALLOWED_PROTOCOLS = Set.of(
            "http:",
            "https:",
            "ftp:",
            "ftps:",
            "mailto:",
            "tel:",
            "sms:"
    );

    private static final String SAFE_PLACEHOLDER = "#";

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private TiptapUrlSanitizer() {
    }

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
     * Tiptap carries link URLs on {@code marks[].attrs.href} and image URLs on {@code attrs.src}.
     */
    public static void sanitize(JsonNode node) {
        if (node == null || !node.isObject()) {
            return;
        }

        ObjectNode objNode = (ObjectNode) node;

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

        JsonNode content = objNode.get("content");
        if (content != null && content.isArray()) {
            for (JsonNode child : content) {
                sanitize(child);
            }
        }
    }

    private static String sanitizeUrl(String url) {
        if (url == null || url.isBlank()) {
            return url;
        }

        String trimmed = url.trim().toLowerCase(Locale.ROOT);

        for (String blocked : BLOCKED_PROTOCOLS) {
            if (trimmed.startsWith(blocked)) {
                log.warn("Blocked dangerous URL protocol in Tiptap content: {}", url);
                return SAFE_PLACEHOLDER;
            }
        }

        for (String allowed : ALLOWED_PROTOCOLS) {
            if (trimmed.startsWith(allowed)) {
                return url; // Return original (preserve case)
            }
        }

        if (trimmed.startsWith("/")) {
            return url;
        }

        if (!trimmed.contains(":")) {
            return url;
        }

        log.warn("Blocked unknown URL protocol in Tiptap content: {}", url);
        return SAFE_PLACEHOLDER;
    }
}
