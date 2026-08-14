package org.danteplanner.backend.planner.validation;

import org.danteplanner.backend.planner.exception.PlannerConflictException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The optimistic-locking rule a client write is held to. Only {@code force} skips the check;
 * everything else has to name the version it wrote against.
 */
class SyncVersionValidatorTest {

    private final SyncVersionValidator validator = new SyncVersionValidator();

    @Test
    void requireSyncVersionMatch_WhenVersionsAgree_Passes() {
        assertThatCode(() -> validator.requireSyncVersionMatch(false, 4L, 4L))
                .doesNotThrowAnyException();
    }

    @Test
    void requireSyncVersionMatch_WhenStoredVersionMoved_Throws() {
        assertThatThrownBy(() -> validator.requireSyncVersionMatch(false, 4L, 5L))
                .isInstanceOf(PlannerConflictException.class);
    }

    @Test
    void requireSyncVersionMatch_WhenNoVersionIsNamed_Throws() {
        assertThatThrownBy(() -> validator.requireSyncVersionMatch(false, null, 5L))
                .isInstanceOf(PlannerConflictException.class);
    }

    @Test
    void requireSyncVersionMatch_WhenForcedWithoutAVersion_Passes() {
        assertThatCode(() -> validator.requireSyncVersionMatch(true, null, 5L))
                .doesNotThrowAnyException();
    }

    @Test
    void requireSyncVersionMatch_WhenForcedWithAStaleVersion_Passes() {
        assertThatCode(() -> validator.requireSyncVersionMatch(true, 4L, 5L))
                .doesNotThrowAnyException();
    }
}
