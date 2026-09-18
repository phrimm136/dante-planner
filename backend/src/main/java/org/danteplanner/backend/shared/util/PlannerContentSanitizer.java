package org.danteplanner.backend.shared.util;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;

@Slf4j
public final class PlannerContentSanitizer {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private PlannerContentSanitizer() {
    }

    public static String sanitize(String contentJson) {
        if (contentJson == null || contentJson.isBlank()) {
            return contentJson;
        }

        try {
            JsonNode root = OBJECT_MAPPER.readTree(contentJson);

            JsonNode sectionNotes = root.get("sectionNotes");
            if (sectionNotes != null && sectionNotes.isObject()) {
                for (Map.Entry<String, JsonNode> entry : sectionNotes.properties()) {
                    JsonNode noteValue = entry.getValue();
                    if (noteValue != null && noteValue.isObject()) {
                        JsonNode tiptapContent = noteValue.get("content");
                        if (tiptapContent != null) {
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
