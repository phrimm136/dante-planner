package org.danteplanner.backend.planner.validation.sinner;

import com.fasterxml.jackson.databind.node.MissingNode;
import org.danteplanner.backend.planner.validation.ContentPath;
import org.danteplanner.backend.planner.validation.ErrorCode;
import org.danteplanner.backend.planner.validation.SinnerEntry;
import org.danteplanner.backend.planner.validation.Violation;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.danteplanner.backend.planner.validation.ContentFixtures.json;

class SinnerKeysTest {

    private static final ContentPath EQUIPMENT = ContentPath.of("equipment");

    @Test
    void parse_WhenTheContainerIsAbsent_AdmitsNothingAndReportsNothing() {
        SinnerKeys.Sinners sinners = SinnerKeys.parse(MissingNode.getInstance(), EQUIPMENT);

        assertThat(sinners.entries()).isEmpty();
        assertThat(sinners.violations()).isEmpty();
    }

    @Test
    void parse_WhenTheContainerIsNull_ReportsItsKind() {
        SinnerKeys.Sinners sinners = SinnerKeys.parse(json("null"), EQUIPMENT);

        assertThat(sinners.violations()).containsExactly(new Violation(ErrorCode.INVALID_FIELD_TYPE, EQUIPMENT,
                "Field 'equipment' must be object, got null"));
    }

    @Test
    void parse_WhenTheContainerIsAnArray_ReportsItsKind() {
        SinnerKeys.Sinners sinners = SinnerKeys.parse(json("[]"), EQUIPMENT);

        assertThat(sinners.entries()).isEmpty();
        assertThat(sinners.violations()).containsExactly(new Violation(ErrorCode.INVALID_FIELD_TYPE, EQUIPMENT,
                "Field 'equipment' must be object, got array"));
    }

    @Test
    void parse_WhenEverySinnerIsPresent_AdmitsTwelveEntriesInSinnerOrder() {
        SinnerKeys.Sinners sinners = SinnerKeys.parse(json(sinnersWith(Map.of())), EQUIPMENT);

        assertThat(sinners.violations()).isEmpty();
        assertThat(sinners.entries()).extracting(SinnerEntry::sinnerKey)
                .containsExactly("01", "02", "03", "04", "05", "06", "07", "08", "09", "10", "11", "12");
        assertThat(sinners.entries().get(4).path()).isEqualTo(EQUIPMENT.key("05"));
    }

    @Test
    void parse_WhenAKeyIsUnpadded_AdmitsItUnderItsPaddedKey() {
        SinnerKeys.Sinners sinners = SinnerKeys.parse(json(sinnersWith(Map.of("05", "\"5\":{}"))), EQUIPMENT);

        assertThat(sinners.violations()).isEmpty();
        assertThat(sinners.entries()).extracting(SinnerEntry::sinnerKey).contains("05");
    }

    @Test
    void parse_WhenAKeyIsNotANumber_ReportsTheKeyAndTheSinnerItLeavesMissing() {
        SinnerKeys.Sinners sinners = SinnerKeys.parse(json(sinnersWith(Map.of("05", "\"yi_sang\":{}"))), EQUIPMENT);

        assertThat(sinners.violations()).containsExactly(
                new Violation(ErrorCode.MISSING_SINNER, EQUIPMENT, "Missing required fields: [05]"),
                new Violation(ErrorCode.INVALID_FIELD_TYPE, EQUIPMENT.key("yi_sang"),
                        "Field 'equipment key 'yi_sang'' has wrong type, expected integer"));
    }

    @Test
    void parse_WhenAKeyOverflowsAnInt_ReportsItAsNotANumber() {
        SinnerKeys.Sinners sinners = SinnerKeys.parse(json(sinnersWith(Map.of("05", "\"99999999999\":{}"))),
                EQUIPMENT);

        assertThat(sinners.violations()).contains(new Violation(ErrorCode.INVALID_FIELD_TYPE,
                EQUIPMENT.key("99999999999"), "Field 'equipment key '99999999999'' has wrong type, expected integer"));
    }

    @Test
    void parse_WhenAKeyIsOutOfRange_ReportsItsValue() {
        SinnerKeys.Sinners sinners = SinnerKeys.parse(json(sinnersWith(Map.of("05", "\"13\":{}"))), EQUIPMENT);

        assertThat(sinners.violations()).containsExactly(
                new Violation(ErrorCode.MISSING_SINNER, EQUIPMENT, "Missing required fields: [05]"),
                new Violation(ErrorCode.OUT_OF_RANGE_SINNER, EQUIPMENT.key("13"),
                        "equipment key value 13 is out of range [1-12]"));
    }

    @Test
    void parse_WhenAKeyIsZero_ReportsItOutOfRange() {
        SinnerKeys.Sinners sinners = SinnerKeys.parse(json(sinnersWith(Map.of("05", "\"0\":{}"))), EQUIPMENT);

        assertThat(sinners.violations()).contains(new Violation(ErrorCode.OUT_OF_RANGE_SINNER, EQUIPMENT.key("0"),
                "equipment key value 0 is out of range [1-12]"));
    }

    @Test
    void parse_WhenASinnerIsNull_ReportsItAndAdmitsTheOthers() {
        SinnerKeys.Sinners sinners = SinnerKeys.parse(json(sinnersWith(Map.of("05", "\"05\":null"))), EQUIPMENT);

        assertThat(sinners.violations()).containsExactly(new Violation(ErrorCode.INVALID_FIELD_TYPE,
                EQUIPMENT.key("05"), "Field 'equipment[05]' has wrong type, expected object"));
        assertThat(sinners.entries()).extracting(SinnerEntry::sinnerKey).hasSize(11).doesNotContain("05");
    }

    @Test
    void parse_WhenTwoSinnersAreMissing_ReportsBothOnceAndAdmitsThePresentOnes() {
        SinnerKeys.Sinners sinners = SinnerKeys.parse(json(sinnersWith(Map.of("01", "", "12", ""))), EQUIPMENT);

        assertThat(sinners.violations()).containsExactly(
                new Violation(ErrorCode.MISSING_SINNER, EQUIPMENT, "Missing required fields: [01, 12]"));
        assertThat(sinners.entries()).hasSize(10);
    }

    private static String sinnersWith(Map<String, String> replacements) {
        return IntStream.rangeClosed(1, 12)
                .mapToObj(sinner -> String.format("%02d", sinner))
                .map(key -> replacements.getOrDefault(key, "\"" + key + "\":{}"))
                .filter(entry -> !entry.isEmpty())
                .collect(Collectors.joining(",", "{", "}"));
    }
}
