package org.danteplanner.backend.planner.entity;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The digest tracks the document, not the save. A save that leaves the content string byte-identical
 * moves the sync version and nothing else; only a moved document re-derives the digest.
 */
class PlannerContentDigestTest {

    private static final String DOCUMENT = "{\"selectedGiftIds\":[\"9001\"]}";
    private static final String OTHER_DOCUMENT = "{\"selectedGiftIds\":[\"9002\"]}";

    /**
     * Drives the JPA callbacks the persistence provider would: the persist that computes the first
     * digest, then the load that captures the searchable values {@code recordSave} compares against.
     */
    private PlannerContent storedContent(String content) {
        PlannerContent contentRow = PlannerContent.builder()
                .title("Test Planner")
                .category("5F")
                .content(content)
                .gameContentVersion(6)
                .build();
        contentRow.onCreate();
        contentRow.onLoad();
        return contentRow;
    }

    @Test
    void digest_WhenSavedTwiceWithIdenticalContent_TracksOnlyTheSyncVersion() {
        PlannerContent contentRow = storedContent(DOCUMENT);
        String atCreate = contentRow.contentDigestHex();

        contentRow.recordSave();
        contentRow.recordSave();

        assertThat(contentRow.contentDigestHex()).isEqualTo(atCreate);
        assertThat(contentRow.getSyncVersion()).isEqualTo(3L);
    }

    @Test
    void digest_WhenContentChanges_MovesWithTheSyncVersion() {
        PlannerContent contentRow = storedContent(DOCUMENT);
        String atCreate = contentRow.contentDigestHex();

        contentRow.setContent(OTHER_DOCUMENT);
        contentRow.recordSave();

        assertThat(contentRow.contentDigestHex()).isNotEqualTo(atCreate);
        assertThat(contentRow.getSyncVersion()).isEqualTo(2L);
    }

    @Test
    void digest_WhenOnlyTheTitleChanges_StaysWithTheUnmovedDocument() {
        PlannerContent contentRow = storedContent(DOCUMENT);
        String atCreate = contentRow.contentDigestHex();

        contentRow.setTitle("Renamed");
        contentRow.recordSave();

        assertThat(contentRow.contentDigestHex()).isEqualTo(atCreate);
        assertThat(contentRow.getContent()).isEqualTo(DOCUMENT);
    }

    @Test
    void digest_WhenComputedAtCreate_IsSixtyFourLowercaseHexChars() {
        PlannerContent contentRow = storedContent(DOCUMENT);

        assertThat(contentRow.contentDigestHex()).matches("[0-9a-f]{64}");
    }
}
