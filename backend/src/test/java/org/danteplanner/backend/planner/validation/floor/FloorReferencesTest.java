package org.danteplanner.backend.planner.validation.floor;

import com.fasterxml.jackson.databind.JsonNode;
import org.danteplanner.backend.planner.entity.MDCategory;
import org.danteplanner.backend.planner.validation.ErrorCode;
import org.danteplanner.backend.planner.validation.Violation;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.danteplanner.backend.planner.validation.floor.FloorFixtures.TABLE;
import static org.danteplanner.backend.planner.validation.floor.FloorFixtures.floor;
import static org.danteplanner.backend.planner.validation.floor.FloorFixtures.floors;
import static org.danteplanner.backend.planner.validation.floor.FloorFixtures.json;

class FloorReferencesTest {

    private static final String KNOWN_PACK = "1001";
    private static final String UNKNOWN_PACK = "8888";
    private static final String AFFORDABLE_GIFT = "9001";
    private static final String UNAFFORDABLE_GIFT = "9002";
    private static final String UNKNOWN_GIFT = "7777";

    private static final FloorReferenceData DATA = new FloorReferenceData() {

        @Override
        public boolean hasThemePack(String themePackId) {
            return KNOWN_PACK.equals(themePackId);
        }

        @Override
        public boolean hasEgoGift(String giftId) {
            return !UNKNOWN_GIFT.equals(giftId);
        }

        @Override
        public boolean isGiftAffordableForThemePack(String giftId, String themePackId) {
            return !UNAFFORDABLE_GIFT.equals(giftId);
        }
    };

    @Test
    void check_WhenTheBoundaryReportedAViolation_RunsNoReferenceCheck() {
        List<Violation> violations = check(floors(floor(UNKNOWN_PACK, 0, UNKNOWN_GIFT), json("5")));

        assertThat(violations).isEmpty();
    }

    @Test
    void check_WhenEveryReferenceResolves_ReportsNothing() {
        assertThat(check(floors(floor(KNOWN_PACK, 0, AFFORDABLE_GIFT)))).isEmpty();
    }

    @Test
    void check_WhenThePackIsUnknown_StillChecksThatFloorsGifts() {
        List<Violation> violations = check(floors(floor(UNKNOWN_PACK, 0, UNKNOWN_GIFT, UNAFFORDABLE_GIFT)));

        assertThat(violations).extracting(Violation::code, violation -> violation.path().render(), Violation::message)
                .containsExactly(
                        tuple(ErrorCode.UNKNOWN_EGOGIFT_FLOOR, "floorSelections[0].giftIds[0]",
                                "floorSelections[0].giftIds[0] ID '7777' not found or invalid"),
                        tuple(ErrorCode.UNAFFORDABLE_EGOGIFT, "floorSelections[0].giftIds[1]",
                                "Gift '9002' is not affordable for theme pack '8888'"),
                        tuple(ErrorCode.UNKNOWN_THEMEPACK, "floorSelections[0].themePackId",
                                "floorSelections[0].themePackId ID '8888' not found or invalid"));
    }

    @Test
    void check_WhenAGiftRepeats_ChecksItOnceAtItsFirstPosition() {
        List<Violation> violations = check(floors(floor(KNOWN_PACK, 0, AFFORDABLE_GIFT, UNAFFORDABLE_GIFT,
                UNKNOWN_GIFT, UNAFFORDABLE_GIFT, UNKNOWN_GIFT)));

        assertThat(violations).extracting(Violation::code, violation -> violation.path().render()).containsExactly(
                tuple(ErrorCode.UNAFFORDABLE_EGOGIFT, "floorSelections[0].giftIds[1]"),
                tuple(ErrorCode.UNKNOWN_EGOGIFT_FLOOR, "floorSelections[0].giftIds[2]"));
    }

    @Test
    void check_WhenAFloorHasNoPack_ChecksNoAffordability() {
        List<Violation> violations = check(floors(floor(KNOWN_PACK, 0, UNAFFORDABLE_GIFT, UNKNOWN_GIFT).without("themePackId")));

        assertThat(violations).extracting(Violation::code).containsExactly(ErrorCode.UNKNOWN_EGOGIFT_FLOOR);
    }

    @Test
    void check_WhenSeveralFloorsFail_ReportsByPathThenCode() {
        List<Violation> violations = check(floors(floor(KNOWN_PACK, 0, UNAFFORDABLE_GIFT),
                floor(UNKNOWN_PACK, 0, AFFORDABLE_GIFT, "9003", "9004", "9005", "9006", "9007", "9008", "9009",
                        "9010", "9011", UNAFFORDABLE_GIFT)));

        assertThat(violations).extracting(violation -> violation.path().render()).containsExactly(
                "floorSelections[0].giftIds[0]",
                "floorSelections[1].giftIds[10]",
                "floorSelections[1].themePackId");
    }

    private static List<Violation> check(JsonNode floorSelections) {
        return FloorReferences.check(FloorBoundary.parse(floorSelections, TABLE.layout(MDCategory.F5)), DATA);
    }
}
