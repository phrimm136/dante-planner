package org.danteplanner.consistency;

import java.util.List;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ReadAfterDeleteTest {

    private static final Delete DELETED = new Delete("s1", "p1", 10, 20, new Deleted());

    @Test
    void found_WhenInvokedAtTheDeleteCompletion_IsClean() {
        assertThat(violations(DELETED, new Read("s2", "p1", 20, 30, new Found(3)))).isEmpty();
    }

    @Test
    void found_WhenInvokedOneTickAfterTheDeleteCompletion_IsAViolation() {
        Read resurrected = new Read("s2", "p1", 21, 30, new Found(3));

        assertThat(violations(DELETED, resurrected))
                .containsExactly(new Violation("read-after-delete", "s2", "p1", resurrected));
    }

    @Test
    void found_WhenTheReadIsOfAnotherKey_IsClean() {
        assertThat(violations(DELETED, new Read("s2", "p2", 25, 30, new Found(3)))).isEmpty();
    }

    @Test
    void found_WhenTheDeleteWasRejected_IsClean() {
        assertThat(violations(
                new Delete("s1", "p1", 10, 20, new Rejected(404)),
                new Read("s2", "p1", 25, 30, new Found(3)))).isEmpty();
    }

    @Test
    void found_WhenTheDeleteOutcomeIsUnknown_IsClean() {
        assertThat(violations(
                new Delete("s1", "p1", 10, 20, new Unknown()),
                new Read("s2", "p1", 25, 30, new Found(3)))).isEmpty();
    }

    @Test
    void read_WhenNotFoundOrFailed_IsClean() {
        assertThat(violations(
                DELETED,
                new Read("s2", "p1", 25, 30, new NotFound()),
                new Read("s2", "p1", 25, 30, new ReadFailed(0)))).isEmpty();
    }

    @Test
    void key_WhenARejectedSaveCompletesAfterTheDeleteBegins_IsJudged() {
        Read resurrected = new Read("s2", "p1", 25, 30, new Found(3));

        assertThat(violations(new Save("s3", "p1", 5, 11, new Rejected(409)), DELETED, resurrected))
                .containsExactly(new Violation("read-after-delete", "s2", "p1", resurrected));
    }

    @Test
    void key_WhenAnAppliedSaveCompletesAfterTheDeleteBegins_IsSkipped() {
        assertThat(violations(
                new Save("s3", "p1", 5, 11, new Applied(4)),
                DELETED,
                new Read("s2", "p1", 25, 30, new Found(4)))).isEmpty();
    }

    @Test
    void key_WhenAnUnknownSaveCompletesAfterTheDeleteBegins_IsSkipped() {
        assertThat(violations(
                new Save("s3", "p1", 5, 11, new Unknown()),
                DELETED,
                new Read("s2", "p1", 25, 30, new Found(4)))).isEmpty();
    }

    @Test
    void key_WhenASaveCompletesAsTheDeleteBegins_IsJudged() {
        Read resurrected = new Read("s2", "p1", 25, 30, new Found(3));

        assertThat(violations(new Save("s3", "p1", 5, 10, new Applied(3)), DELETED, resurrected))
                .containsExactly(new Violation("read-after-delete", "s2", "p1", resurrected));
    }

    @Test
    void key_WhenTheLateSaveIsOfAnotherKey_IsJudged() {
        Read resurrected = new Read("s2", "p1", 25, 30, new Found(3));

        assertThat(violations(new Save("s3", "p2", 5, 50, new Applied(3)), DELETED, resurrected))
                .containsExactly(new Violation("read-after-delete", "s2", "p1", resurrected));
    }

    @Test
    void key_WhenTheLateSaveFollowsOnlyARejectedDelete_IsJudged() {
        Read resurrected = new Read("s2", "p1", 25, 30, new Found(3));

        assertThat(violations(
                new Delete("s1", "p1", 1, 2, new Rejected(404)),
                new Save("s3", "p1", 1, 5, new Applied(3)),
                DELETED,
                resurrected)).containsExactly(new Violation("read-after-delete", "s2", "p1", resurrected));
    }

    @Test
    void found_WhenItFollowsTwoDeletes_IsOneViolation() {
        Read resurrected = new Read("s2", "p1", 50, 60, new Found(3));

        assertThat(violations(DELETED, new Delete("s3", "p1", 30, 40, new Deleted()), resurrected))
                .containsExactly(new Violation("read-after-delete", "s2", "p1", resurrected));
    }

    @Test
    void publicRead_WhenItFindsTheRowAfterTheDeleteCompletion_IsAViolation() {
        PublicRead resurrected = new PublicRead("anon", "p1", 21, 30, new Found(3));

        assertThat(violations(DELETED, resurrected))
                .containsExactly(new Violation("read-after-delete", "anon", "p1", resurrected));
    }

    @Test
    void publicRead_WhenNotFoundOrFailed_IsClean() {
        assertThat(violations(
                DELETED,
                new PublicRead("anon", "p1", 25, 30, new NotFound()),
                new PublicRead("anon", "p1", 25, 30, new ReadFailed(0)))).isEmpty();
    }

    @Test
    void reads_WhenAnOwnerAndAPublicReadBothFindTheRow_AreOneViolationEachInHistoryOrder() {
        PublicRead publicRead = new PublicRead("anon", "p1", 25, 30, new Found(3));
        Read ownerRead = new Read("s2", "p1", 35, 40, new Found(3));

        assertThat(violations(DELETED, publicRead, ownerRead)).containsExactly(
                new Violation("read-after-delete", "anon", "p1", publicRead),
                new Violation("read-after-delete", "s2", "p1", ownerRead));
    }

    @Test
    void publicRead_WhenAnAppliedSaveCompletesAfterTheDeleteBegins_IsSkipped() {
        assertThat(violations(
                new Save("s3", "p1", 5, 11, new Applied(4)),
                DELETED,
                new PublicRead("anon", "p1", 25, 30, new Found(4)))).isEmpty();
    }

    private static List<Violation> violations(Operation... operations) {
        Verdict verdict = new ReadAfterDelete().judge(History.of(List.of(operations)));
        assertThat(verdict.anomalies()).isEmpty();
        return verdict.violations();
    }
}
