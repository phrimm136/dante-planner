package org.danteplanner.backend.planner.validation;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.List;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.stream.IntStream;

public final class JsonArrays {

    private JsonArrays() {
    }

    public static FieldResult<List<Optional<String>>> strings(JsonNode node, ContentPath path) {
        return elements(node, path, JsonArrays::string);
    }

    public static FieldResult<List<Optional<Integer>>> ints(JsonNode node, ContentPath path) {
        return elements(node, path, JsonArrays::integer);
    }

    private static <T> FieldResult<List<Optional<T>>> elements(
            JsonNode node, ContentPath path, BiFunction<JsonNode, ContentPath, FieldResult<Optional<T>>> element) {
        return switch (node.getNodeType()) {
            case MISSING -> FieldResult.of(List.of());
            case ARRAY -> {
                List<FieldResult<Optional<T>>> parsed = IntStream.range(0, node.size())
                        .mapToObj(index -> element.apply(node.get(index), path.index(index)))
                        .toList();
                yield new FieldResult<>(
                        parsed.stream().map(FieldResult::value).toList(),
                        parsed.stream().flatMap(result -> result.violations().stream()).toList());
            }
            default -> FieldResult.rejected(List.of(), Violation.invalidFieldType(path, JsonKind.ARRAY, node));
        };
    }

    private static FieldResult<Optional<String>> string(JsonNode element, ContentPath path) {
        return switch (element.getNodeType()) {
            case STRING -> FieldResult.of(Optional.of(element.asText()));
            default -> FieldResult.rejected(Optional.empty(), Violation.invalidFieldType(path, JsonKind.STRING, element));
        };
    }

    private static FieldResult<Optional<Integer>> integer(JsonNode element, ContentPath path) {
        return JsonNumbers.intValue(element)
                .map(value -> FieldResult.of(Optional.of(value)))
                .orElseGet(() -> FieldResult.rejected(Optional.empty(),
                        Violation.invalidFieldType(path, JsonKind.NUMBER, element)));
    }
}
