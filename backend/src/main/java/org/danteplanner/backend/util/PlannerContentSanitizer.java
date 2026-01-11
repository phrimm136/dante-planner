package org.danteplanner.backend.util;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;

/**
 * Utility class for sanitizing planner content JSON.
 *
 * <p>Sanitizes URLs in Tiptap JSON content within the sectionNotes field
 * of planner content. Implements OWASP defense-in-depth for XSS prevention.
 *
 * @see TiptapUrlSanitizer
 */
@Slf4j
public final class PlannerContentSanitizer {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private PlannerContentSanitizer() {
        // Utility class - prevent instantiation
    }

    /**
     * Sanitizes planner content by sanitizing URLs in all sectionNotes.
     *
     * <p>Parses the content JSON, recursively sanitizes Tiptap URLs in each
     * section's content field, then returns the sanitized JSON string.
     *
     * <p>If parsing fails, returns the original input and logs a warning.
     *
     * @param contentJson the planner content JSON string
     * @return sanitized content JSON string
     */
    public static String sanitize(String contentJson) {
        if (contentJson == null || contentJson.isBlank()) {
            return contentJson;
        }

        try {
            JsonNode root = OBJECT_MAPPER.readTree(contentJson);

            // Sanitize sectionNotes if present
            JsonNode sectionNotes = root.get("sectionNotes");
            if (sectionNotes != null && sectionNotes.isObject()) {
                for (Map.Entry<String, JsonNode> entry : sectionNotes.properties()) {
                    JsonNode noteValue = entry.getValue();
                    if (noteValue != null && noteValue.isObject()) {
                        // Each note has a "content" field with Tiptap JSON
                        JsonNode tiptapContent = noteValue.get("content");
                        if (tiptapContent != null) {
                            // Sanitize URLs in Tiptap JSON in-place
                            TiptapUrlSanitizer.sanitize(tiptapContent);
                        }
                    }
                }
            }

            return OBJECT_MAPPER.writeValueAsString(root);
        } catch (JsonProcessingException e) {
            log.warn("Failed to parse planner content for sanitization, returning unchanged: {}", e.getMessage());
            return contentJson;
        }
    }
}
