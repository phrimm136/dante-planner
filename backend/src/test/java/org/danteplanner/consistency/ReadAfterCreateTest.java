package org.danteplanner.consistency;

import java.util.List;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ReadAfterCreateTest {

    private static final Save CREATED = new Save("s1", "p1", 10, 20, new Applied(1));
    private static final Read MISSED = new Read("s2", "p1", 25, 30, new NotFound());

    @Test
    void notFound_WhenAnotherDeviceReadsAfterTheSave_IsAViolation() {
        assertThat(violations(CREATED, MISSED)).containsExactly(violation(MISSED));
    }

    @Test
    void notFound_WhenInvokedAtTheSaveCompletion_IsClean() {
        assertThat(violations(CREATED, new Read("s2", "p1", 20, 30, new NotFound()))).isEmpty();
    }

    @Test
    void notFound_WhenInvokedOneTickAfterTheSaveCompletion_IsAViolation() {
        Read missed = new Read("s2", "p1", 21, 30, new NotFound());

        assertThat(violations(CREATED, missed)).containsExactly(violation(missed));
    }

    @Test
    void read_WhenFoundOrFailed_IsClean() {
        assertThat(violations(
                CREATED,
                new Read("s2", "p1", 25, 30, new Found(1)),
                new Read("s2", "p1", 25, 30, new ReadFailed(0)))).isEmpty();
    }

    @Test
    void notFound_WhenTheReadIsOfAnotherKey_IsClean() {
        assertThat(violations(CREATED, new Read("s2", "p2", 25, 30, new NotFound()))).isEmpty();
    }

    @Test
    void notFound_WhenTheSaveWasRejected_IsClean() {
        assertThat(violations(new Save("s1", "p1", 10, 20, new Rejected(400)), MISSED)).isEmpty();
    }

    @Test
    void notFound_WhenTheSaveOutcomeIsUnknown_IsClean() {
        assertThat(violations(new Save("s1", "p1", 10, 20, new Unknown()), MISSED)).isEmpty();
    }

    @Test
    void publicRead_WhenItMissesACreatedPlanner_IsNotJudged() {
        assertThat(violations(CREATED, new PublicRead("anon", "p1", 25, 30, new NotFound()))).isEmpty();
    }

    @Test
    void notFound_WhenADeleteCompletedBeforeIt_IsClean() {
        assertThat(violations(CREATED, new Delete("s1", "p1", 21, 24, new Deleted()), MISSED)).isEmpty();
    }

    @Test
    void notFound_WhenADeleteOutcomeIsUnknown_IsClean() {
        assertThat(violations(CREATED, new Delete("s1", "p1", 21, 24, new Unknown()), MISSED)).isEmpty();
    }

    @Test
    void notFound_WhenTheDeleteWasRejected_IsAViolation() {
        assertThat(violations(CREATED, new Delete("s1", "p1", 21, 24, new Rejected(409)), MISSED))
                .containsExactly(violation(MISSED));
    }

    @Test
    void notFound_WhenTheDeleteBeginsAtTheReadCompletion_IsAViolation() {
        assertThat(violations(CREATED, MISSED, new Delete("s1", "p1", 30, 40, new Deleted())))
                .containsExactly(violation(MISSED));
    }

    @Test
    void notFound_WhenTheDeleteBeginsOneTickBeforeTheReadCompletion_IsClean() {
        assertThat(violations(CREATED, MISSED, new Delete("s1", "p1", 29, 40, new Deleted()))).isEmpty();
    }

    @Test
    void notFound_WhenTheDeleteIsOfAnotherKey_IsAViolation() {
        assertThat(violations(CREATED, new Delete("s1", "p2", 21, 24, new Deleted()), MISSED))
                .containsExactly(violation(MISSED));
    }

    @Test
    void notFound_WhenTheAuthorReadsItsOwnSave_IsLeftToAuthorReadYourWrites() {
        assertThat(violations(CREATED, new Read("s1", "p1", 25, 30, new NotFound()))).isEmpty();
    }

    @Test
    void notFound_WhenTheAuthorAndAnotherDeviceBothMiss_IsOneViolationForTheOtherDevice() {
        assertThat(violations(CREATED, new Read("s1", "p1", 25, 30, new NotFound()), MISSED))
                .containsExactly(violation(MISSED));
    }

    @Test
    void notFound_WhenTwoSavesPrecedeIt_IsOneViolation() {
        assertThat(violations(CREATED, new Save("s3", "p1", 21, 24, new Applied(2)), MISSED))
                .containsExactly(violation(MISSED));
    }

    private static Violation violation(Read read) {
        return new Violation("read-after-create", read.session(), read.key(), read);
    }

    private static List<Violation> violations(Operation... operations) {
        Verdict verdict = new ReadAfterCreate().judge(History.of(List.of(operations)));
        assertThat(verdict.anomalies()).isEmpty();
        return verdict.violations();
    }
}
