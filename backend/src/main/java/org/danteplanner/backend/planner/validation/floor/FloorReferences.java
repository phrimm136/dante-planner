package org.danteplanner.backend.planner.validation.floor;

import org.danteplanner.backend.planner.validation.ContentPath;
import org.danteplanner.backend.planner.validation.Positions;
import org.danteplanner.backend.planner.validation.Violation;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.IntStream;
import java.util.stream.Stream;

public final class FloorReferences {

    private FloorReferences() {
    }

    public static List<Violation> check(FloorBoundary.Parsed parsed, FloorReferenceData data) {
        if (!parsed.violations().isEmpty()) {
            return List.of();
        }
        List<FloorSelection> floors = parsed.accepted();
        return IntStream.range(0, floors.size())
                .boxed()
                .flatMap(index -> Stream.concat(
                        themePackViolations(index, floors.get(index), data),
                        giftViolations(index, floors.get(index), data)))
                .sorted(Violation.BY_PATH_THEN_CODE)
                .toList();
    }

    private static Stream<Violation> themePackViolations(int index, FloorSelection floor, FloorReferenceData data) {
        return floor.themePack().chosenId()
                .filter(themePackId -> !data.hasThemePack(themePackId))
                .map(themePackId -> FloorViolations.unknownThemePack(FloorBoundary.themePackPath(index), themePackId))
                .stream();
    }

    private static Stream<Violation> giftViolations(int index, FloorSelection floor, FloorReferenceData data) {
        List<String> giftIds = floor.giftIds();
        Map<String, Integer> firstIndex = Positions.firstIndexOf(giftIds);
        Optional<String> themePack = floor.themePack().chosenId();
        return IntStream.range(0, giftIds.size())
                .filter(giftIndex -> firstIndex.get(giftIds.get(giftIndex)) == giftIndex)
                .mapToObj(giftIndex -> giftViolation(FloorBoundary.giftIdsPath(index).index(giftIndex),
                        giftIds.get(giftIndex), themePack, data))
                .flatMap(Optional::stream);
    }

    private static Optional<Violation> giftViolation(ContentPath path, String giftId, Optional<String> themePack,
                                                     FloorReferenceData data) {
        return data.hasEgoGift(giftId)
                ? themePack.filter(themePackId -> !data.isGiftAffordableForThemePack(giftId, themePackId))
                        .map(themePackId -> FloorViolations.giftNotAffordable(path, giftId, themePackId))
                : Optional.of(FloorViolations.unknownGift(path, giftId));
    }
}
