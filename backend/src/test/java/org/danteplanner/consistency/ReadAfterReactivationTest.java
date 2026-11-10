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
    void read_WhenThePublishOverlapsTheDeactivationAndCompletesBeforeTheReactivation_IsAViolation() {
        PublicRead missed = new PublicRead("anon", "p1", 30, 32, new NotFound());
        ThreadRead threadMissed = new ThreadRead("anon", "p1", 30, 32, new NotFound());

        assertThat(violations(new Publish("s1", "p1", 10, 22, new Changed()),
                new Deactivate("s1", 20, 24, new Changed()), new Reactivate("s1", 26, 28, new Changed()),
                missed, threadMissed))
                .containsExactly(violation(missed), violation(threadMissed));
    }

    @Test
    void read_WhenAnUnpublishMayFollowThePublishThatOverlapsTheDeactivation_IsClean() {
        assertThat(violations(new Publish("s1", "p1", 10, 22, new Changed()),
                new Deactivate("s1", 20, 24, new Changed()), new Unpublish("s2", "p1", 23, 25, new Unknown()),
                new Reactivate("s1", 26, 28, new Changed()),
                new PublicRead("anon", "p1", 30, 32, new NotFound()))).isEmpty();
    }

    @Test
    void read_WhenThePlannerWasPublishedOnlyAfterTheReactivationBegan_IsNotJudged() {
        assertThat(violations(DEACTIVATED, new Publish("s1", "p1", 55, 58, new Changed()), REACTIVATED, MISSED))
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

    @Test
    void read_WhenTheOnlyEarlierDeactivationWasRejected_IsNotJudged() {
        assertThat(violations(PUBLISHED, new Deactivate("s1", 30, 40, new Rejected(403)), REACTIVATED, MISSED))
                .isEmpty();
    }

    @Test
    void read_WhenTheDeactivationCompletesAsTheReactivationBegins_IsAViolation() {
        assertThat(violations(PUBLISHED, new Deactivate("s1", 30, 50, new Changed()), REACTIVATED, MISSED))
                .containsExactly(violation(MISSED));
    }

    @Test
    void read_WhenTheOnlyDeactivationBeginsAfterTheRead_IsNotJudged() {
        assertThat(violations(PUBLISHED, REACTIVATED, MISSED, new Deactivate("s1", 80, 90, new Changed())))
                .isEmpty();
    }

    @Test
    void read_WhenALaterDeactivationWasRejected_IsAViolation() {
        assertThat(violations(PUBLISHED, DEACTIVATED, REACTIVATED, new Deactivate("s1", 62, 68, new Rejected(403)),
                MISSED)).containsExactly(violation(MISSED));
    }

    @Test
    void read_WhenAnotherDeactivationCompletesAsTheReactivationBegins_IsAViolation() {
        assertThat(violations(PUBLISHED, DEACTIVATED, new Deactivate("s1", 45, 50, new Changed()), REACTIVATED,
                MISSED)).containsExactly(violation(MISSED));
    }

    @Test
    void read_WhenALaterDeactivationBeginsAtTheReadCompletion_IsAViolation() {
        assertThat(violations(PUBLISHED, DEACTIVATED, REACTIVATED, MISSED,
                new Deactivate("s1", 70, 80, new Changed()))).containsExactly(violation(MISSED));
    }

    @Test
    void read_WhenThePublishWasRejected_IsNotJudged() {
        assertThat(violations(new Publish("s1", "p1", 10, 20, new Rejected(400)), DEACTIVATED, REACTIVATED,
                MISSED)).isEmpty();
    }

    @Test
    void read_WhenThePublishCompletesAsTheReactivationBegins_IsNotJudged() {
        assertThat(violations(new Publish("s1", "p1", 10, 50, new Changed()), DEACTIVATED, REACTIVATED, MISSED))
                .isEmpty();
    }

    @Test
    void read_WhenTheOnlyUnpublishIsOfAnotherPlannerOrRejected_IsAViolation() {
        assertThat(violations(PUBLISHED, DEACTIVATED, REACTIVATED, new Unpublish("s2", "p2", 62, 65, new Changed()),
                new Unpublish("s2", "p1", 62, 65, new Rejected(403)), MISSED))
                .containsExactly(violation(MISSED));
    }

    @Test
    void read_WhenTheUnpublishCompletedAsThePublishBeganOrBeganAtTheReadCompletion_IsAViolation() {
        assertThat(violations(new Unpublish("s2", "p1", 5, 10, new Changed()), PUBLISHED, DEACTIVATED, REACTIVATED,
                MISSED, new Unpublish("s2", "p1", 70, 75, new Changed()))).containsExactly(violation(MISSED));
    }

    @Test
    void read_WhenTheUnpublishCompletedBeforeThePublishBegan_IsAViolation() {
        assertThat(violations(new Unpublish("s2", "p1", 1, 5, new Changed()), PUBLISHED, DEACTIVATED, REACTIVATED,
                MISSED)).containsExactly(violation(MISSED));
    }

    @Test
    void read_WhenTheOnlyDeleteIsOfAnotherPlannerOrRejected_IsAViolation() {
        assertThat(violations(PUBLISHED, DEACTIVATED, REACTIVATED, new Delete("s2", "p2", 62, 65, new Deleted()),
                new Delete("s2", "p1", 62, 65, new Rejected(404)), MISSED))
                .containsExactly(violation(MISSED));
    }

    @Test
    void read_WhenTheDeleteCompletedAsThePublishBeganOrBeganAtTheReadCompletion_IsAViolation() {
        assertThat(violations(new Delete("s2", "p1", 5, 10, new Deleted()), PUBLISHED, DEACTIVATED, REACTIVATED,
                MISSED, new Delete("s2", "p1", 70, 75, new Deleted()))).containsExactly(violation(MISSED));
    }

    @Test
    void read_WhenTheDeleteCompletedBeforeThePublishBegan_IsAViolation() {
        assertThat(violations(new Delete("s2", "p1", 1, 5, new Deleted()), PUBLISHED, DEACTIVATED, REACTIVATED,
                MISSED)).containsExactly(violation(MISSED));
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
