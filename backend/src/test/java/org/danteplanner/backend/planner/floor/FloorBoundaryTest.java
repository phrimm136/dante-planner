package org.danteplanner.backend.planner.floor;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.danteplanner.backend.planner.validation.ErrorCode;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

class FloorBoundaryTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final int FLOOR_COUNT = 5;
    private static final FloorSelection SALVAGED_PACK_1001 =
            new FloorSelection(ThemePack.chosen("1001"), Difficulty.of(0), List.of());

    @Test
    void parse_WhenFloorIsWellFormed_YieldsItsTypedSelection() {
        FloorBoundary.Parsed parsed = parse("[{\"themePackId\":\"1001\",\"difficulty\":1,\"giftIds\":[\"9001\",\"9002\"]}]");

        assertThat(parsed.violations()).isEmpty();
        assertThat(parsed.floors()).containsExactly(
                new ParsedFloor.Accepted(new FloorSelection(ThemePack.chosen("1001"), Difficulty.of(1), List.of("9001", "9002"))));
    }

    @Test
    void parse_WhenThemePackIdIsAbsent_YieldsAbsentPack() {
        assertSingleFloor(parse("[{\"difficulty\":0,\"giftIds\":[]}]"),
                new FloorSelection(ThemePack.none(), Difficulty.of(0), List.of()));
    }

    @Test
    void parse_WhenThemePackIdIsNull_YieldsAbsentPack() {
        assertSingleFloor(parse("[{\"themePackId\":null,\"difficulty\":0,\"giftIds\":[]}]"),
                new FloorSelection(ThemePack.none(), Difficulty.of(0), List.of()));
    }

    @Test
    void parse_WhenThemePackIdIsEmptyString_YieldsAbsentPack() {
        assertSingleFloor(parse("[{\"themePackId\":\"\",\"difficulty\":0,\"giftIds\":[]}]"),
                new FloorSelection(ThemePack.none(), Difficulty.of(0), List.of()));
    }

    @Test
    void chosenThemePack_WhenIdIsEmpty_IsRefused() {
        assertThatThrownBy(() -> ThemePack.chosen("")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void parse_WhenThemePackIdIsNotAString_RejectsTheFloorAtThePackPath() {
        FloorBoundary.Parsed parsed = parse("[{\"themePackId\":1001,\"difficulty\":0,\"giftIds\":[]}]");

        assertThat(parsed.floors()).containsExactly(new ParsedFloor.Rejected(0,
                new FloorSelection(ThemePack.chosen("1001"), Difficulty.of(0), List.of())));
        assertThat(parsed.violations()).containsExactly(new Violation(ErrorCode.INVALID_FIELD_TYPE,
                "floorSelections[0].themePackId",
                "Field 'floorSelections[0].themePackId' must be string, got number 1001"));
    }

    @Test
    void parse_WhenGiftIdsAreAbsent_YieldsNoGifts() {
        assertSingleFloor(parse("[{\"themePackId\":\"1001\",\"difficulty\":0}]"),
                new FloorSelection(ThemePack.chosen("1001"), Difficulty.of(0), List.of()));
    }

    @Test
    void parse_WhenGiftIdsAreNotAnArray_RejectsTheFloorAtTheGiftsPath() {
        FloorBoundary.Parsed parsed = parse("[{\"themePackId\":\"1001\",\"difficulty\":0,\"giftIds\":5}]");

        assertThat(parsed.floors()).containsExactly(new ParsedFloor.Rejected(0, SALVAGED_PACK_1001));
        assertThat(parsed.violations()).containsExactly(new Violation(ErrorCode.INVALID_FIELD_TYPE,
                "floorSelections[0].giftIds", "Field 'floorSelections[0].giftIds' must be array, got number 5"));
    }

    @Test
    void parse_WhenGiftIdsAreNull_RejectsTheFloorAtTheGiftsPath() {
        FloorBoundary.Parsed parsed = parse("[{\"themePackId\":\"1001\",\"difficulty\":0,\"giftIds\":null}]");

        assertThat(parsed.floors()).containsExactly(new ParsedFloor.Rejected(0, SALVAGED_PACK_1001));
        assertThat(parsed.violations()).containsExactly(new Violation(ErrorCode.INVALID_FIELD_TYPE,
                "floorSelections[0].giftIds", "Field 'floorSelections[0].giftIds' must be array, got null"));
    }

    @Test
    void parse_WhenAGiftIdIsNotAString_RejectsTheFloorAtThatElement() {
        FloorBoundary.Parsed parsed = parse("[{\"themePackId\":\"1001\",\"difficulty\":0,\"giftIds\":[\"9001\",9002]}]");

        assertThat(parsed.floors()).containsExactly(new ParsedFloor.Rejected(0,
                new FloorSelection(ThemePack.chosen("1001"), Difficulty.of(0), List.of("9001", "9002"))));
        assertThat(parsed.violations()).containsExactly(new Violation(ErrorCode.INVALID_FIELD_TYPE,
                "floorSelections[0].giftIds[1]",
                "Field 'floorSelections[0].giftIds[1]' must be string, got number 9002"));
    }

    @Test
    void parse_WhenDifficultyIsAbsent_YieldsAbsentDifficulty() {
        assertSingleFloor(parse("[{\"themePackId\":\"1001\",\"giftIds\":[]}]"),
                new FloorSelection(ThemePack.chosen("1001"), Difficulty.unset(), List.of()));
    }

    @Test
    void parse_WhenDifficultyIsNotAnInteger_RejectsTheFloorAtTheDifficultyPath() {
        FloorBoundary.Parsed parsed = parse("[{\"themePackId\":\"1001\",\"difficulty\":\"1\",\"giftIds\":[]},"
                + "{\"themePackId\":\"1002\",\"difficulty\":1.5,\"giftIds\":[]}]");

        assertThat(parsed.floors()).containsExactly(
                new ParsedFloor.Rejected(0, new FloorSelection(ThemePack.chosen("1001"), Difficulty.unset(), List.of())),
                new ParsedFloor.Rejected(1, new FloorSelection(ThemePack.chosen("1002"), Difficulty.unset(), List.of())));
        assertThat(parsed.violations()).extracting(Violation::code, Violation::path).containsExactly(
                tuple(ErrorCode.INVALID_FIELD_TYPE, "floorSelections[0].difficulty"),
                tuple(ErrorCode.INVALID_FIELD_TYPE, "floorSelections[1].difficulty"));
    }

    @Test
    void parse_WhenDifficultyIsAnIntegralValuedFraction_YieldsThatInteger() {
        assertSingleFloor(parse("[{\"themePackId\":\"1001\",\"difficulty\":1.0,\"giftIds\":[]}]"),
                new FloorSelection(ThemePack.chosen("1001"), Difficulty.of(1), List.of()));
    }

    @Test
    void parse_WhenDifficultyIsAnIntegerBeyondInt_YieldsAnOutOfRangeDifficulty() {
        assertSingleFloor(parse("[{\"themePackId\":\"1001\",\"difficulty\":2147483648,\"giftIds\":[]}]"),
                new FloorSelection(ThemePack.chosen("1001"), Difficulty.outOfRange(new BigInteger("2147483648")),
                        List.of()));
    }

    @Test
    void parse_WhenDifficultyIsANonIntegralNumber_RejectsTheFloorAtTheDifficultyPath() {
        FloorBoundary.Parsed parsed = parse("[{\"themePackId\":\"1001\",\"difficulty\":1.5,\"giftIds\":[]}]");

        assertThat(parsed.floors()).containsExactly(new ParsedFloor.Rejected(0,
                new FloorSelection(ThemePack.chosen("1001"), Difficulty.unset(), List.of())));
        assertThat(parsed.violations()).containsExactly(new Violation(ErrorCode.INVALID_FIELD_TYPE,
                "floorSelections[0].difficulty", "Field 'floorSelections[0].difficulty' must be integer, got number 1.5"));
    }

    @Test
    void parse_WhenDifficultyIsNull_RejectsTheFloorAtTheDifficultyPath() {
        FloorBoundary.Parsed parsed = parse("[{\"themePackId\":\"1001\",\"difficulty\":null,\"giftIds\":[]}]");

        assertThat(parsed.floors()).containsExactly(new ParsedFloor.Rejected(0,
                new FloorSelection(ThemePack.chosen("1001"), Difficulty.unset(), List.of())));
        assertThat(parsed.violations()).containsExactly(new Violation(ErrorCode.INVALID_FIELD_TYPE,
                "floorSelections[0].difficulty", "Field 'floorSelections[0].difficulty' must be integer, got null"));
    }

    @Test
    void parse_WhenFloorIsNotAnObject_RejectsItAtTheFloorPath() {
        FloorBoundary.Parsed parsed = parse("[{\"themePackId\":\"1001\",\"difficulty\":0,\"giftIds\":[]},5]");

        assertThat(parsed.floors()).hasSize(2);
        assertThat(parsed.floors().get(0)).isInstanceOf(ParsedFloor.Accepted.class);
        assertThat(parsed.floors().get(1)).isEqualTo(
                new ParsedFloor.Rejected(1, new FloorSelection(ThemePack.none(), Difficulty.unset(), List.of())));
        assertThat(parsed.violations()).containsExactly(new Violation(ErrorCode.INVALID_FIELD_TYPE,
                "floorSelections[1]", "Field 'floorSelections[1]' must be object, got number 5"));
    }

    @Test
    void parse_WhenARejectedFloorMixesStringAndNumericGifts_SalvagesEveryGiftAsItsDigitString() {
        FloorBoundary.Parsed parsed = parse("[{\"themePackId\":\"1004\",\"giftIds\":[\"9002\",9005]}]");

        assertThat(parsed.floors()).containsExactly(new ParsedFloor.Rejected(0,
                new FloorSelection(ThemePack.chosen("1004"), Difficulty.unset(), List.of("9002", "9005"))));
    }

    @Test
    void parse_WhenARejectedFloorHasNonScalarFields_SalvagesNothingFromThem() {
        FloorBoundary.Parsed parsed = parse("[{\"themePackId\":{\"id\":\"1001\"},\"difficulty\":\"x\","
                + "\"giftIds\":[true,{\"id\":\"9001\"},[\"9002\"]]}]");

        assertThat(parsed.floors()).containsExactly(
                new ParsedFloor.Rejected(0, new FloorSelection(ThemePack.none(), Difficulty.unset(), List.of())));
        assertThat(parsed.violations()).extracting(Violation::path).containsExactly(
                "floorSelections[0].difficulty", "floorSelections[0].giftIds[0]", "floorSelections[0].giftIds[1]",
                "floorSelections[0].giftIds[2]", "floorSelections[0].themePackId");
    }

    @Test
    void parse_WhenARejectedFloorHasANonIntegralNumericPack_SalvagesItsTextAsTheJsonTableDoes() {
        FloorBoundary.Parsed parsed = parse("[{\"themePackId\":1004.0,\"difficulty\":0,\"giftIds\":[9006.0]}]");

        assertThat(parsed.floors()).containsExactly(new ParsedFloor.Rejected(0,
                new FloorSelection(ThemePack.chosen("1004.0"), Difficulty.of(0), List.of("9006.0"))));
    }

    @Test
    void parse_WhenFloorSelectionsIsNotAnArray_RejectsItAtTheFieldPath() {
        FloorBoundary.Parsed parsed = parse("{}");

        assertThat(parsed.floors()).isEmpty();
        assertThat(parsed.violations()).containsExactly(new Violation(ErrorCode.INVALID_FIELD_TYPE,
                "floorSelections", "Field 'floorSelections' must be array, got object"));
    }

    @Test
    void parse_WhenFloorsLieAtOrPastTheCount_LeavesThemUntouchedAndUnreported() {
        FloorBoundary.Parsed parsed = FloorBoundary.parse(json("[{\"themePackId\":\"1001\",\"difficulty\":0},"
                + "{\"themePackId\":\"1002\"},5,{\"themePackId\":7,\"giftIds\":7,\"difficulty\":\"x\"}]"), 2);

        assertThat(parsed.violations()).isEmpty();
        assertThat(parsed.floors()).containsExactly(
                new ParsedFloor.Accepted(new FloorSelection(ThemePack.chosen("1001"), Difficulty.of(0), List.of())),
                new ParsedFloor.Accepted(new FloorSelection(ThemePack.chosen("1002"), Difficulty.unset(), List.of())));
    }

    private static void assertSingleFloor(FloorBoundary.Parsed parsed, FloorSelection expected) {
        assertThat(parsed.violations()).isEmpty();
        assertThat(parsed.floors()).containsExactly(new ParsedFloor.Accepted(expected));
    }

    private static FloorBoundary.Parsed parse(String floorSelections) {
        return FloorBoundary.parse(json(floorSelections), FLOOR_COUNT);
    }

    private static JsonNode json(String text) {
        try {
            return MAPPER.readTree(text);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException(text, e);
        }
    }
}
