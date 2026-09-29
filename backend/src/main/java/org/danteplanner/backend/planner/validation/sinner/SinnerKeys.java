package org.danteplanner.backend.planner.validation.sinner;

import com.fasterxml.jackson.databind.JsonNode;
import org.danteplanner.backend.planner.validation.ContentPath;
import org.danteplanner.backend.planner.validation.ErrorCode;
import org.danteplanner.backend.planner.validation.FieldResult;
import org.danteplanner.backend.planner.validation.JsonKind;
import org.danteplanner.backend.planner.validation.SinnerEntry;
import org.danteplanner.backend.planner.validation.Violation;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

public final class SinnerKeys {

    public record Sinners(List<SinnerEntry> entries, List<Violation> violations) {

        public Sinners {
            entries = List.copyOf(entries);
            violations = List.copyOf(violations);
        }
    }

    private record KeyedValue(FieldResult<Optional<SinnerKey>> sinnerKey, JsonNode value) {
    }

    private record SinnerValue(SinnerKey sinnerKey, JsonNode value) {
    }

    private SinnerKeys() {
    }

    public static Sinners parse(JsonNode container, ContentPath path) {
        return switch (container.getNodeType()) {
            case MISSING -> new Sinners(List.of(), List.of());
            case OBJECT -> parseEntries(container, path);
            default -> new Sinners(List.of(), List.of(Violation.invalidFieldType(path, JsonKind.OBJECT, container)));
        };
    }

    private static Sinners parseEntries(JsonNode container, ContentPath path) {
        List<KeyedValue> keyed = container.properties().stream()
                .map(property -> new KeyedValue(SinnerKey.parse(property.getKey(), path), property.getValue()))
                .toList();
        Map<Integer, List<SinnerValue>> bySinner = keyed.stream()
                .flatMap(entry -> entry.sinnerKey().value().map(key -> new SinnerValue(key, entry.value())).stream())
                .collect(Collectors.groupingBy(sinner -> sinner.sinnerKey().sinner(), TreeMap::new, Collectors.toList()));
        List<FieldResult<Optional<SinnerEntry>>> entries = bySinner.values().stream()
                .filter(named -> named.size() == 1)
                .map(named -> entry(named.getFirst(), path))
                .toList();
        return new Sinners(
                entries.stream().flatMap(entry -> entry.value().stream()).toList(),
                Stream.of(
                                keyed.stream().flatMap(entry -> entry.sinnerKey().violations().stream()),
                                duplicateSinners(bySinner, path),
                                missingSinners(bySinner, path),
                                entries.stream().flatMap(entry -> entry.violations().stream()))
                        .flatMap(violations -> violations)
                        .sorted(Violation.BY_PATH_THEN_CODE)
                        .toList());
    }

    private static Stream<Violation> duplicateSinners(Map<Integer, List<SinnerValue>> bySinner, ContentPath path) {
        return bySinner.values().stream()
                .filter(named -> named.size() > 1)
                .flatMap(List::stream)
                .map(named -> SinnerViolations.duplicateSinner(path, named.sinnerKey()));
    }

    private static Stream<Violation> missingSinners(Map<Integer, List<SinnerValue>> bySinner, ContentPath path) {
        List<String> missing = IntStream.rangeClosed(SinnerKey.FIRST_SINNER, SinnerKey.LAST_SINNER)
                .filter(sinner -> !bySinner.containsKey(sinner))
                .mapToObj(SinnerKey::paddedOf)
                .toList();
        return missing.isEmpty()
                ? Stream.empty()
                : Stream.of(Violation.missingFields(ErrorCode.MISSING_SINNER, path, missing));
    }

    private static FieldResult<Optional<SinnerEntry>> entry(SinnerValue sinner, ContentPath containerPath) {
        String sinnerKey = sinner.sinnerKey().padded();
        ContentPath path = containerPath.key(sinnerKey);
        return switch (sinner.value().getNodeType()) {
            case OBJECT -> FieldResult.of(Optional.of(new SinnerEntry(sinnerKey, path, sinner.value())));
            default -> FieldResult.rejected(Optional.empty(), SinnerViolations.sinnerNotAnObject(path));
        };
    }
}
