package org.danteplanner.backend.planner.validation.startbuff;

import com.fasterxml.jackson.databind.JsonNode;
import org.danteplanner.backend.planner.validation.ContentPath;
import org.danteplanner.backend.planner.validation.FieldResult;
import org.danteplanner.backend.planner.validation.JsonArrays;
import org.danteplanner.backend.planner.validation.Positions;
import org.danteplanner.backend.planner.validation.Violation;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.IntStream;
import java.util.stream.Stream;

public final class StartBuffRules {

    private record Buff(int index, int buffId, boolean known) {

        int baseId() {
            return buffId % BASE_ID_MODULUS;
        }

        Optional<Integer> countedBase() {
            return known && inBaseRange(baseId()) ? Optional.of(baseId()) : Optional.empty();
        }
    }

    static final int MAX_START_BUFFS = 10;
    static final int MIN_BASE_ID = 0;
    static final int MAX_BASE_ID = 9;

    private static final int BASE_ID_MODULUS = 100;
    private static final ContentPath SELECTED_BUFF_IDS = ContentPath.of("selectedBuffIds");

    private StartBuffRules() {
    }

    public static List<Violation> check(JsonNode selectedBuffIds, Optional<StartBuffSeason> season) {
        FieldResult<List<Optional<Integer>>> parsed = JsonArrays.ints(selectedBuffIds, SELECTED_BUFF_IDS);
        return season
                .map(known -> seasonal(parsed, known))
                .orElseGet(() -> kindOnly(selectedBuffIds, parsed))
                .sorted(Violation.BY_PATH_THEN_CODE)
                .toList();
    }

    private static Stream<Violation> kindOnly(JsonNode selectedBuffIds, FieldResult<List<Optional<Integer>>> parsed) {
        return switch (selectedBuffIds.getNodeType()) {
            case ARRAY -> Stream.empty();
            default -> parsed.violations().stream();
        };
    }

    private static Stream<Violation> seasonal(FieldResult<List<Optional<Integer>>> parsed, StartBuffSeason season) {
        List<Optional<Integer>> buffIds = parsed.value();
        return Stream.of(
                        buffIds.size() > MAX_START_BUFFS
                                ? Stream.of(StartBuffViolations.tooMany(SELECTED_BUFF_IDS, buffIds.size()))
                                : Stream.<Violation>empty(),
                        parsed.violations().stream(),
                        buffs(buffIds, season))
                .flatMap(violations -> violations);
    }

    private static Stream<Violation> buffs(List<Optional<Integer>> buffIds, StartBuffSeason season) {
        List<Buff> buffs = IntStream.range(0, buffIds.size())
                .boxed()
                .flatMap(index -> buffIds.get(index)
                        .map(buffId -> new Buff(index, buffId, season.hasStartBuff(String.valueOf(buffId))))
                        .stream())
                .toList();
        List<Optional<Integer>> countedBases = buffs.stream().map(Buff::countedBase).toList();
        Map<Optional<Integer>, Integer> firstIndex = Positions.firstIndexOf(countedBases);
        return IntStream.range(0, buffs.size())
                .boxed()
                .flatMap(position -> buffViolation(buffs.get(position),
                        countedBases.get(position).isPresent() && firstIndex.get(countedBases.get(position)) < position));
    }

    private static Stream<Violation> buffViolation(Buff buff, boolean repeatsABase) {
        ContentPath path = SELECTED_BUFF_IDS.index(buff.index());
        return !buff.known()
                ? Stream.of(StartBuffViolations.unknownBuff(path, SELECTED_BUFF_IDS, buff.buffId()))
                : !inBaseRange(buff.baseId())
                        ? Stream.of(StartBuffViolations.baseIdOutOfRange(path, buff.baseId()))
                        : repeatsABase
                                ? Stream.of(StartBuffViolations.duplicateBaseId(path, SELECTED_BUFF_IDS, buff.baseId()))
                                : Stream.empty();
    }

    private static boolean inBaseRange(int baseId) {
        return baseId >= MIN_BASE_ID && baseId <= MAX_BASE_ID;
    }
}
