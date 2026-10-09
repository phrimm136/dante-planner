package org.danteplanner.consistency;

import java.util.List;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ReadAfterUnpublishTest {

    private static final Unpublish UNPUBLISHED = new Unpublish("s1", "p1", 10, 20, new Changed());
    private static final PublicRead SHOWN = new PublicRead("anon", "p1", 25, 30, new Found(3));

    @Test
    void found_WhenInvokedAtTheUnpublishCompletion_IsClean() {
        assertThat(violations(UNPUBLISHED, new PublicRead("anon", "p1", 20, 30, new Found(3)))).isEmpty();
    }

    @Test
    void found_WhenInvokedOneTickAfterTheUnpublishCompletion_IsAViolation() {
        PublicRead shown = new PublicRead("anon", "p1", 21, 30, new Found(3));

        assertThat(violations(UNPUBLISHED, shown)).containsExactly(violation(shown));
    }

    @Test
    void read_WhenNotFoundOrFailed_IsClean() {
        assertThat(violations(
                UNPUBLISHED,
                new PublicRead("anon", "p1", 25, 30, new NotFound()),
                new PublicRead("anon", "p1", 25, 30, new ReadFailed(0)))).isEmpty();
    }

    @Test
    void found_WhenTheReadIsOfAnotherKey_IsClean() {
        assertThat(violations(UNPUBLISHED, new PublicRead("anon", "p2", 25, 30, new Found(3)))).isEmpty();
    }

    @Test
    void found_WhenTheUnpublishWasRejected_IsClean() {
        assertThat(violations(new Unpublish("s1", "p1", 10, 20, new Rejected(403)), SHOWN)).isEmpty();
    }

    @Test
    void found_WhenTheUnpublishOutcomeIsUnknown_IsClean() {
        assertThat(violations(new Unpublish("s1", "p1", 10, 20, new Unknown()), SHOWN)).isEmpty();
    }

    @Test
    void ownerRead_WhenItFindsTheUnpublishedPlanner_IsNotJudged() {
        assertThat(violations(UNPUBLISHED, new Read("s2", "p1", 25, 30, new Found(3)))).isEmpty();
    }

    @Test
    void publish_WhenItCompletesAsTheUnpublishBegins_DoesNotExcuse() {
        assertThat(violations(new Publish("s2", "p1", 5, 10, new Changed()), UNPUBLISHED, SHOWN))
                .containsExactly(violation(SHOWN));
    }

    @Test
    void publish_WhenItCompletesOneTickAfterTheUnpublishBegins_Excuses() {
        assertThat(violations(new Publish("s2", "p1", 5, 11, new Changed()), UNPUBLISHED, SHOWN)).isEmpty();
    }

    @Test
    void publish_WhenItBeginsAtTheReadCompletion_DoesNotExcuse() {
        assertThat(violations(UNPUBLISHED, SHOWN, new Publish("s2", "p1", 30, 40, new Changed())))
                .containsExactly(violation(SHOWN));
    }

    @Test
    void publish_WhenItBeginsOneTickBeforeTheReadCompletion_Excuses() {
        assertThat(violations(UNPUBLISHED, SHOWN, new Publish("s2", "p1", 29, 40, new Changed()))).isEmpty();
    }

    @Test
    void publish_WhenRejected_DoesNotExcuse() {
        assertThat(violations(UNPUBLISHED, new Publish("s2", "p1", 24, 40, new Rejected(400)), SHOWN))
                .containsExactly(violation(SHOWN));
    }

    @Test
    void publish_WhenItsOutcomeIsUnknown_Excuses() {
        assertThat(violations(UNPUBLISHED, new Publish("s2", "p1", 24, 40, new Unknown()), SHOWN)).isEmpty();
    }

    @Test
    void publish_WhenOfAnotherKey_DoesNotExcuse() {
        assertThat(violations(UNPUBLISHED, new Publish("s2", "p2", 24, 40, new Changed()), SHOWN))
                .containsExactly(violation(SHOWN));
    }

    @Test
    void found_WhenARepublishExcusesOnlyTheFirstOfTwoUnpublishes_IsOneViolation() {
        PublicRead shown = new PublicRead("anon", "p1", 70, 80, new Found(3));

        assertThat(violations(
                UNPUBLISHED,
                new Publish("s2", "p1", 24, 40, new Changed()),
                new Unpublish("s1", "p1", 50, 60, new Changed()),
                shown)).containsExactly(violation(shown));
    }

    @Test
    void found_WhenItFollowsTwoUnpublishes_IsOneViolation() {
        PublicRead shown = new PublicRead("anon", "p1", 50, 60, new Found(3));

        assertThat(violations(UNPUBLISHED, new Unpublish("s2", "p1", 30, 40, new Changed()), shown))
                .containsExactly(violation(shown));
    }

    private static Violation violation(PublicRead read) {
        return new Violation("read-after-unpublish", read.session(), read.key(), read);
    }

    private static List<Violation> violations(Operation... operations) {
        Verdict verdict = new ReadAfterUnpublish().judge(History.of(List.of(operations)));
        assertThat(verdict.anomalies()).isEmpty();
        return verdict.violations();
    }
}
