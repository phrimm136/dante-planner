package org.danteplanner.backend.planner.validation.sinner;

import com.fasterxml.jackson.databind.JsonNode;
import org.danteplanner.backend.planner.validation.ContentPath;
import org.danteplanner.backend.planner.validation.ErrorCode;
import org.danteplanner.backend.planner.validation.FieldResult;
import org.danteplanner.backend.planner.validation.JsonKind;
import org.danteplanner.backend.planner.validation.SinnerEntry;
import org.danteplanner.backend.planner.validation.Violation;

import java.math.BigInteger;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.regex.Pattern;
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

    private record KeyedValue(FieldResult<Optional<String>> sinnerKey, JsonNode value) {
    }

    static final int FIRST_SINNER = 1;
    static final int LAST_SINNER = 12;

    private static final Pattern SINNER_NUMBER = Pattern.compile("[+-]?[\\p{Nd}&&[\\x{0}-\\x{FFFF}]]+");
    private static final List<String> ALL_SINNER_KEYS = IntStream.rangeClosed(FIRST_SINNER, LAST_SINNER)
            .mapToObj(SinnerKeys::keyOf)
            .toList();

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
                .map(property -> new KeyedValue(sinnerKey(property.getKey(), path), property.getValue()))
                .toList();
        Map<String, JsonNode> bySinner = keyed.stream()
                .flatMap(entry -> entry.sinnerKey().value().map(key -> Map.entry(key, entry.value())).stream())
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (first, later) -> later, TreeMap::new));
        List<FieldResult<Optional<SinnerEntry>>> entries = bySinner.entrySet().stream()
                .map(sinner -> entry(sinner.getKey(), sinner.getValue(), path.key(sinner.getKey())))
                .toList();
        return new Sinners(
                entries.stream().flatMap(entry -> entry.value().stream()).toList(),
                Stream.of(
                                keyed.stream().flatMap(entry -> entry.sinnerKey().violations().stream()),
                                missingSinners(bySinner, path),
                                entries.stream().flatMap(entry -> entry.violations().stream()))
                        .flatMap(violations -> violations)
                        .sorted(Violation.BY_PATH_THEN_CODE)
                        .toList());
    }

    private static FieldResult<Optional<String>> sinnerKey(String key, ContentPath containerPath) {
        return number(key)
                .map(number -> inRange(number)
                        ? FieldResult.of(Optional.of(keyOf(number.intValue())))
                        : FieldResult.<Optional<String>>rejected(Optional.empty(),
                                SinnerViolations.keyOutOfRange(containerPath, key, number.intValue())))
                .orElseGet(() -> FieldResult.rejected(Optional.empty(), SinnerViolations.keyNotANumber(containerPath, key)));
    }

    private static Optional<BigInteger> number(String key) {
        return SINNER_NUMBER.matcher(key).matches()
                ? Optional.of(new BigInteger(key)).filter(number -> number.bitLength() < Integer.SIZE)
                : Optional.empty();
    }

    private static boolean inRange(BigInteger number) {
        return number.intValue() >= FIRST_SINNER && number.intValue() <= LAST_SINNER;
    }

    private static Stream<Violation> missingSinners(Map<String, JsonNode> bySinner, ContentPath path) {
        List<String> missing = ALL_SINNER_KEYS.stream().filter(key -> !bySinner.containsKey(key)).toList();
        return missing.isEmpty()
                ? Stream.empty()
                : Stream.of(Violation.missingFields(ErrorCode.MISSING_SINNER, path, missing));
    }

    private static FieldResult<Optional<SinnerEntry>> entry(String sinnerKey, JsonNode value, ContentPath path) {
        return switch (value.getNodeType()) {
            case OBJECT -> FieldResult.of(Optional.of(new SinnerEntry(sinnerKey, path, value)));
            default -> FieldResult.rejected(Optional.empty(), SinnerViolations.sinnerNotAnObject(path));
        };
    }

    private static String keyOf(int sinner) {
        return String.format("%02d", sinner);
    }
}
