package org.danteplanner.backend.planner.validation.identity;

import org.danteplanner.backend.planner.validation.ContentPath;
import org.danteplanner.backend.planner.validation.ErrorCode;
import org.danteplanner.backend.planner.validation.Violation;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class IdentityRulesTest {

    private record KnownIdentities(Set<String> ids) implements IdentityReferenceData {

        @Override
        public boolean hasIdentity(String identityId) {
            return ids.contains(identityId);
        }

        @Override
        public boolean belongsToSinner(String sinnerKey, String entityId) {
            return Integer.parseInt(entityId.substring(1, 3)) == Integer.parseInt(sinnerKey);
        }
    }

    private static final IdentityReferenceData DATA = new KnownIdentities(Set.of("10501", "10601"));
    private static final ContentPath IDENTITY = ContentPath.of("equipment").key("5").field("identity");

    @Test
    void check_WhenEquipmentHasNoSinners_ReportsNothing() {
        assertThat(IdentityRules.check(Map.of(), DATA)).isEmpty();
    }

    @Test
    void check_WhenTheIdentityIsKnownAndInRange_ReportsNothing() {
        assertThat(IdentityRules.check(sinnerFive(new Identity("10501", 45, 4)), DATA)).isEmpty();
    }

    @Test
    void check_WhenTheIdIsUnknown_ReportsItAndSkipsTheBounds() {
        assertThat(IdentityRules.check(sinnerFive(new Identity("10599", 99, 4)), DATA)).containsExactly(new Violation(
                ErrorCode.UNKNOWN_IDENTITY, IDENTITY.field("id"), "identity ID '10599' not found or invalid"));
    }

    @Test
    void check_WhenTheIdBelongsToAnotherSinner_ReportsTheMismatch() {
        assertThat(IdentityRules.check(sinnerFive(new Identity("10601", 45, 4)), DATA))
                .containsExactly(new Violation(ErrorCode.MISMATCHED_IDENTITY, IDENTITY.field("id"),
                        "identity '10601' does not belong to sinner 5"));
    }

    @Test
    void check_WhenLevelAndUptieAreBothOutOfRange_ReportsEachInPathOrder() {
        assertThat(IdentityRules.check(sinnerFive(new Identity("10501", 99, 5)), DATA)).containsExactly(
                new Violation(ErrorCode.OUT_OF_RANGE_IDENTITY_LEVEL, IDENTITY.field("level"),
                        "level value 99 is out of range [1-60]"),
                new Violation(ErrorCode.OUT_OF_RANGE_IDENTITY_UPTIE, IDENTITY.field("uptie"),
                        "uptie value 5 is out of range [1-4]"));
    }

    @Test
    void check_WhenUptieIsBelowTheRange_ReportsIt() {
        assertThat(IdentityRules.check(sinnerFive(new Identity("10501", 45, 0)), DATA)).containsExactly(
                new Violation(ErrorCode.OUT_OF_RANGE_IDENTITY_UPTIE, IDENTITY.field("uptie"),
                        "uptie value 0 is out of range [1-4]"));
    }

    @Test
    void check_WhenLevelExceedsAnInt_ReportsItOutOfRange() {
        assertThat(IdentityRules.check(sinnerFive(new Identity("10501", 3_000_000_000L, 4)), DATA)).containsExactly(
                new Violation(ErrorCode.OUT_OF_RANGE_IDENTITY_LEVEL, IDENTITY.field("level"),
                        "level value 3000000000 is out of range [1-60]"));
    }

    private static Map<String, Identity> sinnerFive(Identity identity) {
        return Map.of("5", identity);
    }
}
