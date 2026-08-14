package org.danteplanner.backend.planner.entity;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The digest identifies the document a save carried. Every save carrying one re-derives it, so the
 * value follows the string the client sent rather than the form storage returns; a save carrying no
 * document leaves it alone.
 */
class PlannerContentDigestTest {

    private static final String DOCUMENT = "{\"selectedGiftIds\":[\"9001\"]}";
    private static final String OTHER_DOCUMENT = "{\"selectedGiftIds\":[\"9002\"]}";

    /**
     * Drives the JPA callbacks the persistence provider would: the persist that computes the first
     * digest, then the load that leaves the row carrying no assignment of its own.
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
    void digest_WhenResavedWithTheSameDocument_KeepsTheSameValue() {
        PlannerContent contentRow = storedContent(DOCUMENT);
        String atCreate = contentRow.contentDigestHex();

        contentRow.setContent(DOCUMENT);
        contentRow.recordSave();

        assertThat(contentRow.contentDigestHex()).isEqualTo(atCreate);
    }

    @Test
    void digest_WhenAStoredDocumentIsResavedVerbatim_HashesTheAssignedString() {
        PlannerContent contentRow = storedContent(DOCUMENT);
        String storedForm = "{\"selectedGiftIds\": [\"9001\"]}";

        contentRow.setContent(storedForm);
        contentRow.recordSave();

        assertThat(contentRow.contentDigestHex())
                .as("the digest follows what the client sent, not what it once identified")
                .isNotEqualTo(storedContent(DOCUMENT).contentDigestHex());
        assertThat(contentRow.contentDigestHex())
                .isEqualTo(storedContent(storedForm).contentDigestHex());
    }

    @Test
    void digest_WhenComputedAtCreate_IsSixtyFourLowercaseHexChars() {
        PlannerContent contentRow = storedContent(DOCUMENT);

        assertThat(contentRow.contentDigestHex()).matches("[0-9a-f]{64}");
    }
}
