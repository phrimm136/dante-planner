package org.danteplanner.backend.planner.sync;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.danteplanner.backend.planner.entity.PlannerContent;
import org.danteplanner.backend.planner.entity.PlannerStatus;
import org.danteplanner.backend.planner.entity.StoredPlannerContent;
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
class PlannerChangeTest {

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

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static PlannerContent storedRow() {
        return StoredPlannerContent.holding(PlannerContent.builder()
                .title("Stored Title")
                .status(PlannerStatus.SAVED)
                .category("5F")
                .selectedKeywords(Set.of("Combustion", "Sinking"))
                .contentSchemaVersion(2)
                .gameContentVersion(6)
                .deviceId(DEVICE)
                .build(), DOCUMENT);
    }

    private static PlannerChange change(PlannerContent stored, CarriedWrite carried) {
        return PlannerChange.between(stored, carried, MAPPER);
    }

    private static boolean isNone(PlannerContent stored, CarriedWrite carried) {
        return change(stored, carried).isNone();
    }

    /** Every field the stored row already holds, carried verbatim. */
    private static CarriedWrite.CarriedWriteBuilder carryingStoredValues() {
        return CarriedWrite.builder()
                .title("Stored Title")
                .status(PlannerStatus.SAVED)
                .category("5F")
                .content(DOCUMENT)
                .contentSchemaVersion(2)
                .gameContentVersion(6)
                .deviceId(DEVICE);
    }

    @Test
    @DisplayName("stale-identical-save-acks-current-version: every carried field equal is a no-op")
    void isNone_WhenEveryCarriedFieldEqualsTheStoredRow_IsTrue() {
        assertThat(isNone(storedRow(), carryingStoredValues().build())).isTrue();
    }

    @Test
    @DisplayName("pulled-rendering-counts-as-identical: content is compared as a tree, not as bytes")
    void isNone_WhenContentDiffersOnlyInRendering_IsTrue() {
        CarriedWrite carried = carryingStoredValues().content(SAME_DOCUMENT_RESTYLED).build();

        assertThat(SAME_DOCUMENT_RESTYLED)
                .as("the fixture is only meaningful while the two strings differ byte for byte")
                .isNotEqualTo(DOCUMENT);
        assertThat(isNone(storedRow(), carried)).isTrue();
    }

    @Test
    void isNone_WhenContentDiffersOnlyInNumberRendering_IsTrue() {
        PlannerContent stored = StoredPlannerContent.holding(PlannerContent.builder().build(),
                "{\"deploymentOrder\":[5.0],\"equipment\":{}}");
        CarriedWrite carried = CarriedWrite.builder()
                .content("{\"equipment\":{},\"deploymentOrder\":[5]}")
                .build();

        assertThat(isNone(stored, carried)).isTrue();
    }

    @Test
    @DisplayName("stale-different-content-conflicts: a document that is not tree-equal is not a no-op")
    void isNone_WhenContentTreeDiffers_IsFalse() {
        CarriedWrite carried = carryingStoredValues().content(OTHER_DOCUMENT).build();

        assertThat(isNone(storedRow(), carried)).isFalse();
    }

    @Test
    @DisplayName("stale-title-change-still-conflicts: a scalar the request moves is not a no-op")
    void isNone_WhenTitleDiffers_IsFalse() {
        CarriedWrite carried = carryingStoredValues().title("Renamed").build();

        assertThat(isNone(storedRow(), carried)).isFalse();
    }

    @Test
    @DisplayName("unparseable-content-falls-through-to-conflict: stored side unparseable")
    void isNone_WhenStoredContentIsUnparseable_IsFalse() {
        PlannerContent stored = StoredPlannerContent.holding(PlannerContent.builder()
                .title("Stored Title")
                .status(PlannerStatus.SAVED)
                .category("5F")
                .selectedKeywords(Set.of("Combustion", "Sinking"))
                .contentSchemaVersion(2)
                .gameContentVersion(6)
                .deviceId(DEVICE)
                .build(), "{\"equipment\":");

        assertThat(isNone(stored, carryingStoredValues().build())).isFalse();
    }

    @Test
    @DisplayName("unparseable-content-falls-through-to-conflict: carried side unparseable")
    void isNone_WhenCarriedContentIsUnparseable_IsFalse() {
        CarriedWrite carried = carryingStoredValues().content("{\"equipment\":").build();

        assertThat(isNone(storedRow(), carried)).isFalse();
    }

    @Test
    void isNone_WhenStatusDiffers_IsFalse() {
        assertThat(isNone(storedRow(),
                carryingStoredValues().status(PlannerStatus.DRAFT).build())).isFalse();
    }

    @Test
    void isNone_WhenCategoryDiffers_IsFalse() {
        assertThat(isNone(storedRow(),
                carryingStoredValues().category("10F").build())).isFalse();
    }

    @Test
    void isNone_WhenGameContentVersionDiffers_IsFalse() {
        assertThat(isNone(storedRow(),
                carryingStoredValues().gameContentVersion(7).build())).isFalse();
    }

    @Test
    void isNone_WhenContentSchemaVersionDiffers_IsFalse() {
        assertThat(isNone(storedRow(),
                carryingStoredValues().contentSchemaVersion(3).build())).isFalse();
    }

    @Test
    void isNone_WhenDeviceIdDiffers_IsFalse() {
        assertThat(isNone(storedRow(),
                carryingStoredValues().deviceId(UUID.randomUUID()).build())).isFalse();
    }

    @Test
    @DisplayName("a field the request does not carry changes nothing, so it cannot defeat the no-op")
    void isNone_WhenNoFieldIsCarried_IsTrue() {
        assertThat(isNone(storedRow(), CarriedWrite.builder().build())).isTrue();
    }

    @Test
    void isNone_WhenOnlyContentIsCarriedAndEqual_IsTrue() {
        assertThat(isNone(storedRow(),
                CarriedWrite.builder().content(SAME_DOCUMENT_RESTYLED).build())).isTrue();
    }

    @Test
    void contentChanged_WhenCarriedContentIsTheSameDocumentWithDifferentWhitespace_IsFalse() {
        CarriedWrite carried = carryingStoredValues().content(SAME_DOCUMENT_RESTYLED).build();

        assertThat(change(storedRow(), carried).contentChanged()).isFalse();
    }

    @Test
    void contentChanged_WhenCarriedContentIsUnparseable_IsTrue() {
        CarriedWrite carried = carryingStoredValues().content("{\"equipment\":").build();

        assertThat(change(storedRow(), carried).contentChanged()).isTrue();
    }

    @Test
    void categoryOnly_WhenOnlyTheCategoryMoves_IsTrue() {
        CarriedWrite carried = carryingStoredValues().category("10F").content(SAME_DOCUMENT_RESTYLED).build();

        assertThat(change(storedRow(), carried).categoryOnly()).isTrue();
    }

    @Test
    void categoryOnly_WhenTheCategoryAndTheGameContentVersionMove_IsFalse() {
        CarriedWrite carried = carryingStoredValues().category("10F").gameContentVersion(7).build();

        assertThat(change(storedRow(), carried).categoryOnly()).isFalse();
    }

    @Test
    void categoryOnly_WhenTheCategoryAndTheContentMove_IsFalse() {
        CarriedWrite carried = carryingStoredValues().category("10F").content(OTHER_DOCUMENT).build();

        assertThat(change(storedRow(), carried).categoryOnly()).isFalse();
    }

    @Test
    void categoryOnly_WhenTheCategoryIsUnchanged_IsFalse() {
        assertThat(change(storedRow(), carryingStoredValues().build()).categoryOnly()).isFalse();
    }
}
