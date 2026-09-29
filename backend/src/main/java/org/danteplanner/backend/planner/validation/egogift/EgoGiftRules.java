package org.danteplanner.backend.planner.validation.egogift;

import com.fasterxml.jackson.databind.JsonNode;
import org.danteplanner.backend.planner.validation.ContentPath;
import org.danteplanner.backend.planner.validation.ErrorCode;
import org.danteplanner.backend.planner.validation.FieldResult;
import org.danteplanner.backend.planner.validation.JsonArrays;
import org.danteplanner.backend.planner.validation.Positions;
import org.danteplanner.backend.planner.validation.Violation;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.IntStream;
import java.util.stream.Stream;

public final class EgoGiftRules {

    private static final ContentPath OBSERVATION_GIFT_IDS = ContentPath.of("observationGiftIds");
    private static final ContentPath COMPREHENSIVE_GIFT_IDS = ContentPath.of("comprehensiveGiftIds");

    private EgoGiftRules() {
    }

    public static List<Violation> check(JsonNode observationGiftIds, JsonNode comprehensiveGiftIds,
                                        EgoGiftReferenceData data) {
        return Stream.concat(
                        giftIds(observationGiftIds, OBSERVATION_GIFT_IDS, data),
                        giftIds(comprehensiveGiftIds, COMPREHENSIVE_GIFT_IDS, data))
                .sorted(Violation.BY_PATH_THEN_CODE)
                .toList();
    }

    private static Stream<Violation> giftIds(JsonNode node, ContentPath path, EgoGiftReferenceData data) {
        FieldResult<List<Optional<String>>> parsed = JsonArrays.strings(node, path);
        List<Optional<String>> giftIds = parsed.value();
        Map<Optional<String>, Integer> firstIndex = Positions.firstIndexOf(giftIds);
        return Stream.concat(
                parsed.violations().stream(),
                IntStream.range(0, giftIds.size())
                        .boxed()
                        .flatMap(index -> giftIds.get(index).stream()
                                .flatMap(giftId -> firstIndex.get(giftIds.get(index)) < index
                                        ? Stream.of(Violation.duplicateValue(ErrorCode.DUPLICATE_EGOGIFT,
                                                path.index(index), path.render(), giftId))
                                        : unknownGift(path, index, giftId, data))));
    }

    private static Stream<Violation> unknownGift(ContentPath path, int index, String giftId,
                                                 EgoGiftReferenceData data) {
        return data.hasEgoGift(giftId)
                ? Stream.empty()
                : Stream.of(Violation.unknownId(ErrorCode.UNKNOWN_EGOGIFT, path.index(index), path.render(), giftId));
    }
}
