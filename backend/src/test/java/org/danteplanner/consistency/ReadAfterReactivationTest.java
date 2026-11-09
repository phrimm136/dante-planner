package org.danteplanner.consistency;

import java.util.List;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ReadAfterReactivationTest {

    private static final Publish PUBLISHED = new Publish("s1", "p1", 10, 20, new Changed());
    private static final Deactivate DEACTIVATED = new Deactivate("s1", 30, 40, new Changed());
    private static final Reactivate REACTIVATED = new Reactivate("s1", 50, 60, new Changed());
    private static final PublicRead MISSED = new PublicRead("anon", "p1", 61, 70, new NotFound());
    private static final ThreadRead THREAD_MISSED = new ThreadRead("anon", "p1", 61, 70, new NotFound());

    @Test
    void publicRead_WhenItStartsAfterTheReactivationResponseAndAnswers404_IsAViolation() {
        assertThat(violations(PUBLISHED, DEACTIVATED, REACTIVATED, MISSED)).containsExactly(violation(MISSED));
    }

    @Test
    void threadRead_WhenItStartsAfterTheReactivationResponseAndAnswers404_IsAViolation() {
        assertThat(violations(PUBLISHED, DEACTIVATED, REACTIVATED, THREAD_MISSED))
                .containsExactly(violation(THREAD_MISSED));
    }

    @Test
    void reads_WhenFoundShownOrFailed_AreClean() {
        assertThat(violations(PUBLISHED, DEACTIVATED, REACTIVATED,
                new PublicRead("anon", "p1", 61, 70, new Found(3)),
                new PublicRead("anon", "p1", 61, 70, new ReadFailed(503)),
                new ThreadRead("anon", "p1", 61, 70, new Shown()),
                new ThreadRead("anon", "p1", 61, 70, new ReadFailed(503)))).isEmpty();
    }

    @Test
    void read_WhenItStartsAtTheReactivationCompletion_IsNotJudged() {
        assertThat(violations(PUBLISHED, DEACTIVATED, REACTIVATED,
                new PublicRead("anon", "p1", 60, 70, new NotFound()))).isEmpty();
    }

    @Test
    void read_WhenTheReactivationOutcomeIsUnknown_IsNotJudged() {
        assertThat(violations(PUBLISHED, DEACTIVATED, new Reactivate("s1", 50, 60, new Unknown()), MISSED))
                .isEmpty();
    }

    @Test
    void read_WhenNoDeactivationPrecededTheReactivation_IsNotJudged() {
        assertThat(violations(PUBLISHED, REACTIVATED, MISSED)).isEmpty();
    }

    @Test
    void read_WhenThePlannerWasPublishedOnlyAfterTheDeactivationBegan_IsNotJudged() {
        assertThat(violations(new Publish("s1", "p1", 25, 31, new Changed()), DEACTIVATED, REACTIVATED, MISSED))
                .isEmpty();
    }

    @Test
    void read_WhenThePlannerMayHaveBeenUnpublishedOrDeleted_IsClean() {
        assertThat(violations(PUBLISHED, DEACTIVATED, REACTIVATED, new Unpublish("s2", "p1", 62, 80, new Unknown()),
                MISSED)).isEmpty();
        assertThat(violations(PUBLISHED, DEACTIVATED, REACTIVATED, new Delete("s2", "p1", 15, 21, new Deleted()),
                MISSED)).isEmpty();
    }

    @Test
    void read_WhenTheAuthorMayHaveDeactivatedAgain_IsClean() {
        assertThat(violations(PUBLISHED, DEACTIVATED, REACTIVATED, new Deactivate("s1", 65, 75, new Unknown()),
                MISSED)).isEmpty();
    }

    @Test
    void read_WhenOfAnotherPlanner_IsClean() {
        assertThat(violations(PUBLISHED, DEACTIVATED, REACTIVATED,
                new PublicRead("anon", "p2", 61, 70, new NotFound()))).isEmpty();
    }

    @Test
    void ownerRead_WhenItMisses_IsNotJudged() {
        assertThat(violations(PUBLISHED, DEACTIVATED, REACTIVATED, new Read("s2", "p1", 61, 70, new NotFound())))
                .isEmpty();
    }

    private static Violation violation(PublicRead read) {
        return new Violation("read-after-reactivation", read.session(), read.key(), read);
    }

    private static Violation violation(ThreadRead read) {
        return new Violation("read-after-reactivation", read.session(), read.key(), read);
    }

    private static List<Violation> violations(Operation... operations) {
        Verdict verdict = new ReadAfterReactivation().judge(History.of(List.of(operations)));
        assertThat(verdict.anomalies()).isEmpty();
        return verdict.violations();
    }
}
