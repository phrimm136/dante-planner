package org.danteplanner.backend.planner.validation.identity;

import org.danteplanner.backend.planner.validation.ContentPath;
import org.danteplanner.backend.planner.validation.ErrorCode;
import org.danteplanner.backend.planner.validation.Violation;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.danteplanner.backend.planner.validation.ContentFixtures.equipmentOfSinnerFive;

class IdentityRulesTest {

    private record KnownIdentities(Set<String> ids) implements IdentityReferenceData {

        @Override
        public boolean hasIdentity(String identityId) {
            return ids.contains(identityId);
        }

        @Override
        public boolean belongsToSinner(String sinnerKey, String entityId) {
            return entityId.substring(1, 3).equals(sinnerKey);
        }
    }

    private static final IdentityReferenceData DATA = new KnownIdentities(Set.of("10501", "10601"));
    private static final ContentPath IDENTITY = ContentPath.of("equipment").key("05").field("identity");

    @Test
    void check_WhenEquipmentHasNoSinners_ReportsNothing() {
        assertThat(IdentityRules.check(List.of(), DATA)).isEmpty();
    }

    @Test
    void check_WhenTheIdentityIsKnownAndInRange_ReportsNothing() {
        assertThat(IdentityRules.check(equipmentOfSinnerFive(
                "{\"identity\":{\"id\":\"10501\",\"level\":45,\"uptie\":4}}"), DATA)).isEmpty();
    }

    @Test
    void check_WhenTheIdentityIsAbsent_ReportsItMissing() {
        assertThat(IdentityRules.check(equipmentOfSinnerFive("{\"egos\":{}}"), DATA)).containsExactly(new Violation(
                ErrorCode.MISSING_IDENTITY, IDENTITY, "Field 'equipment[05].identity' is missing"));
    }

    @Test
    void check_WhenTheIdentityIsNull_ReportsItMissing() {
        assertThat(IdentityRules.check(equipmentOfSinnerFive("{\"identity\":null}"), DATA))
                .extracting(Violation::code)
                .containsExactly(ErrorCode.MISSING_IDENTITY);
    }

    @Test
    void check_WhenTheIdentityIsANumber_ReportsItsType() {
        assertThat(IdentityRules.check(equipmentOfSinnerFive("{\"identity\":5}"), DATA)).containsExactly(new Violation(
                ErrorCode.INVALID_FIELD_TYPE, IDENTITY, "Field 'equipment[05].identity' has wrong type, expected object"));
    }

    @Test
    void check_WhenTheIdIsBlank_ReportsItMissingAndNotUnknown() {
        assertThat(IdentityRules.check(equipmentOfSinnerFive("{\"identity\":{\"id\":\"  \",\"level\":45}}"), DATA))
                .containsExactly(new Violation(ErrorCode.MISSING_IDENTITY, IDENTITY.field("id"),
                        "Field 'equipment[05].identity.id' is missing"));
    }

    @Test
    void check_WhenTheIdIsANumber_ReportsItsTypeOnce() {
        assertThat(IdentityRules.check(equipmentOfSinnerFive("{\"identity\":{\"id\":10501}}"), DATA))
                .containsExactly(new Violation(ErrorCode.INVALID_FIELD_TYPE, IDENTITY.field("id"),
                        "Field 'equipment[05].identity.id' has wrong type, expected non-empty string"));
    }

    @Test
    void check_WhenTheIdIsUnknown_ReportsItAndSkipsTheBounds() {
        assertThat(IdentityRules.check(equipmentOfSinnerFive(
                "{\"identity\":{\"id\":\"10599\",\"level\":99}}"), DATA)).containsExactly(new Violation(
                ErrorCode.UNKNOWN_IDENTITY, IDENTITY.field("id"), "identity ID '10599' not found or invalid"));
    }

    @Test
    void check_WhenTheIdBelongsToAnotherSinner_ReportsTheMismatch() {
        assertThat(IdentityRules.check(equipmentOfSinnerFive("{\"identity\":{\"id\":\"10601\"}}"), DATA))
                .containsExactly(new Violation(ErrorCode.MISMATCHED_IDENTITY, IDENTITY.field("id"),
                        "identity '10601' does not belong to sinner 05"));
    }

    @Test
    void check_WhenLevelAndUptieAreBothWrong_ReportsEachInPathOrder() {
        assertThat(IdentityRules.check(equipmentOfSinnerFive(
                "{\"identity\":{\"id\":\"10501\",\"uptie\":\"4\",\"level\":99}}"), DATA)).containsExactly(
                new Violation(ErrorCode.OUT_OF_RANGE_IDENTITY_LEVEL, IDENTITY.field("level"),
                        "level value 99 is out of range [1-60]"),
                new Violation(ErrorCode.INVALID_FIELD_TYPE, IDENTITY.field("uptie"),
                        "Field 'equipment[05].identity.uptie' must be number, got string \"4\""));
    }

    @Test
    void check_WhenLevelIsNull_ReportsItsType() {
        assertThat(IdentityRules.check(equipmentOfSinnerFive("{\"identity\":{\"id\":\"10501\",\"level\":null}}"),
                DATA)).containsExactly(new Violation(ErrorCode.INVALID_FIELD_TYPE, IDENTITY.field("level"),
                "Field 'equipment[05].identity.level' must be number, got null"));
    }

    @Test
    void check_WhenUptieIsBelowTheRange_ReportsIt() {
        assertThat(IdentityRules.check(equipmentOfSinnerFive("{\"identity\":{\"id\":\"10501\",\"uptie\":0}}"),
                DATA)).containsExactly(new Violation(ErrorCode.OUT_OF_RANGE_IDENTITY_UPTIE, IDENTITY.field("uptie"),
                "uptie value 0 is out of range [1-4]"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"\"\"", "\"  \""})
    void check_WhenTheIdentityIsABlankString_ReportsItsType(String identity) {
        assertThat(IdentityRules.check(equipmentOfSinnerFive("{\"identity\":" + identity + "}"), DATA))
                .containsExactly(new Violation(ErrorCode.INVALID_FIELD_TYPE, IDENTITY,
                        "Field 'equipment[05].identity' has wrong type, expected object"));
    }

    @Test
    void check_WhenLevelAndUptieAreIntegralValuedFractions_AcceptsThem() {
        assertThat(IdentityRules.check(equipmentOfSinnerFive(
                "{\"identity\":{\"id\":\"10501\",\"level\":60.0,\"uptie\":4.0}}"), DATA)).isEmpty();
    }
}
