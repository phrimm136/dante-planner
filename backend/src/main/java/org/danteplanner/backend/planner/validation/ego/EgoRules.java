package org.danteplanner.backend.planner.validation.ego;

import org.danteplanner.backend.planner.validation.ContentPath;
import org.danteplanner.backend.planner.validation.Violation;
import org.danteplanner.backend.shared.util.GameConstants;

import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

public final class EgoRules {

    private static final ContentPath EQUIPMENT = ContentPath.of("equipment");
    private static final String EGOS = "egos";
    private static final String ID = "id";
    private static final String THREADSPIN = "threadspin";

    private EgoRules() {
    }

    public static List<Violation> check(Map<String, Map<String, Ego>> egos, EgoReferenceData data) {
        return egos.entrySet().stream()
                .flatMap(sinner -> sinner.getValue().entrySet().stream()
                        .flatMap(slot -> ego(sinner.getKey(), EQUIPMENT.key(sinner.getKey()).field(EGOS)
                                .field(slot.getKey()), slot.getValue(), data)))
                .sorted(Violation.BY_PATH_THEN_CODE)
                .toList();
    }

    private static Stream<Violation> ego(String sinnerKey, ContentPath path, Ego ego, EgoReferenceData data) {
        ContentPath idPath = path.field(ID);
        return !data.hasEgo(ego.id())
                ? Stream.of(EgoViolations.unknownEgo(idPath, ego.id()))
                : !data.belongsToSinner(sinnerKey, ego.id())
                        ? Stream.of(EgoViolations.egoOfAnotherSinner(idPath, sinnerKey, ego.id()))
                        : threadspin(path.field(THREADSPIN), ego, data);
    }

    private static Stream<Violation> threadspin(ContentPath path, Ego ego, EgoReferenceData data) {
        long threadspin = ego.threadspin();
        return threadspin < GameConstants.MIN_THREADSPIN || threadspin > GameConstants.MAX_THREADSPIN
                ? Stream.of(EgoViolations.threadspinOutOfRange(path, threadspin))
                : data.maxThreadspin(ego.id())
                        .map(ceiling -> threadspin > ceiling
                                ? Stream.of(EgoViolations.threadspinAboveCeiling(path, ego.id(), threadspin, ceiling))
                                : Stream.<Violation>empty())
                        .orElseGet(() -> Stream.of(EgoViolations.missingThreadspinCeiling(path, ego.id())));
    }
}
