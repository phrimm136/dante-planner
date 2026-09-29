package org.danteplanner.backend.planner.validation.startgift;

import com.fasterxml.jackson.databind.node.MissingNode;
import org.danteplanner.backend.planner.validation.ContentPath;
import org.danteplanner.backend.planner.validation.ErrorCode;
import org.danteplanner.backend.planner.validation.Violation;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.danteplanner.backend.planner.validation.ContentFixtures.json;

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

    private static final StartGiftReferenceData GIFTS = Set.of("9001", "9002", "9003", "gift_a")::contains;
    private static final StartGiftSeason SEASON = new Pools(Map.of(
            "Combustion", Set.of("9001", "9002", "gift_a"),
            "Slash", Set.of("9003")));
    private static final ContentPath KEYWORD = ContentPath.of("selectedGiftKeyword");
    private static final ContentPath GIFT_IDS = ContentPath.of("selectedGiftIds");

    @Test
    void check_WhenKeywordAndGiftsAreAbsent_ReportsNothing() {
        assertThat(StartGiftRules.check(MissingNode.getInstance(), MissingNode.getInstance(), GIFTS, SEASON))
                .isEmpty();
    }

    @Test
    void check_WhenTheGiftsAreInTheKeywordsPool_ReportsNothing() {
        assertThat(StartGiftRules.check(json("\"Combustion\""), json("[\"9001\",\"gift_a\"]"), GIFTS, SEASON))
                .isEmpty();
    }

    @Test
    @DisplayName("mixed-type gift array reports the wrong type and the repeat, once each")
    void check_WhenArrayMixesTypesAndRepeats_ReportsTypeThenDuplicate() {
        assertThat(StartGiftRules.check(json("\"Combustion\""), json("[\"gift_a\",42,\"gift_a\"]"), GIFTS, SEASON))
                .containsExactly(
                        new Violation(ErrorCode.INVALID_FIELD_TYPE, GIFT_IDS.index(1),
                                "Field 'selectedGiftIds[1]' must be string, got number 42"),
                        new Violation(ErrorCode.DUPLICATE_STARTGIFT, GIFT_IDS.index(2),
                                "Duplicate value 'gift_a' in selectedGiftIds"));
    }

    @Test
    void check_WhenAGiftIsNotAString_ReportsItOnce() {
        assertThat(StartGiftRules.check(json("\"Combustion\""), json("[9001]"), GIFTS, SEASON)).containsExactly(
                new Violation(ErrorCode.INVALID_FIELD_TYPE, GIFT_IDS.index(0),
                        "Field 'selectedGiftIds[0]' must be string, got number 9001"));
    }

    @Test
    void check_WhenGiftsAreNotAnArray_ReportsTheirKindAndNeedNoKeyword() {
        assertThat(StartGiftRules.check(MissingNode.getInstance(), json("\"none\""), GIFTS, SEASON)).containsExactly(
                new Violation(ErrorCode.INVALID_FIELD_TYPE, GIFT_IDS,
                        "Field 'selectedGiftIds' must be array, got string \"none\""));
    }

    @Test
    void check_WhenGiftsHaveNoKeyword_ReportsTheMissingKeyword() {
        assertThat(StartGiftRules.check(json("null"), json("[\"9001\"]"), GIFTS, SEASON)).containsExactly(
                new Violation(ErrorCode.MISSING_STARTGIFT_KEYWORD, GIFT_IDS,
                        "selectedGiftIds requires a selectedGiftKeyword"));
    }

    @Test
    void check_WhenTheKeywordIsNotAString_ReportsOnlyItsType() {
        assertThat(StartGiftRules.check(json("123"), json("[\"9001\"]"), GIFTS, SEASON)).containsExactly(
                new Violation(ErrorCode.INVALID_FIELD_TYPE, KEYWORD,
                        "Field 'selectedGiftKeyword' must be string or null, got number 123"));
    }

    @Test
    void check_WhenTheKeywordIsUnknown_ReportsItAndSkipsThePool() {
        assertThat(StartGiftRules.check(json("\"Nonsense\""), json("[\"9003\"]"), GIFTS, SEASON)).containsExactly(
                new Violation(ErrorCode.UNKNOWN_STARTGIFT_KEYWORD, KEYWORD,
                        "selectedGiftKeyword ID 'Nonsense' not found or invalid"));
    }

    @Test
    void check_WhenAnUnknownGiftIsOutsideThePool_ReportsItOnlyAsUnknown() {
        assertThat(StartGiftRules.check(json("\"Combustion\""), json("[\"9999\"]"), GIFTS, SEASON)).containsExactly(
                new Violation(ErrorCode.UNKNOWN_EGOGIFT, GIFT_IDS.index(0),
                        "selectedGiftIds ID '9999' not found or invalid"));
    }

    @Test
    void check_WhenARepeatedGiftIsOutsideThePool_ReportsThePoolOnceAndTheRepeat() {
        assertThat(StartGiftRules.check(json("\"Slash\""), json("[\"9001\",\"9001\"]"), GIFTS, SEASON))
                .containsExactly(
                        new Violation(ErrorCode.MISMATCHED_STARTGIFT, GIFT_IDS.index(0),
                                "start gift '9001' is not in the 'Slash' keyword pool"),
                        new Violation(ErrorCode.DUPLICATE_STARTGIFT, GIFT_IDS.index(1),
                                "Duplicate value '9001' in selectedGiftIds"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "  "})
    void check_WhenTheKeywordIsBlankWithGifts_ReportsTheMissingKeyword(String keyword) {
        assertThat(StartGiftRules.check(json("\"" + keyword + "\""), json("[\"9001\"]"), GIFTS, SEASON))
                .containsExactly(new Violation(ErrorCode.MISSING_STARTGIFT_KEYWORD, GIFT_IDS,
                        "selectedGiftIds requires a selectedGiftKeyword"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "  "})
    void check_WhenTheKeywordIsBlankWithoutGifts_ReportsNothing(String keyword) {
        assertThat(StartGiftRules.check(json("\"" + keyword + "\""), MissingNode.getInstance(), GIFTS, SEASON))
                .isEmpty();
    }

    @Test
    void check_WhenTheKeywordIsAnArrayWithGifts_ReportsOnlyItsType() {
        assertThat(StartGiftRules.check(json("[]"), json("[\"9001\"]"), GIFTS, SEASON)).containsExactly(
                new Violation(ErrorCode.INVALID_FIELD_TYPE, KEYWORD,
                        "Field 'selectedGiftKeyword' must be string or null, got array"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"19001", "29001"})
    void check_WhenAnEnhancedGiftsBaseIsInThePool_ReportsNothing(String giftId) {
        StartGiftReferenceData enhancedGifts = id -> id.endsWith("9001");

        assertThat(StartGiftRules.check(json("\"Combustion\""), json("[\"" + giftId + "\"]"), enhancedGifts,
                SEASON)).isEmpty();
    }

    @Test
    void check_WhenAnEnhancedGiftsBaseIsOutsideThePool_ReportsItsRawId() {
        StartGiftReferenceData enhancedGifts = id -> id.endsWith("9003");

        assertThat(StartGiftRules.check(json("\"Combustion\""), json("[\"19003\"]"), enhancedGifts, SEASON))
                .containsExactly(new Violation(ErrorCode.MISMATCHED_STARTGIFT, GIFT_IDS.index(0),
                        "start gift '19003' is not in the 'Combustion' keyword pool"));
    }

    @Test
    void check_WhenABlankGiftIsSelected_ReportsItOnlyAsUnknown() {
        assertThat(StartGiftRules.check(json("\"Combustion\""), json("[\"\"]"), GIFTS, SEASON)).containsExactly(
                new Violation(ErrorCode.UNKNOWN_EGOGIFT, GIFT_IDS.index(0), "selectedGiftIds ID '' not found or invalid"));
    }
}
