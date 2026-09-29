package org.danteplanner.backend.planner.validation.startgift;

import org.danteplanner.backend.planner.validation.ContentPath;
import org.danteplanner.backend.planner.validation.ErrorCode;
import org.danteplanner.backend.planner.validation.Violation;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class StartGiftRulesTest {

    private record Pools(Map<String, Set<String>> byKeyword) implements StartGiftSeason {

        @Override
        public boolean hasStartGiftKeyword(String keyword) {
            return byKeyword.containsKey(keyword);
        }

        @Override
        public Set<String> startGiftPool(String keyword) {
            return byKeyword.get(keyword);
        }
    }

    private static final StartGiftReferenceData GIFTS = Set.of("9001", "9002", "9003")::contains;
    private static final StartGiftSeason SEASON = new Pools(Map.of(
            "Combustion", Set.of("9001", "9002"),
            "Slash", Set.of("9003")));
    private static final ContentPath KEYWORD = ContentPath.of("selectedGiftKeyword");
    private static final ContentPath GIFT_IDS = ContentPath.of("selectedGiftIds");

    @Test
    void check_WhenKeywordAndGiftsAreEmpty_ReportsNothing() {
        assertThat(StartGiftRules.check(Optional.empty(), List.of(), GIFTS, SEASON)).isEmpty();
    }

    @Test
    void check_WhenTheGiftsAreInTheKeywordsPool_ReportsNothing() {
        assertThat(StartGiftRules.check(Optional.of("Combustion"), List.of("9001", "9002"), GIFTS, SEASON)).isEmpty();
    }

    @Test
    void check_WhenAGiftRepeats_ReportsTheRepeat() {
        assertThat(StartGiftRules.check(Optional.of("Combustion"), List.of("9002", "9001", "9002"), GIFTS, SEASON))
                .containsExactly(new Violation(ErrorCode.DUPLICATE_STARTGIFT, GIFT_IDS.index(2),
                        "Duplicate value '9002' in selectedGiftIds"));
    }

    @Test
    void check_WhenGiftsHaveNoKeyword_ReportsTheMissingKeyword() {
        assertThat(StartGiftRules.check(Optional.empty(), List.of("9001"), GIFTS, SEASON)).containsExactly(
                new Violation(ErrorCode.MISSING_STARTGIFT_KEYWORD, GIFT_IDS,
                        "selectedGiftIds requires a selectedGiftKeyword"));
    }

    @Test
    void check_WhenTheKeywordIsUnknown_ReportsItAndSkipsThePool() {
        assertThat(StartGiftRules.check(Optional.of("Nonsense"), List.of("9003"), GIFTS, SEASON)).containsExactly(
                new Violation(ErrorCode.UNKNOWN_STARTGIFT_KEYWORD, KEYWORD,
                        "selectedGiftKeyword ID 'Nonsense' not found or invalid"));
    }

    @Test
    void check_WhenAnUnknownGiftIsOutsideThePool_ReportsItOnlyAsUnknown() {
        assertThat(StartGiftRules.check(Optional.of("Combustion"), List.of("9999"), GIFTS, SEASON)).containsExactly(
                new Violation(ErrorCode.UNKNOWN_EGOGIFT, GIFT_IDS.index(0),
                        "selectedGiftIds ID '9999' not found or invalid"));
    }

    @Test
    void check_WhenARepeatedGiftIsOutsideThePool_ReportsThePoolOnceAndTheRepeat() {
        assertThat(StartGiftRules.check(Optional.of("Slash"), List.of("9001", "9001"), GIFTS, SEASON))
                .containsExactly(
                        new Violation(ErrorCode.MISMATCHED_STARTGIFT, GIFT_IDS.index(0),
                                "start gift '9001' is not in the 'Slash' keyword pool"),
                        new Violation(ErrorCode.DUPLICATE_STARTGIFT, GIFT_IDS.index(1),
                                "Duplicate value '9001' in selectedGiftIds"));
    }

    @Test
    void check_WhenAKeywordHasNoGifts_ReportsNothing() {
        assertThat(StartGiftRules.check(Optional.of("Combustion"), List.of(), GIFTS, SEASON)).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"19001", "29001"})
    void check_WhenAnEnhancedGiftsBaseIsInThePool_ReportsNothing(String giftId) {
        StartGiftReferenceData enhancedGifts = id -> id.endsWith("9001");

        assertThat(StartGiftRules.check(Optional.of("Combustion"), List.of(giftId), enhancedGifts, SEASON)).isEmpty();
    }

    @Test
    void check_WhenAnEnhancedGiftsBaseIsOutsideThePool_ReportsItsRawId() {
        StartGiftReferenceData enhancedGifts = id -> id.endsWith("9003");

        assertThat(StartGiftRules.check(Optional.of("Combustion"), List.of("19003"), enhancedGifts, SEASON))
                .containsExactly(new Violation(ErrorCode.MISMATCHED_STARTGIFT, GIFT_IDS.index(0),
                        "start gift '19003' is not in the 'Combustion' keyword pool"));
    }
}
