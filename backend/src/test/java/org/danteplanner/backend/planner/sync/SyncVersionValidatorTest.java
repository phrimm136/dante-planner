package org.danteplanner.backend.planner.sync;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.danteplanner.backend.planner.entity.PlannerContent;
import org.danteplanner.backend.planner.entity.PlannerStatus;
import org.danteplanner.backend.planner.entity.StoredPlannerContent;
import org.danteplanner.backend.planner.exception.PlannerConflictException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.danteplanner.backend.support.Rejections.assertRejected;

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

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final SyncVersionValidator validator = new SyncVersionValidator();

    private static PlannerContent storedRow() {
        return StoredPlannerContent.holding(PlannerContent.builder()
                .title("Stored Title")
                .status(PlannerStatus.SAVED)
                .category("5F")
                .contentSchemaVersion(2)
                .gameContentVersion(6)
                .build(), DOCUMENT);
    }

    private static PlannerChange identicalChange() {
        return PlannerChange.between(storedRow(), identicalWrite(), MAPPER);
    }

    private static PlannerChange divergentChange() {
        return PlannerChange.between(storedRow(), divergentWrite(), MAPPER);
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
        assertThat(validator.arbitrate(false, 4L, 4L, divergentChange()))
                .isEqualTo(WriteArbitration.WRITE);
    }

    // force-still-bypasses
    @Test
    void arbitrate_WhenForcedWithAStaleVersion_Writes() {
        assertThat(validator.arbitrate(true, 4L, 5L, divergentChange()))
                .isEqualTo(WriteArbitration.WRITE);
    }

    @Test
    void arbitrate_WhenForcedWithoutAVersion_Writes() {
        assertThat(validator.arbitrate(true, null, 5L, divergentChange()))
                .isEqualTo(WriteArbitration.WRITE);
    }

    // stale-identical-save-acks-current-version
    @Test
    void arbitrate_WhenStaleAndNothingWouldChange_AcknowledgesWithoutWriting() {
        assertThat(validator.arbitrate(false, 4L, 5L, identicalChange()))
                .isEqualTo(WriteArbitration.ACK_NO_OP);
    }

    // stale-different-content-conflicts
    @Test
    void arbitrate_WhenStaleAndAFieldWouldChange_Throws() {
        assertThatThrownBy(() -> validator.arbitrate(false, 4L, 5L, divergentChange()))
                .isInstanceOf(PlannerConflictException.class)
                .extracting(conflict -> ((PlannerConflictException) conflict).getActualVersion())
                .isEqualTo(5L);
    }

    // no-version-still-conflicts
    @Test
    void arbitrate_WhenNoVersionIsNamed_ThrowsWithoutArbitrating() {
        assertRejected(() -> validator.arbitrate(false, null, 5L, identicalChange()))
                .withCode("SYNC_CONFLICT")
                .satisfies(rejection -> assertThat(rejection.getMessage()).isEqualTo("Sync version conflict: expected null but found 5"));
    }

    // unparseable-content-falls-through-to-conflict
    @Test
    void arbitrate_WhenStaleAndTheStoredDocumentIsUnparseable_Throws() {
        PlannerContent unparseable = StoredPlannerContent.holding(PlannerContent.builder()
                .title("Stored Title")
                .status(PlannerStatus.SAVED)
                .category("5F")
                .contentSchemaVersion(2)
                .gameContentVersion(6)
                .build(), "{\"equipment\":");

        assertRejected(() -> validator.arbitrate(false, 4L, 5L,
                PlannerChange.between(unparseable, identicalWrite(), MAPPER)))
                .withCode("SYNC_CONFLICT")
                .satisfies(rejection -> assertThat(rejection.getMessage()).isEqualTo("Sync version conflict: expected 4 but found 5"));
    }
}
