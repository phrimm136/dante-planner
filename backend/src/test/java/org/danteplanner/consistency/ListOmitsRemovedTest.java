package org.danteplanner.consistency;

import java.util.List;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ListOmitsRemovedTest {

    private static final Delete DELETED = new Delete("s1", "p1", 10, 20, new Deleted());
    private static final Unpublish UNPUBLISHED = new Unpublish("s1", "p1", 10, 20, new Changed());

    @Test
    void ownerList_WhenItStartsAfterTheDeleteResponseAndHoldsThePlanner_IsAViolation() {
        ListRead list = owner(21, 30, "p1");

        assertThat(violations(ListRead.Listing.OWNER, DELETED, list))
                .containsExactly(new Violation("owner-list-after-delete", "viewer", "p1", list));
    }

    @Test
    void ownerList_WhenItStartsAtTheDeleteCompletion_IsNotJudged() {
        assertThat(violations(ListRead.Listing.OWNER, DELETED, owner(20, 30, "p1"))).isEmpty();
    }

    @Test
    void ownerList_WhenTheDeleteWasRejectedOrUnknown_IsClean() {
        assertThat(violations(ListRead.Listing.OWNER, new Delete("s1", "p1", 10, 20, new Rejected(404)),
                new Delete("s2", "p1", 10, 20, new Unknown()), owner(25, 30, "p1"))).isEmpty();
    }

    @Test
    void ownerList_WhenASaveOutlivesTheDelete_IsClean() {
        assertThat(violations(ListRead.Listing.OWNER, DELETED, new Save("s2", "p1", 15, 22, new Applied(4)),
                owner(25, 30, "p1"))).isEmpty();
    }

    @Test
    void ownerList_WhenThePlannerWasOnlyUnpublished_IsClean() {
        assertThat(violations(ListRead.Listing.OWNER, UNPUBLISHED, owner(25, 30, "p1"))).isEmpty();
    }

    @Test
    void ownerList_WhenItIsOnALaterPage_IsStillJudged() {
        ListRead list = new ListRead("viewer", ListRead.Listing.OWNER, 25, 30, 2, 5, 40, List.of("p1"));

        assertThat(violations(ListRead.Listing.OWNER, DELETED, list))
                .containsExactly(new Violation("owner-list-after-delete", "viewer", "p1", list));
    }

    @Test
    void catalog_WhenItStartsAfterTheUnpublishResponseAndHoldsThePlanner_IsAViolation() {
        ListRead list = catalog(21, 30, "p1");

        assertThat(violations(ListRead.Listing.CATALOG, UNPUBLISHED, list))
                .containsExactly(new Violation("catalog-after-withdrawal", "anon", "p1", list));
    }

    @Test
    void catalog_WhenItStartsAfterTheDeleteResponseAndHoldsThePlanner_IsAViolation() {
        ListRead list = catalog(21, 30, "p1");

        assertThat(violations(ListRead.Listing.CATALOG, DELETED, list))
                .containsExactly(new Violation("catalog-after-withdrawal", "anon", "p1", list));
    }

    @Test
    void catalog_WhenAPublishMayHaveFollowedTheUnpublish_IsClean() {
        assertThat(violations(ListRead.Listing.CATALOG, UNPUBLISHED, new Publish("s2", "p1", 22, 29, new Unknown()),
                catalog(25, 30, "p1"))).isEmpty();
    }

    @Test
    void catalog_WhenTheRepublishCompletedBeforeTheUnpublishBegan_DoesNotExcuse() {
        ListRead list = catalog(25, 30, "p1");

        assertThat(violations(ListRead.Listing.CATALOG, new Publish("s2", "p1", 5, 10, new Changed()), UNPUBLISHED,
                list)).containsExactly(new Violation("catalog-after-withdrawal", "anon", "p1", list));
    }

    @Test
    void catalog_WhenItOverlapsTheUnpublish_IsNotJudged() {
        assertThat(violations(ListRead.Listing.CATALOG, UNPUBLISHED, catalog(15, 30, "p1"))).isEmpty();
    }

    @Test
    void catalog_WhenItOmitsTheUnpublishedPlanner_IsClean() {
        assertThat(violations(ListRead.Listing.CATALOG, UNPUBLISHED, catalog(25, 30, "p2"))).isEmpty();
    }

    @Test
    void ownerRule_WhenTheListIsTheCatalog_IsNotJudged() {
        assertThat(violations(ListRead.Listing.OWNER, DELETED, catalog(25, 30, "p1"))).isEmpty();
    }

    @Test
    void catalog_WhenTheUnpublishWasRejected_IsClean() {
        assertThat(violations(ListRead.Listing.CATALOG, new Unpublish("s1", "p1", 10, 20, new Rejected(403)),
                catalog(25, 30, "p1"))).isEmpty();
    }

    @Test
    void catalog_WhenItStartsAtTheUnpublishCompletion_IsNotJudged() {
        assertThat(violations(ListRead.Listing.CATALOG, UNPUBLISHED, catalog(20, 30, "p1"))).isEmpty();
    }

    @Test
    void catalog_WhenTheLaterPublishIsOfAnotherPlannerOrRejected_DoesNotExcuse() {
        ListRead list = catalog(25, 30, "p1");

        assertThat(violations(ListRead.Listing.CATALOG, UNPUBLISHED, new Publish("s2", "p2", 22, 24, new Changed()),
                new Publish("s2", "p1", 22, 24, new Rejected(400)), list))
                .containsExactly(new Violation("catalog-after-withdrawal", "anon", "p1", list));
    }

    @Test
    void catalog_WhenTheRepublishBeginsAtTheReadCompletion_DoesNotExcuse() {
        ListRead list = catalog(25, 30, "p1");

        assertThat(violations(ListRead.Listing.CATALOG, UNPUBLISHED, list,
                new Publish("s2", "p1", 30, 35, new Changed())))
                .containsExactly(new Violation("catalog-after-withdrawal", "anon", "p1", list));
    }

    private static ListRead owner(long invokedAt, long completedAt, String... keys) {
        return new ListRead("viewer", ListRead.Listing.OWNER, invokedAt, completedAt, 0, 20, keys.length,
                List.of(keys));
    }

    private static ListRead catalog(long invokedAt, long completedAt, String... keys) {
        return new ListRead("anon", ListRead.Listing.CATALOG, invokedAt, completedAt, 0, 20, keys.length,
                List.of(keys));
    }

    private static List<Violation> violations(ListRead.Listing listing, Operation... operations) {
        Verdict verdict = new ListOmitsRemoved(listing).judge(History.of(List.of(operations)));
        assertThat(verdict.anomalies()).isEmpty();
        return verdict.violations();
    }
}
