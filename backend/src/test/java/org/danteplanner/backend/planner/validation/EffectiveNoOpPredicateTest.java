package org.danteplanner.backend.planner.validation;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.danteplanner.backend.planner.entity.PlannerContent;
import org.danteplanner.backend.planner.entity.PlannerStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The equality that decides whether a stale write is worth a conflict. Content is compared as a
 * parsed tree, so the storage layer's rendering of a document counts as the same document; every
 * other carried field is compared by value, and a field the request does not carry cannot change
 * anything and is therefore ignored.
 */
class EffectiveNoOpPredicateTest {

    private static final String DOCUMENT = """
            {"equipment":{},"selectedGiftIds":["9001"],"observationGiftIds":[],\
            "comprehensiveGiftIds":[],"floorSelections":[]}""";

    /** The same document as {@link #DOCUMENT}: keys reordered, whitespace inserted. */
    private static final String SAME_DOCUMENT_RESTYLED = """
            {
              "floorSelections" : [],
              "selectedGiftIds" : [ "9001" ],
              "comprehensiveGiftIds" : [],
              "observationGiftIds" : [],
              "equipment" : { }
            }""";

    private static final String OTHER_DOCUMENT = """
            {"equipment":{},"selectedGiftIds":["9002"],"observationGiftIds":[],\
            "comprehensiveGiftIds":[],"floorSelections":[]}""";

    private static final UUID DEVICE = UUID.fromString("0b5d1f2e-0000-4000-8000-00000000abcd");

    private final EffectiveNoOpPredicate predicate = new EffectiveNoOpPredicate(new ObjectMapper());

    private static PlannerContent storedRow() {
        return PlannerContent.builder()
                .title("Stored Title")
                .status(PlannerStatus.SAVED)
                .category("5F")
                .content(DOCUMENT)
                .selectedKeywords(Set.of("Combustion", "Sinking"))
                .contentSchemaVersion(2)
                .gameContentVersion(6)
                .deviceId(DEVICE)
                .build();
    }

    /** Every field the stored row already holds, carried verbatim. */
    private static CarriedWrite.CarriedWriteBuilder carryingStoredValues() {
        return CarriedWrite.builder()
                .title("Stored Title")
                .status(PlannerStatus.SAVED)
                .category("5F")
                .content(DOCUMENT)
                .selectedKeywords(Set.of("Combustion", "Sinking"))
                .contentSchemaVersion(2)
                .gameContentVersion(6)
                .deviceId(DEVICE);
    }

    @Test
    @DisplayName("stale-identical-save-acks-current-version: every carried field equal is a no-op")
    void effectiveNoOp_WhenEveryCarriedFieldEqualsTheStoredRow_IsTrue() {
        assertThat(predicate.isEffectiveNoOp(storedRow(), carryingStoredValues().build())).isTrue();
    }

    @Test
    @DisplayName("pulled-rendering-counts-as-identical: content is compared as a tree, not as bytes")
    void effectiveNoOp_WhenContentDiffersOnlyInRendering_IsTrue() {
        CarriedWrite carried = carryingStoredValues().content(SAME_DOCUMENT_RESTYLED).build();

        assertThat(SAME_DOCUMENT_RESTYLED)
                .as("the fixture is only meaningful while the two strings differ byte for byte")
                .isNotEqualTo(DOCUMENT);
        assertThat(predicate.isEffectiveNoOp(storedRow(), carried)).isTrue();
    }

    @Test
    @DisplayName("stale-different-content-conflicts: a document that is not tree-equal is not a no-op")
    void effectiveNoOp_WhenContentTreeDiffers_IsFalse() {
        CarriedWrite carried = carryingStoredValues().content(OTHER_DOCUMENT).build();

        assertThat(predicate.isEffectiveNoOp(storedRow(), carried)).isFalse();
    }

    @Test
    @DisplayName("stale-title-change-still-conflicts: a scalar the request moves is not a no-op")
    void effectiveNoOp_WhenTitleDiffers_IsFalse() {
        CarriedWrite carried = carryingStoredValues().title("Renamed").build();

        assertThat(predicate.isEffectiveNoOp(storedRow(), carried)).isFalse();
    }

    @Test
    @DisplayName("unparseable-content-falls-through-to-conflict: stored side unparseable")
    void effectiveNoOp_WhenStoredContentIsUnparseable_IsFalse() {
        PlannerContent stored = PlannerContent.builder()
                .title("Stored Title")
                .status(PlannerStatus.SAVED)
                .category("5F")
                .content("{\"equipment\":")
                .selectedKeywords(Set.of("Combustion", "Sinking"))
                .contentSchemaVersion(2)
                .gameContentVersion(6)
                .deviceId(DEVICE)
                .build();

        assertThat(predicate.isEffectiveNoOp(stored, carryingStoredValues().build())).isFalse();
    }

    @Test
    @DisplayName("unparseable-content-falls-through-to-conflict: carried side unparseable")
    void effectiveNoOp_WhenCarriedContentIsUnparseable_IsFalse() {
        CarriedWrite carried = carryingStoredValues().content("{\"equipment\":").build();

        assertThat(predicate.isEffectiveNoOp(storedRow(), carried)).isFalse();
    }

    @Test
    void effectiveNoOp_WhenStatusDiffers_IsFalse() {
        assertThat(predicate.isEffectiveNoOp(storedRow(),
                carryingStoredValues().status(PlannerStatus.DRAFT).build())).isFalse();
    }

    @Test
    void effectiveNoOp_WhenCategoryDiffers_IsFalse() {
        assertThat(predicate.isEffectiveNoOp(storedRow(),
                carryingStoredValues().category("10F").build())).isFalse();
    }

    @Test
    void effectiveNoOp_WhenGameContentVersionDiffers_IsFalse() {
        assertThat(predicate.isEffectiveNoOp(storedRow(),
                carryingStoredValues().gameContentVersion(7).build())).isFalse();
    }

    @Test
    void effectiveNoOp_WhenContentSchemaVersionDiffers_IsFalse() {
        assertThat(predicate.isEffectiveNoOp(storedRow(),
                carryingStoredValues().contentSchemaVersion(3).build())).isFalse();
    }

    @Test
    void effectiveNoOp_WhenDeviceIdDiffers_IsFalse() {
        assertThat(predicate.isEffectiveNoOp(storedRow(),
                carryingStoredValues().deviceId(UUID.randomUUID()).build())).isFalse();
    }

    @Test
    void effectiveNoOp_WhenKeywordsDiffer_IsFalse() {
        assertThat(predicate.isEffectiveNoOp(storedRow(),
                carryingStoredValues().selectedKeywords(Set.of("Combustion")).build())).isFalse();
    }

    @Test
    void effectiveNoOp_WhenKeywordsAreCarriedInADifferentOrder_IsTrue() {
        assertThat(predicate.isEffectiveNoOp(storedRow(),
                carryingStoredValues().selectedKeywords(Set.of("Sinking", "Combustion")).build())).isTrue();
    }

    @Test
    @DisplayName("a field the request does not carry changes nothing, so it cannot defeat the no-op")
    void effectiveNoOp_WhenNoFieldIsCarried_IsTrue() {
        assertThat(predicate.isEffectiveNoOp(storedRow(), CarriedWrite.builder().build())).isTrue();
    }

    @Test
    void effectiveNoOp_WhenOnlyContentIsCarriedAndEqual_IsTrue() {
        assertThat(predicate.isEffectiveNoOp(storedRow(),
                CarriedWrite.builder().content(SAME_DOCUMENT_RESTYLED).build())).isTrue();
    }

    @Test
    void effectiveNoOp_WhenKeywordsAreCarriedButTheRowHasNone_IsFalse() {
        PlannerContent stored = PlannerContent.builder()
                .title("Stored Title")
                .status(PlannerStatus.SAVED)
                .category("5F")
                .content(DOCUMENT)
                .contentSchemaVersion(2)
                .gameContentVersion(6)
                .build();

        assertThat(predicate.isEffectiveNoOp(stored, CarriedWrite.builder()
                .selectedKeywords(Set.of())
                .build())).isFalse();
    }
}
