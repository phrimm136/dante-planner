package org.danteplanner.backend.planner.validation;

import com.fasterxml.jackson.databind.JsonNode;
import org.danteplanner.backend.planner.entity.PlannerKeywords;
import org.danteplanner.backend.planner.validation.floor.Stage;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.IntStream;

record DocumentChecks(int maxContentSizeBytes, int maxNoteSizeBytes) {

    private static final String SELECTED_KEYWORDS = "selectedKeywords";
    private static final String SECTION_NOTES = "sectionNotes";
    private static final ContentPath SELECTED_KEYWORDS_PATH = ContentPath.of(SELECTED_KEYWORDS);

    static Optional<Violation> emptiness(String content) {
        return content == null || content.isBlank()
                ? Optional.of(new Violation(ErrorCode.EMPTY_CONTENT, ContentPath.root(), "Content is empty or null"))
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

    Optional<Violation> noteSize(Map<String, JsonNode> sectionNotes) {
        return sectionNotes.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(note -> noteSize(note.getKey(), note.getValue()))
                .flatMap(Optional::stream)
                .findFirst();
    }

    static List<Violation> selectedKeywords(List<String> keywords, Stage stage) {
        return switch (stage) {
            case PUBLISH -> unknownKeywords(keywords);
            case DRAFT, INDEX -> List.of();
        };
    }

    private static List<Violation> unknownKeywords(List<String> keywords) {
        return IntStream.range(0, keywords.size())
                .filter(index -> !PlannerKeywords.isKnown(keywords.get(index)))
                .mapToObj(index -> Violation.unknownValue(ErrorCode.UNKNOWN_KEYWORD,
                        SELECTED_KEYWORDS_PATH.index(index), SELECTED_KEYWORDS, keywords.get(index)))
                .toList();
    }

    private Optional<Violation> noteSize(String sectionKey, JsonNode note) {
        int size = canonicalSize(note);
        return size > maxNoteSizeBytes
                ? Optional.of(sizeExceeded(ContentPath.of(SECTION_NOTES).key(sectionKey),
                        String.format("Note '%s'", sectionKey), size, maxNoteSizeBytes))
                : Optional.empty();
    }

    private static Violation sizeExceeded(ContentPath path, String subject, int size, int limit) {
        return new Violation(ErrorCode.SIZE_EXCEEDED, path,
                String.format("%s size %d exceeds limit %d", subject, size, limit));
    }

    private static int canonicalSize(JsonNode node) {
        return node.toString().getBytes(StandardCharsets.UTF_8).length;
    }
}
