package org.danteplanner.backend.planner.validation.startbuff;

import org.danteplanner.backend.planner.validation.ContentPath;
import org.danteplanner.backend.planner.validation.Positions;
import org.danteplanner.backend.planner.validation.Violation;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.IntStream;
import java.util.stream.Stream;

public final class StartBuffRules {

    private record Buff(int index, long buffId, boolean known) {

        long baseId() {
            return buffId % BASE_ID_MODULUS;
        }

        Optional<Long> countedBase() {
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

    public static List<Violation> check(List<Long> selectedBuffIds, StartBuffSeason season) {
        return Stream.concat(
                        selectedBuffIds.size() > MAX_START_BUFFS
                                ? Stream.of(StartBuffViolations.tooMany(SELECTED_BUFF_IDS, selectedBuffIds.size()))
                                : Stream.<Violation>empty(),
                        buffs(selectedBuffIds, season))
                .sorted(Violation.BY_PATH_THEN_CODE)
                .toList();
    }

    private static Stream<Violation> buffs(List<Long> buffIds, StartBuffSeason season) {
        List<Buff> buffs = IntStream.range(0, buffIds.size())
                .mapToObj(index -> new Buff(index, buffIds.get(index),
                        season.hasStartBuff(String.valueOf(buffIds.get(index)))))
                .toList();
        List<Optional<Long>> countedBases = buffs.stream().map(Buff::countedBase).toList();
        Map<Optional<Long>, Integer> firstIndex = Positions.firstIndexOf(countedBases);
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

    private static boolean inBaseRange(long baseId) {
        return baseId >= MIN_BASE_ID && baseId <= MAX_BASE_ID;
    }
}
