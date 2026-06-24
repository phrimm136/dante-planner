package org.danteplanner.backend.validation;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Validates the structural shape of planner content: byte-size limits,
 * JSON well-formedness, the allowed/required field set, field types, and
 * per-note size limits.
 */
@Component
@Slf4j
class StructuralValidator {

    private static final Set<String> REQUIRED_KEYS = Set.of(
            "selectedKeywords", "equipment",
            "deploymentOrder", "floorSelections", "sectionNotes"
    );

    private static final Set<String> ALLOWED_KEYS = Set.of(
            "selectedKeywords", "selectedBuffIds",
            "selectedGiftKeyword", "selectedGiftIds", "observationGiftIds",
            "comprehensiveGiftIds", "equipment", "deploymentOrder",
            "skillEAState", "floorSelections", "sectionNotes"
    );

    private final ObjectMapper objectMapper;
    private final int maxContentSizeBytes;
    private final int maxNoteSizeBytes;

    StructuralValidator(
            ObjectMapper objectMapper,
            @Value("${planner.validation.max-content-size}") int maxContentSizeBytes,
            @Value("${planner.validation.max-note-size}") int maxNoteSizeBytes) {
        this.objectMapper = objectMapper;
        this.maxContentSizeBytes = maxContentSizeBytes;
        this.maxNoteSizeBytes = maxNoteSizeBytes;
    }

    void validateContentSize(String content) {
        int size = content.getBytes(StandardCharsets.UTF_8).length;
        if (size > maxContentSizeBytes) {
            log.warn("Validation failed: content size {} exceeds limit {}", size, maxContentSizeBytes);
            throw ValidationErrors.sizeExceeded("Content", size, maxContentSizeBytes);
        }
    }

    JsonNode parseJson(String content) {
        try {
            return objectMapper.readTree(content);
        } catch (JsonProcessingException e) {
            log.warn("Validation failed: malformed JSON - {}", e.getMessage());
            throw ValidationErrors.malformedJson(e.getMessage());
        }
    }

    void validateNoUnknownFields(JsonNode root) {
        Set<String> unknown = new HashSet<>();
        Iterator<String> fields = root.fieldNames();

        while (fields.hasNext()) {
            String field = fields.next();
            if (!ALLOWED_KEYS.contains(field)) {
                unknown.add(field);
            }
        }

        if (!unknown.isEmpty()) {
            log.warn("Validation failed: unknown fields - {}", unknown);
            throw ValidationErrors.unknownField(unknown);
        }
    }

    void validateRequiredFields(JsonNode root) {
        Set<String> missing = new HashSet<>();

        for (String key : REQUIRED_KEYS) {
            if (!root.has(key)) {
                missing.add(key);
            }
        }

        if (!missing.isEmpty()) {
            log.warn("Validation failed: missing fields - {}", missing);
            throw ValidationErrors.missingRequiredField(missing);
        }
    }

    void validateFieldTypes(JsonNode root, ValidationContext context) {
        validateType(root, "selectedKeywords", JsonNode::isArray, "array", context);
        validateType(root, "deploymentOrder", JsonNode::isArray, "array", context);
        validateType(root, "floorSelections", JsonNode::isArray, "array", context);
        validateType(root, "equipment", JsonNode::isObject, "object", context);
        validateType(root, "sectionNotes", JsonNode::isObject, "object", context);

        validateOptionalType(root, "selectedBuffIds", JsonNode::isArray, "array", context);
        validateOptionalType(root, "selectedGiftIds", JsonNode::isArray, "array", context);
        validateOptionalType(root, "observationGiftIds", JsonNode::isArray, "array", context);
        validateOptionalType(root, "comprehensiveGiftIds", JsonNode::isArray, "array", context);
        validateOptionalType(root, "skillEAState", JsonNode::isObject, "object", context);

        if (root.has("selectedGiftKeyword")) {
            JsonNode node = root.get("selectedGiftKeyword");
            if (!node.isTextual() && !node.isNull()) {
                log.warn("Validation failed: 'selectedGiftKeyword' wrong type");
                context.addError(ValidationErrors.invalidFieldType("selectedGiftKeyword", "string or null", node));
            }
        }
    }

    private void validateType(JsonNode root, String field, Predicate<JsonNode> check, String expected,
                              ValidationContext context) {
        JsonNode node = root.get(field);
        if (node != null && !check.test(node)) {
            log.warn("Validation failed: '{}' wrong type (expected {})", field, expected);
            context.addError(ValidationErrors.invalidFieldType(field, expected, node));
        }
    }

    private void validateOptionalType(JsonNode root, String field, Predicate<JsonNode> check, String expected,
                                      ValidationContext context) {
        if (root.has(field)) {
            validateType(root, field, check, expected, context);
        }
    }

    void validateNoteSize(JsonNode root) {
        JsonNode sectionNotes = root.get("sectionNotes");
        if (sectionNotes == null || !sectionNotes.isObject()) {
            return;
        }

        for (Map.Entry<String, JsonNode> entry : sectionNotes.properties()) {
            String sectionKey = entry.getKey();
            try {
                String noteJson = objectMapper.writeValueAsString(entry.getValue());
                int noteSize = noteJson.getBytes(StandardCharsets.UTF_8).length;

                if (noteSize > maxNoteSizeBytes) {
                    log.warn("Validation failed: note '{}' size {} exceeds limit {}", sectionKey, noteSize, maxNoteSizeBytes);
                    throw ValidationErrors.sizeExceeded("Note '" + sectionKey + "'", noteSize, maxNoteSizeBytes);
                }
            } catch (JsonProcessingException e) {
                log.warn("Validation failed: cannot serialize note '{}'", sectionKey);
                throw ValidationErrors.malformedJson("cannot serialize note '" + sectionKey + "'");
            }
        }
    }
}
