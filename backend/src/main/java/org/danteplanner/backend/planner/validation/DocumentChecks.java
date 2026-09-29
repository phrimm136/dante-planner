package org.danteplanner.backend.planner.validation;

import com.fasterxml.jackson.databind.JsonNode;
import org.danteplanner.backend.planner.entity.MDCategory;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

record DocumentChecks(int maxContentSizeBytes, int maxNoteSizeBytes) {

    private static final Set<String> REQUIRED_FIELDS = Set.of(
            "selectedKeywords", "equipment",
            "deploymentOrder", "floorSelections", "sectionNotes");

    private static final Set<String> ALLOWED_FIELDS = Set.of(
            "selectedKeywords", "selectedBuffIds",
            "selectedGiftKeyword", "selectedGiftIds", "observationGiftIds",
            "comprehensiveGiftIds", "equipment", "deploymentOrder",
            "skillEAState", "floorSelections", "sectionNotes");

    private static final String SELECTED_KEYWORDS = "selectedKeywords";
    private static final String SECTION_NOTES = "sectionNotes";

    static Optional<Violation> emptiness(String content) {
        return content == null || content.isBlank()
                ? Optional.of(new Violation(ErrorCode.EMPTY_CONTENT, ContentPath.root(), "Content is empty or null"))
                : Optional.empty();
    }

    static Optional<Violation> category(String category) {
        return category == null || category.isBlank() || !MDCategory.isValid(category)
                ? Optional.of(new Violation(ErrorCode.INVALID_CATEGORY, ContentPath.root(),
                        String.format("Invalid category: %s", category)))
                : Optional.empty();
    }

    static Violation malformed(String detail) {
        return new Violation(ErrorCode.MALFORMED_JSON, ContentPath.root(), String.format("Malformed JSON: %s", detail));
    }

    Optional<Violation> contentSize(JsonNode parsed) {
        int size = canonicalSize(parsed);
        return size > maxContentSizeBytes
                ? Optional.of(sizeExceeded(ContentPath.root(), "Content", size, maxContentSizeBytes))
                : Optional.empty();
    }

    static Optional<Violation> objectRoot(JsonNode parsed) {
        return switch (parsed.getNodeType()) {
            case OBJECT -> Optional.empty();
            default -> Optional.of(malformed("root element is not an object"));
        };
    }

    static Optional<Violation> unknownFields(JsonNode root) {
        List<String> unknown = root.properties().stream()
                .map(Map.Entry::getKey)
                .filter(field -> !ALLOWED_FIELDS.contains(field))
                .sorted()
                .toList();
        return unknown.isEmpty()
                ? Optional.empty()
                : Optional.of(new Violation(ErrorCode.UNKNOWN_FIELD, ContentPath.root(),
                        String.format("Unknown fields: %s", unknown)));
    }

    static Optional<Violation> requiredFields(JsonNode root) {
        List<String> missing = REQUIRED_FIELDS.stream()
                .filter(field -> !root.has(field))
                .toList();
        return missing.isEmpty()
                ? Optional.empty()
                : Optional.of(Violation.missingFields(ErrorCode.MISSING_REQUIRED_FIELD, ContentPath.root(), missing));
    }

    Optional<Violation> noteSize(JsonNode root) {
        JsonNode sectionNotes = root.path(SECTION_NOTES);
        return switch (sectionNotes.getNodeType()) {
            case OBJECT -> sectionNotes.properties().stream()
                    .map(note -> noteSize(note.getKey(), note.getValue()))
                    .flatMap(Optional::stream)
                    .findFirst();
            default -> Optional.empty();
        };
    }

    static List<Violation> fieldKinds(JsonNode root) {
        return Stream.of(
                        arrayKind(root.path(SELECTED_KEYWORDS), ContentPath.of(SELECTED_KEYWORDS)),
                        objectKind(root.path(SECTION_NOTES), ContentPath.of(SECTION_NOTES)))
                .flatMap(Optional::stream)
                .toList();
    }

    private Optional<Violation> noteSize(String sectionKey, JsonNode note) {
        int size = canonicalSize(note);
        return size > maxNoteSizeBytes
                ? Optional.of(sizeExceeded(ContentPath.of(SECTION_NOTES).key(sectionKey),
                        String.format("Note '%s'", sectionKey), size, maxNoteSizeBytes))
                : Optional.empty();
    }

    private static Optional<Violation> arrayKind(JsonNode node, ContentPath path) {
        return switch (node.getNodeType()) {
            case MISSING, ARRAY -> Optional.empty();
            default -> Optional.of(Violation.invalidFieldType(path, JsonKind.ARRAY, node));
        };
    }

    private static Optional<Violation> objectKind(JsonNode node, ContentPath path) {
        return switch (node.getNodeType()) {
            case MISSING, OBJECT -> Optional.empty();
            default -> Optional.of(Violation.invalidFieldType(path, JsonKind.OBJECT, node));
        };
    }

    private static Violation sizeExceeded(ContentPath path, String subject, int size, int limit) {
        return new Violation(ErrorCode.SIZE_EXCEEDED, path,
                String.format("%s size %d exceeds limit %d", subject, size, limit));
    }

    private static int canonicalSize(JsonNode node) {
        return node.toString().getBytes(StandardCharsets.UTF_8).length;
    }
}
