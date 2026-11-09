package org.danteplanner.consistency;

import java.util.List;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ListHoldsAddedTest {

    private static final Save CREATED = new Save("s1", "p1", 10, 20, new Applied(1));

    @Test
    void ownerList_WhenWholeAndStartedAfterTheCreateResponseWithoutThePlanner_IsAViolation() {
        ListRead list = owner(21, 30, 0, 20, 1, "p2");

        assertThat(violations(CREATED, list))
                .containsExactly(new Violation("owner-list-after-create", "viewer", "p1", list));
    }

    @Test
    void ownerList_WhenItHoldsThePlanner_IsClean() {
        assertThat(violations(CREATED, owner(21, 30, 0, 20, 1, "p1"))).isEmpty();
    }

    @Test
    void ownerList_WhenItStartsAtTheCreateCompletion_IsNotJudged() {
        assertThat(violations(CREATED, owner(20, 30, 0, 20, 0))).isEmpty();
    }

    @Test
    void ownerList_WhenTheSaveWasRejectedOrUnknown_IsClean() {
        assertThat(violations(new Save("s1", "p1", 10, 20, new Rejected(409)),
                new Save("s2", "p1", 10, 20, new Unknown()), owner(25, 30, 0, 20, 0))).isEmpty();
    }

    @Test
    void ownerList_WhenTheReadAskedForALaterPage_IsNotJudged() {
        assertThat(violations(CREATED, owner(25, 30, 1, 20, 3))).isEmpty();
    }

    @Test
    void ownerList_WhenMorePlannersExistThanThePageHolds_IsNotJudged() {
        assertThat(violations(CREATED, owner(25, 30, 0, 2, 3, "p2", "p3"))).isEmpty();
    }

    @Test
    void ownerList_WhenTheTotalEqualsThePageSize_IsJudged() {
        ListRead list = owner(25, 30, 0, 2, 2, "p2", "p3");

        assertThat(violations(CREATED, list))
                .containsExactly(new Violation("owner-list-after-create", "viewer", "p1", list));
    }

    @Test
    void ownerList_WhenADeleteMayHavePrecededTheRead_IsClean() {
        assertThat(violations(CREATED, new Delete("s2", "p1", 29, 40, new Unknown()),
                owner(25, 30, 0, 20, 0))).isEmpty();
    }

    @Test
    void ownerList_WhenTheDeleteBeginsAtTheReadCompletion_DoesNotExcuse() {
        ListRead list = owner(25, 30, 0, 20, 0);

        assertThat(violations(CREATED, new Delete("s2", "p1", 30, 40, new Deleted()), list))
                .containsExactly(new Violation("owner-list-after-create", "viewer", "p1", list));
    }

    @Test
    void ownerList_WhenTwoSavesPrecedeTheRead_ReportsThePlannerOnce() {
        ListRead list = owner(25, 30, 0, 20, 0);

        assertThat(violations(CREATED, new Save("s1", "p1", 21, 22, new Applied(2)), list))
                .containsExactly(new Violation("owner-list-after-create", "viewer", "p1", list));
    }

    @Test
    void catalog_WhenWholeAndStartedAfterAPublishResponseWithoutThePlanner_IsNotJudged() {
        assertThat(violations(new Publish("s1", "p1", 10, 20, new Changed()), CREATED, catalog(25, 30, 0, 20, 0)))
                .isEmpty();
    }

    @Test
    void ownerList_WhenTheDeleteIsOfAnotherPlannerOrRejected_DoesNotExcuse() {
        ListRead list = owner(25, 30, 0, 20, 0);

        assertThat(violations(CREATED, new Delete("s2", "p2", 21, 24, new Deleted()),
                new Delete("s2", "p1", 21, 24, new Rejected(404)), list))
                .containsExactly(new Violation("owner-list-after-create", "viewer", "p1", list));
    }

    private static ListRead owner(long invokedAt, long completedAt, int page, int size, long total, String... keys) {
        return new ListRead("viewer", ListRead.Listing.OWNER, invokedAt, completedAt, page, size, total,
                List.of(keys));
    }

    private static ListRead catalog(long invokedAt, long completedAt, int page, int size, long total,
            String... keys) {
        return new ListRead("anon", ListRead.Listing.CATALOG, invokedAt, completedAt, page, size, total,
                List.of(keys));
    }

    private static List<Violation> violations(Operation... operations) {
        Verdict verdict = new ListHoldsAdded().judge(History.of(List.of(operations)));
        assertThat(verdict.anomalies()).isEmpty();
        return verdict.violations();
    }
}
