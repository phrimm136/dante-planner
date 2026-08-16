package org.danteplanner.backend.planner.validation;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.danteplanner.backend.planner.entity.PlannerContent;
import org.danteplanner.backend.planner.entity.PlannerStatus;
import org.danteplanner.backend.planner.exception.PlannerConflictException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The optimistic-locking rule a client write is held to. {@code force} skips the check and a
 * matching version writes without comparing anything; only a stale version reaches the arbitration
 * step, where a write that would move no field is acknowledged instead of refused. A request naming
 * no version stays a conflict whatever it carries.
 */
class SyncVersionValidatorTest {

    private static final String DOCUMENT = """
            {"equipment":{},"selectedGiftIds":["9001"],"observationGiftIds":[],\
            "comprehensiveGiftIds":[],"floorSelections":[]}""";

    private static final String OTHER_DOCUMENT = """
            {"equipment":{},"selectedGiftIds":["9002"],"observationGiftIds":[],\
            "comprehensiveGiftIds":[],"floorSelections":[]}""";

    private final SyncVersionValidator validator =
            new SyncVersionValidator(new EffectiveNoOpPredicate(new ObjectMapper()));

    /** A validator whose arbitration step fails the test if anything reaches it. */
    private static SyncVersionValidator refusingToCompare() {
        return new SyncVersionValidator(new EffectiveNoOpPredicate(new ObjectMapper()) {
            @Override
            public boolean isEffectiveNoOp(PlannerContent stored, CarriedWrite carried) {
                throw new AssertionError("a comparison ran on a path that does not arbitrate");
            }
        });
    }

    private static PlannerContent storedRow() {
        return PlannerContent.builder()
                .title("Stored Title")
                .status(PlannerStatus.SAVED)
                .category("5F")
                .content(DOCUMENT)
                .contentSchemaVersion(2)
                .gameContentVersion(6)
                .build();
    }

    private static CarriedWrite identicalWrite() {
        return CarriedWrite.builder()
                .title("Stored Title")
                .status(PlannerStatus.SAVED)
                .category("5F")
                .content(DOCUMENT)
                .contentSchemaVersion(2)
                .gameContentVersion(6)
                .build();
    }

    private static CarriedWrite divergentWrite() {
        return CarriedWrite.builder()
                .title("Stored Title")
                .status(PlannerStatus.SAVED)
                .category("5F")
                .content(OTHER_DOCUMENT)
                .contentSchemaVersion(2)
                .gameContentVersion(6)
                .build();
    }

    @Test
    void arbitrate_WhenVersionsAgree_Writes() {
        assertThat(refusingToCompare().arbitrate(false, 4L, 4L, storedRow(), divergentWrite()))
                .isEqualTo(WriteArbitration.WRITE);
    }

    @Test
    @DisplayName("force-still-bypasses: a forced write is applied without arbitration")
    void arbitrate_WhenForcedWithAStaleVersion_Writes() {
        assertThat(refusingToCompare().arbitrate(true, 4L, 5L, storedRow(), identicalWrite()))
                .isEqualTo(WriteArbitration.WRITE);
    }

    @Test
    void arbitrate_WhenForcedWithoutAVersion_Writes() {
        assertThat(refusingToCompare().arbitrate(true, null, 5L, storedRow(), divergentWrite()))
                .isEqualTo(WriteArbitration.WRITE);
    }

    @Test
    @DisplayName("stale-identical-save-acks-current-version: a stale write moving no field is acknowledged")
    void arbitrate_WhenStaleAndNothingWouldChange_AcknowledgesWithoutWriting() {
        assertThat(validator.arbitrate(false, 4L, 5L, storedRow(), identicalWrite()))
                .isEqualTo(WriteArbitration.ACK_NO_OP);
    }

    @Test
    @DisplayName("stale-different-content-conflicts: a stale write that would move a field still conflicts")
    void arbitrate_WhenStaleAndAFieldWouldChange_Throws() {
        assertThatThrownBy(() -> validator.arbitrate(false, 4L, 5L, storedRow(), divergentWrite()))
                .isInstanceOf(PlannerConflictException.class)
                .extracting(conflict -> ((PlannerConflictException) conflict).getActualVersion())
                .isEqualTo(5L);
    }

    @Test
    @DisplayName("no-version-still-conflicts: naming no version is a conflict even when nothing would change")
    void arbitrate_WhenNoVersionIsNamed_ThrowsWithoutArbitrating() {
        assertThatThrownBy(() -> refusingToCompare().arbitrate(false, null, 5L, storedRow(), identicalWrite()))
                .isInstanceOf(PlannerConflictException.class);
    }

    @Test
    @DisplayName("unparseable-content-falls-through-to-conflict: arbitration never turns an error into a write")
    void arbitrate_WhenStaleAndTheStoredDocumentIsUnparseable_Throws() {
        PlannerContent unparseable = PlannerContent.builder()
                .title("Stored Title")
                .status(PlannerStatus.SAVED)
                .category("5F")
                .content("{\"equipment\":")
                .contentSchemaVersion(2)
                .gameContentVersion(6)
                .build();

        assertThatThrownBy(() -> validator.arbitrate(false, 4L, 5L, unparseable, identicalWrite()))
                .isInstanceOf(PlannerConflictException.class);
    }
}
