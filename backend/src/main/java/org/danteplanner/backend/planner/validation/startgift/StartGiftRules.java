package org.danteplanner.backend.planner.validation.startgift;

import org.danteplanner.backend.planner.validation.ContentPath;
import org.danteplanner.backend.planner.validation.EncodedGiftId;
import org.danteplanner.backend.planner.validation.GiftId;
import org.danteplanner.backend.planner.validation.Positions;
import org.danteplanner.backend.planner.validation.Violation;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

public final class StartGiftRules {

    private record StartGift(int index, String giftId, boolean repeated) {
    }

    private static final ContentPath SELECTED_GIFT_KEYWORD = ContentPath.of("selectedGiftKeyword");
    private static final ContentPath SELECTED_GIFT_IDS = ContentPath.of("selectedGiftIds");

    private StartGiftRules() {
    }

    public static List<Violation> check(Optional<String> selectedGiftKeyword, List<String> selectedGiftIds,
                                        StartGiftReferenceData data, StartGiftSeason season) {
        List<StartGift> gifts = gifts(selectedGiftIds);
        return Stream.concat(
                        gifts.stream().flatMap(gift -> giftViolation(gift, data)),
                        keywordViolations(selectedGiftKeyword, selectedGiftIds.size(), knownGifts(gifts, data),
                                season))
                .sorted(Violation.BY_PATH_THEN_CODE)
                .toList();
    }

    private static List<StartGift> gifts(List<String> giftIds) {
        Map<String, Integer> firstIndex = Positions.firstIndexOf(giftIds);
        return IntStream.range(0, giftIds.size())
                .mapToObj(index -> new StartGift(index, giftIds.get(index), firstIndex.get(giftIds.get(index)) < index))
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

    private static List<StartGift> knownGifts(List<StartGift> gifts, StartGiftReferenceData data) {
        return gifts.stream().filter(gift -> data.hasEgoGift(gift.giftId())).toList();
    }

    private static Stream<Violation> outsidePool(String keyword, List<StartGift> gifts, Set<String> pool) {
        Set<GiftId> poolBases = pool.stream().map(StartGiftRules::baseOf).collect(Collectors.toSet());
        return gifts.stream()
                .filter(gift -> !gift.repeated() && !poolBases.contains(baseOf(gift.giftId())))
                .map(gift -> StartGiftViolations.outsidePool(SELECTED_GIFT_IDS.index(gift.index()), SELECTED_GIFT_IDS,
                        keyword, gift.giftId()));
    }

    private static GiftId baseOf(String giftId) {
        return EncodedGiftId.parse(giftId).base();
    }
}
