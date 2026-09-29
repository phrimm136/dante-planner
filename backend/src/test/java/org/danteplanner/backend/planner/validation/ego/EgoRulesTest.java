package org.danteplanner.backend.planner.validation.ego;

import org.danteplanner.backend.planner.validation.ContentPath;
import org.danteplanner.backend.planner.validation.ErrorCode;
import org.danteplanner.backend.planner.validation.SinnerEntry;
import org.danteplanner.backend.planner.validation.Violation;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.danteplanner.backend.planner.validation.ContentFixtures.equipmentOfSinnerFive;

class EgoRulesTest {

    private record KnownEgos(Map<String, Integer> ceilings, List<String> withoutCeiling) implements EgoReferenceData {

        @Override
        public boolean hasEgo(String egoId) {
            return ceilings.containsKey(egoId) || withoutCeiling.contains(egoId);
        }

        @Override
        public boolean belongsToSinner(String sinnerKey, String entityId) {
            return entityId.substring(1, 3).equals(sinnerKey);
        }

        @Override
        public Optional<Integer> maxThreadspin(String egoId) {
            return Optional.ofNullable(ceilings.get(egoId));
        }
    }

    private static final EgoReferenceData DATA =
            new KnownEgos(Map.of("20501", 4, "20601", 4, "20503", 5), List.of("20502"));
    private static final ContentPath EGOS = ContentPath.of("equipment").key("05").field("egos");
    private static final ContentPath ZAYIN = EGOS.field("ZAYIN");

    @Test
    void check_WhenEquipmentHasNoSinners_ReportsNothing() {
        assertThat(EgoRules.check(List.of(), DATA)).isEmpty();
    }

    @Test
    void check_WhenTheZayinIsKnownAndWithinItsCeiling_ReportsNothing() {
        assertThat(EgoRules.check(egos("{\"ZAYIN\":{\"id\":\"20501\",\"threadspin\":4}}"), DATA)).isEmpty();
    }

    @Test
    void check_WhenEgosAreAbsent_ReportsTheZayinMissing() {
        assertThat(EgoRules.check(equipmentOfSinnerFive("{}"), DATA)).containsExactly(new Violation(
                ErrorCode.MISSING_EGO_ZAYIN, EGOS, "Field 'equipment[05].egos' is missing"));
    }

    @Test
    void check_WhenEgosAreANumber_ReportsTheirType() {
        assertThat(EgoRules.check(egos("5"), DATA)).containsExactly(new Violation(
                ErrorCode.INVALID_FIELD_TYPE, EGOS, "Field 'equipment[05].egos' has wrong type, expected object"));
    }

    @Test
    void check_WhenTheZayinIsAbsent_ReportsItMissing() {
        assertThat(EgoRules.check(egos("{\"TETH\":{\"id\":\"20501\",\"threadspin\":4}}"), DATA))
                .containsExactly(new Violation(ErrorCode.MISSING_EGO_ZAYIN, ZAYIN,
                        "Missing required fields: [equipment[05].egos.ZAYIN]"));
    }

    @Test
    void check_WhenTheZayinIsANumber_ReportsItsType() {
        assertThat(EgoRules.check(egos("{\"ZAYIN\":5}"), DATA)).containsExactly(new Violation(
                ErrorCode.INVALID_FIELD_TYPE, ZAYIN, "Field 'equipment[05].egos.ZAYIN' must be object, got number 5"));
    }

    @Test
    void check_WhenTheZayinIdIsBlank_ReportsItMissingAndNotUnknown() {
        assertThat(EgoRules.check(egos("{\"ZAYIN\":{\"id\":\"\",\"threadspin\":4}}"), DATA))
                .containsExactly(new Violation(ErrorCode.MISSING_EGO_ZAYIN, ZAYIN.field("id"),
                        "Field 'equipment[05].egos.ZAYIN.id' is missing"));
    }

    @Test
    void check_WhenTheZayinIdIsNull_ReportsItMissing() {
        assertThat(EgoRules.check(egos("{\"ZAYIN\":{\"id\":null}}"), DATA))
                .extracting(Violation::code)
                .containsExactly(ErrorCode.MISSING_EGO_ZAYIN);
    }

    @Test
    void check_WhenTheZayinIdIsANumber_ReportsItsTypeOnce() {
        assertThat(EgoRules.check(egos("{\"ZAYIN\":{\"id\":20501,\"threadspin\":4}}"), DATA))
                .containsExactly(new Violation(ErrorCode.INVALID_FIELD_TYPE, ZAYIN.field("id"),
                        "Field 'equipment[05].egos.ZAYIN.id' must be string, got number 20501"));
    }

    @Test
    void check_WhenAnOptionalEgoHasNoId_ReportsNothingForIt() {
        assertThat(EgoRules.check(egos("{\"ZAYIN\":{\"id\":\"20501\"},\"TETH\":{\"id\":null}}"), DATA)).isEmpty();
    }

    @Test
    void check_WhenAnEgoTypeIsUnknown_ReportsTheType() {
        assertThat(EgoRules.check(egos("{\"ZAYIN\":{\"id\":\"20501\"},\"KETHER\":{}}"), DATA))
                .containsExactly(new Violation(ErrorCode.INVALID_EGO_TYPE, EGOS.field("KETHER"),
                        "Field 'equipment[05].egos.KETHER' has wrong type, expected valid EGO type (ZAYIN/TETH/HE/WAW/ALEPH)"));
    }

    @Test
    void check_WhenTheEgoIsUnknown_ReportsIt() {
        assertThat(EgoRules.check(egos("{\"ZAYIN\":{\"id\":\"20599\",\"threadspin\":9}}"), DATA))
                .containsExactly(new Violation(ErrorCode.UNKNOWN_EGO, ZAYIN.field("id"),
                        "EGO ID '20599' not found or invalid"));
    }

    @Test
    void check_WhenTheEgoBelongsToAnotherSinner_ReportsTheMismatch() {
        assertThat(EgoRules.check(egos("{\"ZAYIN\":{\"id\":\"20601\"}}"), DATA))
                .containsExactly(new Violation(ErrorCode.MISMATCHED_EGO, ZAYIN.field("id"),
                        "EGO '20601' does not belong to sinner 05"));
    }

    @Test
    void check_WhenThreadspinIsOutsideTheGameRange_ReportsTheGameRange() {
        assertThat(EgoRules.check(egos("{\"ZAYIN\":{\"id\":\"20503\",\"threadspin\":9}}"), DATA))
                .containsExactly(new Violation(ErrorCode.OUT_OF_RANGE_EGO_THREADSPIN, ZAYIN.field("threadspin"),
                        "threadspin value 9 is out of range [1-5]"));
    }

    @Test
    void check_WhenThreadspinIsAboveTheEgosCeiling_ReportsTheCeiling() {
        assertThat(EgoRules.check(egos("{\"ZAYIN\":{\"id\":\"20501\",\"threadspin\":5}}"), DATA))
                .containsExactly(new Violation(ErrorCode.OUT_OF_RANGE_EGO_THREADSPIN, ZAYIN.field("threadspin"),
                        "threadspin for EGO 20501 value 5 is out of range [1-4]"));
    }

    @Test
    void check_WhenTheEgoHasNoCeiling_ReportsTheMissingCeiling() {
        assertThat(EgoRules.check(egos("{\"ZAYIN\":{\"id\":\"20501\"},\"TETH\":{\"id\":\"20502\",\"threadspin\":3}}"),
                DATA)).containsExactly(new Violation(ErrorCode.MISSING_EGO_THREADSPIN_CEILING,
                EGOS.field("TETH").field("threadspin"), "EGO '20502' has no threadspin ceiling in game data"));
    }

    @Test
    void check_WhenThreadspinIsAString_ReportsItsType() {
        assertThat(EgoRules.check(egos("{\"ZAYIN\":{\"id\":\"20501\",\"threadspin\":\"4\"}}"), DATA))
                .containsExactly(new Violation(ErrorCode.INVALID_FIELD_TYPE, ZAYIN.field("threadspin"),
                        "Field 'equipment[05].egos.ZAYIN.threadspin' must be number, got string \"4\""));
    }

    @ParameterizedTest
    @ValueSource(strings = {"7", "\"x\"", "[]", "null", "\"\"", "\"  \""})
    void check_WhenAnOptionalSlotIsNotAnObject_ReportsItsType(String slot) {
        assertThat(EgoRules.check(egos("{\"ZAYIN\":{\"id\":\"20501\"},\"TETH\":" + slot + "}"), DATA))
                .extracting(Violation::code, violation -> violation.path().render())
                .containsExactly(tuple(ErrorCode.INVALID_FIELD_TYPE, "equipment[05].egos.TETH"));
    }

    @Test
    void check_WhenAnUnknownTypeSlotIsNull_ReportsOnlyTheType() {
        assertThat(EgoRules.check(egos("{\"ZAYIN\":{\"id\":\"20501\"},\"KETHER\":null}"), DATA))
                .extracting(Violation::code)
                .containsExactly(ErrorCode.INVALID_EGO_TYPE);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "  "})
    void check_WhenAnOptionalEgoIdIsBlank_LeavesTheSlotEmptyAndSkipsTheThreadspin(String id) {
        assertThat(EgoRules.check(egos("{\"ZAYIN\":{\"id\":\"20501\"},\"TETH\":{\"id\":\"" + id
                + "\",\"threadspin\":99}}"), DATA)).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"\"\"", "\"  \""})
    void check_WhenEgosAreABlankString_ReportsTheirType(String egos) {
        assertThat(EgoRules.check(egos(egos), DATA)).containsExactly(new Violation(
                ErrorCode.INVALID_FIELD_TYPE, EGOS, "Field 'equipment[05].egos' has wrong type, expected object"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"\"\"", "\"  \""})
    void check_WhenTheZayinIsABlankString_ReportsItsType(String zayin) {
        assertThat(EgoRules.check(egos("{\"ZAYIN\":" + zayin + "}"), DATA))
                .extracting(Violation::code, violation -> violation.path().render())
                .containsExactly(tuple(ErrorCode.INVALID_FIELD_TYPE, "equipment[05].egos.ZAYIN"));
    }

    private static List<SinnerEntry> egos(String egos) {
        return equipmentOfSinnerFive("{\"egos\":" + egos + "}");
    }
}
