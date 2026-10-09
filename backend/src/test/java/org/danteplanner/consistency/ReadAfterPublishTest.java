package org.danteplanner.consistency;

import java.util.List;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ReadAfterPublishTest {

    private static final Publish PUBLISHED = new Publish("s1", "p1", 10, 20, new Changed());
    private static final PublicRead MISSED = new PublicRead("anon", "p1", 25, 30, new NotFound());

    @Test
    void notFound_WhenInvokedAtThePublishCompletion_IsClean() {
        assertThat(violations(PUBLISHED, new PublicRead("anon", "p1", 20, 30, new NotFound()))).isEmpty();
    }

    @Test
    void notFound_WhenInvokedOneTickAfterThePublishCompletion_IsAViolation() {
        PublicRead missed = new PublicRead("anon", "p1", 21, 30, new NotFound());

        assertThat(violations(PUBLISHED, missed)).containsExactly(violation(missed));
    }

    @Test
    void read_WhenFoundOrFailed_IsClean() {
        assertThat(violations(
                PUBLISHED,
                new PublicRead("anon", "p1", 25, 30, new Found(3)),
                new PublicRead("anon", "p1", 25, 30, new ReadFailed(0)))).isEmpty();
    }

    @Test
    void notFound_WhenTheReadIsOfAnotherKey_IsClean() {
        assertThat(violations(PUBLISHED, new PublicRead("anon", "p2", 25, 30, new NotFound()))).isEmpty();
    }

    @Test
    void notFound_WhenThePublishWasRejected_IsClean() {
        assertThat(violations(new Publish("s1", "p1", 10, 20, new Rejected(400)), MISSED)).isEmpty();
    }

    @Test
    void notFound_WhenThePublishOutcomeIsUnknown_IsClean() {
        assertThat(violations(new Publish("s1", "p1", 10, 20, new Unknown()), MISSED)).isEmpty();
    }

    @Test
    void ownerRead_WhenItMissesThePublishedPlanner_IsNotJudged() {
        assertThat(violations(PUBLISHED, new Read("s2", "p1", 25, 30, new NotFound()))).isEmpty();
    }

    @Test
    void unpublish_WhenItCompletesAsThePublishBegins_DoesNotExcuse() {
        assertThat(violations(new Unpublish("s2", "p1", 5, 10, new Changed()), PUBLISHED, MISSED))
                .containsExactly(violation(MISSED));
    }

    @Test
    void unpublish_WhenItCompletesOneTickAfterThePublishBegins_Excuses() {
        assertThat(violations(new Unpublish("s2", "p1", 5, 11, new Changed()), PUBLISHED, MISSED)).isEmpty();
    }

    @Test
    void unpublish_WhenItBeginsAtTheReadCompletion_DoesNotExcuse() {
        assertThat(violations(PUBLISHED, MISSED, new Unpublish("s2", "p1", 30, 40, new Changed())))
                .containsExactly(violation(MISSED));
    }

    @Test
    void unpublish_WhenItBeginsOneTickBeforeTheReadCompletion_Excuses() {
        assertThat(violations(PUBLISHED, MISSED, new Unpublish("s2", "p1", 29, 40, new Changed()))).isEmpty();
    }

    @Test
    void unpublish_WhenRejected_DoesNotExcuse() {
        assertThat(violations(PUBLISHED, new Unpublish("s2", "p1", 26, 35, new Rejected(403)), MISSED))
                .containsExactly(violation(MISSED));
    }

    @Test
    void unpublish_WhenItsOutcomeIsUnknown_Excuses() {
        assertThat(violations(PUBLISHED, new Unpublish("s2", "p1", 26, 35, new Unknown()), MISSED)).isEmpty();
    }

    @Test
    void unpublish_WhenOfAnotherKey_DoesNotExcuse() {
        assertThat(violations(PUBLISHED, new Unpublish("s2", "p2", 26, 35, new Changed()), MISSED))
                .containsExactly(violation(MISSED));
    }

    @Test
    void delete_WhenItCompletesAsThePublishBegins_DoesNotExcuse() {
        assertThat(violations(new Delete("s2", "p1", 5, 10, new Deleted()), PUBLISHED, MISSED))
                .containsExactly(violation(MISSED));
    }

    @Test
    void delete_WhenItCompletesOneTickAfterThePublishBegins_Excuses() {
        assertThat(violations(new Delete("s2", "p1", 5, 11, new Deleted()), PUBLISHED, MISSED)).isEmpty();
    }

    @Test
    void delete_WhenItBeginsAtTheReadCompletion_DoesNotExcuse() {
        assertThat(violations(PUBLISHED, MISSED, new Delete("s2", "p1", 30, 40, new Deleted())))
                .containsExactly(violation(MISSED));
    }

    @Test
    void delete_WhenItBeginsOneTickBeforeTheReadCompletion_Excuses() {
        assertThat(violations(PUBLISHED, MISSED, new Delete("s2", "p1", 29, 40, new Deleted()))).isEmpty();
    }

    @Test
    void delete_WhenRejected_DoesNotExcuse() {
        assertThat(violations(PUBLISHED, new Delete("s2", "p1", 26, 35, new Rejected(409)), MISSED))
                .containsExactly(violation(MISSED));
    }

    @Test
    void delete_WhenItsOutcomeIsUnknown_Excuses() {
        assertThat(violations(PUBLISHED, new Delete("s2", "p1", 26, 35, new Unknown()), MISSED)).isEmpty();
    }

    @Test
    void delete_WhenOfAnotherKey_DoesNotExcuse() {
        assertThat(violations(PUBLISHED, new Delete("s2", "p2", 26, 35, new Deleted()), MISSED))
                .containsExactly(violation(MISSED));
    }

    @Test
    void notFound_WhenAnUnpublishExcusesOnlyTheFirstOfTwoPublishes_IsOneViolation() {
        PublicRead missed = new PublicRead("anon", "p1", 70, 80, new NotFound());

        assertThat(violations(
                PUBLISHED,
                new Unpublish("s2", "p1", 24, 40, new Changed()),
                new Publish("s1", "p1", 50, 60, new Changed()),
                missed)).containsExactly(violation(missed));
    }

    @Test
    void notFound_WhenItFollowsTwoPublishes_IsOneViolation() {
        PublicRead missed = new PublicRead("anon", "p1", 50, 60, new NotFound());

        assertThat(violations(PUBLISHED, new Publish("s2", "p1", 30, 40, new Changed()), missed))
                .containsExactly(violation(missed));
    }

    private static Violation violation(PublicRead read) {
        return new Violation("read-after-publish", read.session(), read.key(), read);
    }

    private static List<Violation> violations(Operation... operations) {
        Verdict verdict = new ReadAfterPublish().judge(History.of(List.of(operations)));
        assertThat(verdict.anomalies()).isEmpty();
        return verdict.violations();
    }
}
