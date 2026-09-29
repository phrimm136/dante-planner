package org.danteplanner.backend.planner.validation.egogift;

import org.danteplanner.backend.planner.validation.ContentPath;
import org.danteplanner.backend.planner.validation.ErrorCode;
import org.danteplanner.backend.planner.validation.Positions;
import org.danteplanner.backend.planner.validation.Violation;

import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;
import java.util.stream.Stream;

public final class EgoGiftRules {

    private static final ContentPath OBSERVATION_GIFT_IDS = ContentPath.of("observationGiftIds");
    private static final ContentPath COMPREHENSIVE_GIFT_IDS = ContentPath.of("comprehensiveGiftIds");

    private EgoGiftRules() {
    }

    public static List<Violation> check(List<String> observationGiftIds, List<String> comprehensiveGiftIds,
                                        EgoGiftReferenceData data) {
        return Stream.concat(
                        giftIds(observationGiftIds, OBSERVATION_GIFT_IDS, data),
                        giftIds(comprehensiveGiftIds, COMPREHENSIVE_GIFT_IDS, data))
                .sorted(Violation.BY_PATH_THEN_CODE)
                .toList();
    }

    private static Stream<Violation> giftIds(List<String> giftIds, ContentPath path, EgoGiftReferenceData data) {
        Map<String, Integer> firstIndex = Positions.firstIndexOf(giftIds);
        return IntStream.range(0, giftIds.size())
                .boxed()
                .flatMap(index -> firstIndex.get(giftIds.get(index)) < index
                        ? Stream.of(Violation.duplicateValue(ErrorCode.DUPLICATE_EGOGIFT, path.index(index),
                                path.render(), giftIds.get(index)))
                        : unknownGift(path, index, giftIds.get(index), data));
    }

    private static Stream<Violation> unknownGift(ContentPath path, int index, String giftId,
                                                 EgoGiftReferenceData data) {
        return data.hasEgoGift(giftId)
                ? Stream.empty()
                : Stream.of(Violation.unknownId(ErrorCode.UNKNOWN_EGOGIFT, path.index(index), path.render(), giftId));
    }
}
