package org.danteplanner.backend.planner.validation.floor;

import org.danteplanner.backend.planner.entity.MDCategory;
import org.danteplanner.backend.planner.validation.ContentPath;
import org.danteplanner.backend.planner.validation.ErrorCode;
import org.danteplanner.backend.planner.validation.Violation;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.danteplanner.backend.planner.validation.floor.FloorFixtures.TABLE;
import static org.danteplanner.backend.planner.validation.floor.FloorFixtures.content;
import static org.danteplanner.backend.planner.validation.floor.FloorFixtures.json;

class FloorBoundaryTest {

    private static final FloorSelection SALVAGED_PACK_1001 =
            new FloorSelection(ThemePack.chosen("1001"), Difficulty.of(0), List.of());

    @Test
    void parse_WhenFloorIsWellFormed_AcceptsItsTypedSelection() {
        FloorBoundary.Parsed parsed = parse("[{\"themePackId\":\"1001\",\"difficulty\":1,\"giftIds\":[\"9001\",\"9002\"]}]");

        assertThat(parsed.violations()).isEmpty();
        assertThat(parsed.floors()).containsExactly(
                new ParsedFloor.Accepted(new FloorSelection(ThemePack.chosen("1001"), Difficulty.of(1), List.of("9001", "9002"))));
    }

    @Test
    void parse_WhenThemePackIdIsNull_AcceptsAnAbsentPack() {
        FloorBoundary.Parsed parsed = parse("[{\"themePackId\":null,\"difficulty\":0,\"giftIds\":[]}]");

        assertThat(parsed.violations()).isEmpty();
        assertThat(parsed.floors()).containsExactly(
                new ParsedFloor.Accepted(new FloorSelection(ThemePack.none(), Difficulty.of(0), List.of())));
    }

    @Test
    void parse_WhenThemePackIdIsEmptyString_RejectsTheFloorAndSalvagesAnAbsentPack() {
        FloorBoundary.Parsed parsed = parse("[{\"themePackId\":\"\",\"difficulty\":0,\"giftIds\":[]}]");

        assertThat(parsed.floors()).containsExactly(
                new ParsedFloor.Rejected(new FloorSelection(ThemePack.none(), Difficulty.of(0), List.of())));
        assertThat(parsed.violations()).extracting(Violation::code, violation -> violation.path().render())
                .containsExactly(tuple(ErrorCode.INVALID_FIELD_TYPE, "floorSelections[0].themePackId"));
    }

    @Test
    void parse_WhenThemePackIdIsAbsent_RejectsTheFloorAsMissingTheField() {
        FloorBoundary.Parsed parsed = parse("[{\"difficulty\":0,\"giftIds\":[]}]");

        assertThat(parsed.floors()).containsExactly(
                new ParsedFloor.Rejected(new FloorSelection(ThemePack.none(), Difficulty.of(0), List.of())));
        assertThat(parsed.violations()).extracting(Violation::code, violation -> violation.path().render())
                .containsExactly(tuple(ErrorCode.MISSING_REQUIRED_FIELD, "floorSelections[0].themePackId"));
    }

    @Test
    void chosenThemePack_WhenIdIsEmpty_IsRefused() {
        assertThatThrownBy(() -> ThemePack.chosen("")).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("a chosen theme pack id is never empty");
    }

    @Test
    void parse_WhenThemePackIdIsNotAString_RejectsTheFloorAndSalvagesThePackAsText() {
        FloorBoundary.Parsed parsed = parse("[{\"themePackId\":1001,\"difficulty\":0,\"giftIds\":[]}]");

        assertThat(parsed.floors()).containsExactly(new ParsedFloor.Rejected(SALVAGED_PACK_1001));
        assertThat(parsed.violations()).extracting(Violation::path)
                .containsExactly(ContentPath.of("floorSelections").index(0).field("themePackId"));
    }

    @Test
    void parse_WhenGiftIdsAreNotAnArray_RejectsTheFloorWithNoGifts() {
        FloorBoundary.Parsed parsed = parse("[{\"themePackId\":\"1001\",\"difficulty\":0,\"giftIds\":5}]");

        assertThat(parsed.floors()).containsExactly(new ParsedFloor.Rejected(SALVAGED_PACK_1001));
        assertThat(parsed.violations()).extracting(Violation::path)
                .containsExactly(ContentPath.of("floorSelections").index(0).field("giftIds"));
    }

    @Test
    void parse_WhenARejectedFloorMixesStringAndNumericGifts_SalvagesEveryGiftAsItsDigitString() {
        FloorBoundary.Parsed parsed = parse("[{\"themePackId\":\"1004\",\"giftIds\":[\"9002\",9005]}]");

        assertThat(parsed.floors()).containsExactly(new ParsedFloor.Rejected(
                new FloorSelection(ThemePack.chosen("1004"), Difficulty.unset(), List.of("9002", "9005"))));
    }

    @Test
    void parse_WhenARejectedFloorHasNonScalarFields_SalvagesNothingFromThem() {
        FloorBoundary.Parsed parsed = parse("[{\"themePackId\":{\"id\":\"1001\"},\"difficulty\":\"x\","
                + "\"giftIds\":[true,{\"id\":\"9001\"},[\"9002\"]]}]");

        assertThat(parsed.floors()).containsExactly(
                new ParsedFloor.Rejected(new FloorSelection(ThemePack.none(), Difficulty.unset(), List.of())));
    }

    @Test
    void parse_WhenARejectedFloorHasANonIntegralNumericPack_SalvagesItsTextAsTheJsonTableDoes() {
        FloorBoundary.Parsed parsed = parse("[{\"themePackId\":1004.0,\"difficulty\":0,\"giftIds\":[9006.0]}]");

        assertThat(parsed.floors()).containsExactly(new ParsedFloor.Rejected(
                new FloorSelection(ThemePack.chosen("1004.0"), Difficulty.of(0), List.of("9006.0"))));
    }

    @Test
    void parse_WhenDifficultyIsAnIntegralValuedFraction_AcceptsThatInteger() {
        FloorBoundary.Parsed parsed = parse("[{\"themePackId\":\"1001\",\"difficulty\":1.0,\"giftIds\":[]}]");

        assertThat(parsed.floors()).containsExactly(
                new ParsedFloor.Accepted(new FloorSelection(ThemePack.chosen("1001"), Difficulty.of(1), List.of())));
    }

    @Test
    void parse_WhenDifficultyIsAnIntegerBeyondInt_AcceptsAnOutOfRangeDifficulty() {
        FloorBoundary.Parsed parsed = parse("[{\"themePackId\":\"1001\",\"difficulty\":2147483648,\"giftIds\":[]}]");

        assertThat(parsed.floors()).containsExactly(new ParsedFloor.Accepted(new FloorSelection(
                ThemePack.chosen("1001"), Difficulty.outOfRange(new BigInteger("2147483648")), List.of())));
    }

    @Test
    void parse_WhenDifficultyIsNotAnInteger_RejectsTheFloorWithAnUnsetDifficulty() {
        FloorBoundary.Parsed parsed = parse("[{\"themePackId\":\"1001\",\"difficulty\":\"1\",\"giftIds\":[]},"
                + "{\"themePackId\":\"1002\",\"difficulty\":1.5,\"giftIds\":[]},"
                + "{\"themePackId\":\"1003\",\"difficulty\":1e400,\"giftIds\":[]}]");

        assertThat(parsed.floors()).containsExactly(
                new ParsedFloor.Rejected(new FloorSelection(ThemePack.chosen("1001"), Difficulty.unset(), List.of())),
                new ParsedFloor.Rejected(new FloorSelection(ThemePack.chosen("1002"), Difficulty.unset(), List.of())),
                new ParsedFloor.Rejected(new FloorSelection(ThemePack.chosen("1003"), Difficulty.unset(), List.of())));
        assertThat(parsed.violations()).extracting(Violation::code, violation -> violation.path().render())
                .containsExactly(
                        tuple(ErrorCode.INVALID_FIELD_TYPE, "floorSelections[0].difficulty"),
                        tuple(ErrorCode.INVALID_FIELD_TYPE, "floorSelections[1].difficulty"),
                        tuple(ErrorCode.INVALID_FIELD_TYPE, "floorSelections[2].difficulty"));
    }

    @Test
    void parse_WhenFloorIsNotAnObject_RejectsItWithNothingSalvaged() {
        FloorBoundary.Parsed parsed = parse("[{\"themePackId\":\"1001\",\"difficulty\":0,\"giftIds\":[]},5]");

        assertThat(parsed.floors()).containsExactly(new ParsedFloor.Accepted(SALVAGED_PACK_1001),
                new ParsedFloor.Rejected(new FloorSelection(ThemePack.none(), Difficulty.unset(), List.of())));
        assertThat(parsed.violations()).extracting(Violation::path)
                .containsExactly(ContentPath.of("floorSelections").index(1));
    }

    @Test
    void parse_WhenFloorSelectionsIsNotAnArray_ParsesNoFloorsBesideTheViolation() {
        FloorBoundary.Parsed parsed = FloorBoundary.parse(content(json("{}")), TABLE.layout(MDCategory.F5));

        assertThat(parsed.floors()).isEmpty();
        assertThat(parsed.violations()).extracting(Violation::path).containsExactly(ContentPath.of("floorSelections"));
    }

    @Test
    void parse_WhenFloorsLieAtOrPastTheCount_ShapeChecksThemButParsesNone() {
        FloorBoundary.Parsed parsed = parse("[{\"themePackId\":\"1001\",\"difficulty\":0,\"giftIds\":[]},"
                + "{\"themePackId\":\"1002\",\"difficulty\":0,\"giftIds\":[]},"
                + "{\"themePackId\":\"1003\",\"difficulty\":0,\"giftIds\":[]},"
                + "{\"themePackId\":\"1004\",\"difficulty\":0,\"giftIds\":[]},"
                + "{\"themePackId\":\"1005\",\"difficulty\":0,\"giftIds\":[]},5]");

        assertThat(parsed.floors()).hasSize(TABLE.floorCount(MDCategory.F5))
                .allMatch(ParsedFloor.Accepted.class::isInstance);
        assertThat(parsed.violations()).extracting(Violation::path)
                .containsExactly(ContentPath.of("floorSelections").index(5));
    }

    @Test
    void parse_WhenAViolationLiesOutsideTheFloors_CarriesOnlyTheFloorViolations() {
        FloorBoundary.Parsed parsed = FloorBoundary.parse(
                content(json("[{\"themePackId\":\"1001\",\"difficulty\":0,\"giftIds\":[]}]")).put("mystery", 1),
                TABLE.layout(MDCategory.F5));

        assertThat(parsed.violations()).isEmpty();
        assertThat(parsed.floors()).containsExactly(new ParsedFloor.Accepted(SALVAGED_PACK_1001));
    }

    @Test
    void parse_WhenFloorsParse_CarriesTheCategoryLayout() {
        FloorBoundary.Parsed parsed = FloorBoundary.parse(content(json("[]")), TABLE.layout(MDCategory.F15));

        assertThat(parsed.layout()).isEqualTo(TABLE.layout(MDCategory.F15));
    }

    @Test
    void parsed_WhenItHoldsMoreFloorsThanItsLayoutCounts_FailsToConstruct() {
        List<ParsedFloor> sixFloors = Collections.nCopies(TABLE.floorCount(MDCategory.F5) + 1,
                new ParsedFloor.Accepted(new FloorSelection(ThemePack.none(), Difficulty.unset(), List.of())));

        assertThatThrownBy(() -> new FloorBoundary.Parsed(TABLE.layout(MDCategory.F5), sixFloors, List.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("6 parsed floors exceed the layout's 5 floors");
    }

    @Test
    void accepted_WhenSomeFloorsFailTheSchema_ListsOnlyTheAcceptedFloorsInOrder() {
        FloorBoundary.Parsed parsed = parse("[{\"themePackId\":\"1001\",\"difficulty\":0,\"giftIds\":[]},5,"
                + "{\"themePackId\":\"1003\",\"difficulty\":0,\"giftIds\":[]},{\"themePackId\":7}]");

        assertThat(parsed.accepted()).containsExactly(SALVAGED_PACK_1001,
                new FloorSelection(ThemePack.chosen("1003"), Difficulty.of(0), List.of()));
    }

    private static FloorBoundary.Parsed parse(String floorSelections) {
        return FloorFixtures.parse(json(floorSelections), MDCategory.F5);
    }
}
