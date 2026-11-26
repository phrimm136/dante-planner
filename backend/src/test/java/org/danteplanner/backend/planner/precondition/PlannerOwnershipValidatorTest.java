package org.danteplanner.backend.planner.precondition;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.Test;

import org.danteplanner.backend.planner.repository.StoredPlanner;
import org.danteplanner.backend.planner.repository.PlannerOwnershipRow;
import org.danteplanner.backend.support.LogCapture;
import org.danteplanner.backend.support.OwnershipRows;
import org.danteplanner.backend.support.TestDataFactory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.danteplanner.backend.support.Rejections.assertRejected;

class PlannerOwnershipValidatorTest {

    private static final Long CALLER = 1L;

    private static final Long OTHER = 2L;

    private static final UUID ID = UUID.fromString("0b5d1f2e-0000-4000-8000-00000000abcd");

    private static final Optional<PlannerOwnershipRow> VACANT = Optional.empty();

    private static final Optional<PlannerOwnershipRow> MINE = row(CALLER, null);

    private static final Optional<PlannerOwnershipRow> MINE_DELETED = row(CALLER, Instant.EPOCH);

    private static final Optional<PlannerOwnershipRow> FOREIGN = row(OTHER, null);

    private static final Optional<PlannerOwnershipRow> FOREIGN_DELETED = row(OTHER, Instant.EPOCH);

    private static final String NOT_FOUND = "PLANNER_NOT_FOUND";

    private static final String FORBIDDEN = "PLANNER_FORBIDDEN";

    private static final String SOFT_DELETED_WARNING =
            "Planner " + ID + " is soft-deleted for user 1 - cannot recreate";

    private static final String COLLISION_WARNING =
            "Planner " + ID + " exists but belongs to another user (ID collision)";

    private final PlannerOwnershipValidator validator = new PlannerOwnershipValidator();

    private static Optional<PlannerOwnershipRow> row(Long userId, Instant deletedAt) {
        return Optional.of(OwnershipRows.of(userId, deletedAt));
    }

    private static void assertRefusedSilently(String code, ThrowingCallable check) {
        try (LogCapture capture = LogCapture.onThisThread(PlannerOwnershipValidator.class)) {
            assertRejected(check).withCode(code);
            assertThat(capture.events()).isEmpty();
        }
    }

    private static void assertRefusedWarning(String code, String warning, ThrowingCallable check) {
        try (LogCapture capture = LogCapture.onThisThread(PlannerOwnershipValidator.class)) {
            assertRejected(check).withCode(code);
            assertThat(capture.at(Level.WARN)).extracting(ILoggingEvent::getFormattedMessage)
                    .containsExactly(warning);
        }
    }

    @Test
    void requireOwner_WhenTheCallerOwnsThePlanner_CarriesIt() {
        StoredPlanner planner = TestDataFactory.planner(TestDataFactory.unsavedUser(CALLER)).build();

        assertThat(validator.requireOwner(planner, CALLER).planner()).isSameAs(planner);
        assertRefusedSilently(FORBIDDEN, () -> validator.requireOwner(planner, OTHER));
    }

    @Test
    void requireLiveOwner_WhenTheRowIsNotTheCallersLiveOne_TellsMissingFromForeign() {
        assertThat(validator.requireLiveOwner(ID, CALLER, MINE).plannerId()).isEqualTo(ID);
        assertRefusedSilently(NOT_FOUND, () -> validator.requireLiveOwner(ID, CALLER, VACANT));
        assertRefusedSilently(NOT_FOUND, () -> validator.requireLiveOwner(ID, CALLER, MINE_DELETED));
        assertRefusedSilently(NOT_FOUND, () -> validator.requireLiveOwner(ID, CALLER, FOREIGN_DELETED));
        assertRefusedSilently(FORBIDDEN, () -> validator.requireLiveOwner(ID, CALLER, FOREIGN));
    }

    @Test
    void requireLiveOwnedBy_WhenTheRowIsNotTheCallersLiveOne_AnswersNotFound() {
        assertThat(validator.requireLiveOwnedBy(ID, CALLER, MINE).plannerId()).isEqualTo(ID);
        assertRefusedSilently(NOT_FOUND, () -> validator.requireLiveOwnedBy(ID, CALLER, VACANT));
        assertRefusedSilently(NOT_FOUND, () -> validator.requireLiveOwnedBy(ID, CALLER, MINE_DELETED));
        assertRefusedSilently(NOT_FOUND, () -> validator.requireLiveOwnedBy(ID, CALLER, FOREIGN));
        assertRefusedSilently(NOT_FOUND, () -> validator.requireLiveOwnedBy(ID, CALLER, FOREIGN_DELETED));
    }

    @Test
    void requireIdAvailable_WhenARowExists_RefusesTheCallersOwnAndForeignLiveOnes() {
        assertThat(validator.requireIdAvailable(ID, CALLER, VACANT).plannerId()).isEqualTo(ID);
        assertThat(validator.requireIdAvailable(ID, CALLER, FOREIGN_DELETED).plannerId()).isEqualTo(ID);
        assertRefusedWarning(NOT_FOUND, SOFT_DELETED_WARNING,
                () -> validator.requireIdAvailable(ID, CALLER, MINE_DELETED));
        assertRefusedWarning(NOT_FOUND, SOFT_DELETED_WARNING, () -> validator.requireIdAvailable(ID, CALLER, MINE));
        assertRefusedWarning(FORBIDDEN, COLLISION_WARNING, () -> validator.requireIdAvailable(ID, CALLER, FOREIGN));
    }

    @Test
    void requireUpsertableId_WhenTheRowIsTheCallersLiveOneOrTheIdIsAvailable_Passes() {
        assertThat(validator.requireUpsertableId(ID, CALLER, MINE).plannerId()).isEqualTo(ID);
        assertThat(validator.requireUpsertableId(ID, CALLER, VACANT).plannerId()).isEqualTo(ID);
        assertThat(validator.requireUpsertableId(ID, CALLER, FOREIGN_DELETED).plannerId()).isEqualTo(ID);
        assertRefusedWarning(NOT_FOUND, SOFT_DELETED_WARNING,
                () -> validator.requireUpsertableId(ID, CALLER, MINE_DELETED));
        assertRefusedWarning(FORBIDDEN, COLLISION_WARNING, () -> validator.requireUpsertableId(ID, CALLER, FOREIGN));
    }
}
