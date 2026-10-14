package org.danteplanner.backend.planner.validation.startbuff;

import org.danteplanner.backend.planner.validation.ContentPath;
import org.danteplanner.backend.planner.validation.Positions;
import org.danteplanner.backend.planner.validation.Violation;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.IntStream;
import java.util.stream.Stream;

public final class StartBuffRules {

    private record Buff(int index, long buffId, boolean known) {

        long baseId() {
            return buffId % BASE_ID_MODULUS;
        }

        Optional<Long> countedBase() {
            return known ? Optional.of(baseId()) : Optional.empty();
        }
    }

    private static final int BASE_ID_MODULUS = 100;
    private static final ContentPath SELECTED_BUFF_IDS = ContentPath.of("selectedBuffIds");

    private StartBuffRules() {
    }

    public static List<Violation> check(List<Long> selectedBuffIds, StartBuffSeason season) {
        Set<String> seasonIds = season.startBuffIds();
        int maxCount = baseCount(seasonIds);
        return Stream.concat(
                        selectedBuffIds.size() > maxCount
                                ? Stream.of(StartBuffViolations.tooMany(SELECTED_BUFF_IDS, selectedBuffIds.size(),
                                        maxCount))
                                : Stream.<Violation>empty(),
                        buffs(selectedBuffIds, seasonIds))
                .sorted(Violation.BY_PATH_THEN_CODE)
                .toList();
    }

    private static int baseCount(Set<String> seasonIds) {
        return (int) seasonIds.stream()
                .mapToLong(Long::parseLong)
                .map(id -> id % BASE_ID_MODULUS)
                .distinct()
                .count();
    }

    private static Stream<Violation> buffs(List<Long> buffIds, Set<String> seasonIds) {
        List<Buff> buffs = IntStream.range(0, buffIds.size())
                .mapToObj(index -> new Buff(index, buffIds.get(index),
                        seasonIds.contains(String.valueOf(buffIds.get(index)))))
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
                : repeatsABase
                        ? Stream.of(StartBuffViolations.duplicateBaseId(path, SELECTED_BUFF_IDS, buff.baseId()))
                        : Stream.empty();
    }
}
