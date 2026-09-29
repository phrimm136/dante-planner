package org.danteplanner.backend.planner.validation.startgift;

import com.fasterxml.jackson.databind.JsonNode;
import org.danteplanner.backend.planner.validation.ContentPath;
import org.danteplanner.backend.planner.validation.FieldResult;
import org.danteplanner.backend.planner.validation.JsonArrays;
import org.danteplanner.backend.planner.validation.JsonKind;
import org.danteplanner.backend.planner.validation.Positions;
import org.danteplanner.backend.planner.validation.Violation;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.IntStream;
import java.util.stream.Stream;

public final class StartGiftRules {

    private record StartGift(int index, String giftId, boolean repeated) {
    }

    private static final ContentPath SELECTED_GIFT_KEYWORD = ContentPath.of("selectedGiftKeyword");
    private static final ContentPath SELECTED_GIFT_IDS = ContentPath.of("selectedGiftIds");

    private StartGiftRules() {
    }

    public static List<Violation> check(JsonNode selectedGiftKeyword, JsonNode selectedGiftIds,
                                        StartGiftReferenceData data, Optional<StartGiftSeason> season) {
        FieldResult<Optional<String>> keyword = keyword(selectedGiftKeyword);
        FieldResult<List<Optional<String>>> giftIds = JsonArrays.strings(selectedGiftIds, SELECTED_GIFT_IDS);
        List<StartGift> gifts = gifts(giftIds.value());
        return Stream.of(
                        keyword.violations().stream(),
                        giftIds.violations().stream(),
                        gifts.stream().flatMap(gift -> giftViolation(gift, data)),
                        season.stream().flatMap(known -> keywordViolations(keyword.value(), giftIds.value().size(),
                                gifts, known)))
                .flatMap(violations -> violations)
                .sorted(Violation.BY_PATH_THEN_CODE)
                .toList();
    }

    private static FieldResult<Optional<String>> keyword(JsonNode keyword) {
        return switch (keyword.getNodeType()) {
            case MISSING, NULL -> FieldResult.of(Optional.empty());
            case STRING -> FieldResult.of(Optional.of(keyword.asText()));
            default -> FieldResult.rejected(Optional.empty(),
                    Violation.invalidFieldType(SELECTED_GIFT_KEYWORD, JsonKind.STRING_OR_NULL, keyword));
        };
    }

    private static List<StartGift> gifts(List<Optional<String>> giftIds) {
        Map<Optional<String>, Integer> firstIndex = Positions.firstIndexOf(giftIds);
        return IntStream.range(0, giftIds.size())
                .boxed()
                .flatMap(index -> giftIds.get(index)
                        .map(giftId -> new StartGift(index, giftId, firstIndex.get(giftIds.get(index)) < index))
                        .stream())
                .toList();
    }

    private static Stream<Violation> giftViolation(StartGift gift, StartGiftReferenceData data) {
        ContentPath path = SELECTED_GIFT_IDS.index(gift.index());
        return gift.repeated()
                ? Stream.of(StartGiftViolations.duplicateGift(path, SELECTED_GIFT_IDS, gift.giftId()))
                : data.hasEgoGift(gift.giftId())
                        ? Stream.empty()
                        : Stream.of(StartGiftViolations.unknownGift(path, SELECTED_GIFT_IDS, gift.giftId()));
    }

    private static Stream<Violation> keywordViolations(Optional<String> keyword, int giftCount, List<StartGift> gifts,
                                                       StartGiftSeason season) {
        return keyword
                .map(chosen -> season.hasStartGiftKeyword(chosen)
                        ? outsidePool(chosen, gifts, season.startGiftPool(chosen))
                        : Stream.of(StartGiftViolations.unknownKeyword(SELECTED_GIFT_KEYWORD, chosen)))
                .orElseGet(() -> giftCount == 0
                        ? Stream.empty()
                        : Stream.of(StartGiftViolations.missingKeyword(SELECTED_GIFT_IDS)));
    }

    private static Stream<Violation> outsidePool(String keyword, List<StartGift> gifts, Set<String> pool) {
        return gifts.stream()
                .filter(gift -> !gift.repeated() && !pool.contains(gift.giftId()))
                .map(gift -> StartGiftViolations.outsidePool(SELECTED_GIFT_IDS.index(gift.index()), SELECTED_GIFT_IDS,
                        keyword, gift.giftId()));
    }
}
