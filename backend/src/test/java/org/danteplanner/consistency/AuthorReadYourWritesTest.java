package org.danteplanner.consistency;

import java.util.List;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AuthorReadYourWritesTest {

    private static final Save APPLIED_5 = new Save("s1", "p1", 10, 20, new Applied(5));

    @Test
    void read_WhenTheSaveIsAnotherSessions_IsClean() {
        assertThat(violations(
                new Save("s2", "p1", 10, 20, new Applied(5)),
                new Read("s1", "p1", 30, 40, new Found(4)))).isEmpty();
    }

    @Test
    void read_WhenTheSaveIsOfAnotherKey_IsClean() {
        assertThat(violations(
                new Save("s1", "p2", 10, 20, new Applied(5)),
                new Read("s1", "p1", 30, 40, new Found(4)))).isEmpty();
    }

    @Test
    void read_WhenInvokedAtTheSaveCompletion_IsClean() {
        assertThat(violations(
                new Save("s1", "p1", 10, 30, new Applied(5)),
                new Read("s1", "p1", 30, 40, new Found(4)))).isEmpty();
    }

    @Test
    void read_WhenInvokedOneTickAfterTheSaveCompletion_IsAViolation() {
        Read stale = new Read("s1", "p1", 30, 40, new Found(4));

        assertThat(violations(new Save("s1", "p1", 10, 29, new Applied(5)), stale))
                .containsExactly(new Violation("author-read-your-writes", "s1", "p1", stale));
    }

    @Test
    void read_WhenOlderThanTheHighestOfSeveralSaves_IsAViolation() {
        Read stale = new Read("s1", "p1", 50, 60, new Found(6));

        assertThat(violations(
                new Save("s1", "p1", 10, 20, new Applied(7)),
                new Save("s1", "p1", 30, 40, new Applied(5)),
                stale)).containsExactly(new Violation("author-read-your-writes", "s1", "p1", stale));
    }

    @Test
    void read_WhenItSeesTheSavedVersion_IsClean() {
        assertThat(violations(APPLIED_5, new Read("s1", "p1", 30, 40, new Found(5)))).isEmpty();
    }

    @Test
    void read_WhenItSeesANewerVersion_IsClean() {
        assertThat(violations(APPLIED_5, new Read("s1", "p1", 30, 40, new Found(6)))).isEmpty();
    }

    @Test
    void read_WhenTheOnlySaveWasRejected_IsClean() {
        assertThat(violations(
                new Save("s1", "p1", 10, 20, new Rejected(409)),
                new Read("s1", "p1", 30, 40, new NotFound()))).isEmpty();
    }

    @Test
    void read_WhenTheOnlySaveIsUnknown_IsClean() {
        assertThat(violations(
                new Save("s1", "p1", 10, 20, new Unknown()),
                new Read("s1", "p1", 30, 40, new NotFound()))).isEmpty();
    }

    @Test
    void read_WhenItFailed_IsClean() {
        assertThat(violations(APPLIED_5, new Read("s1", "p1", 30, 40, new ReadFailed(503)))).isEmpty();
    }

    @Test
    void notFound_WhenNoDeleteExists_IsAViolation() {
        Read missing = new Read("s1", "p1", 30, 40, new NotFound());

        assertThat(violations(APPLIED_5, missing))
                .containsExactly(new Violation("author-read-your-writes", "s1", "p1", missing));
    }

    @Test
    void notFound_WhenNoSavePrecedesIt_IsClean() {
        assertThat(violations(new Read("s1", "p1", 30, 40, new NotFound()))).isEmpty();
    }

    @Test
    void notFound_WhenAnotherSessionDeletedConcurrently_IsClean() {
        assertThat(violations(
                APPLIED_5,
                new Delete("s2", "p1", 35, 50, new Deleted()),
                new Read("s1", "p1", 30, 40, new NotFound()))).isEmpty();
    }

    @Test
    void notFound_WhenADeleteOutcomeIsUnknown_IsClean() {
        assertThat(violations(
                APPLIED_5,
                new Delete("s2", "p1", 25, 26, new Unknown()),
                new Read("s1", "p1", 30, 40, new NotFound()))).isEmpty();
    }

    @Test
    void notFound_WhenTheDeleteWasRejected_IsAViolation() {
        Read missing = new Read("s1", "p1", 30, 40, new NotFound());

        assertThat(violations(APPLIED_5, new Delete("s2", "p1", 25, 26, new Rejected(403)), missing))
                .containsExactly(new Violation("author-read-your-writes", "s1", "p1", missing));
    }

    @Test
    void notFound_WhenTheDeleteIsOfAnotherKey_IsAViolation() {
        Read missing = new Read("s1", "p1", 30, 40, new NotFound());

        assertThat(violations(APPLIED_5, new Delete("s2", "p2", 25, 26, new Deleted()), missing))
                .containsExactly(new Violation("author-read-your-writes", "s1", "p1", missing));
    }

    @Test
    void notFound_WhenTheDeleteStartsAtTheReadCompletion_IsAViolation() {
        Read missing = new Read("s1", "p1", 30, 40, new NotFound());

        assertThat(violations(APPLIED_5, new Delete("s2", "p1", 40, 50, new Deleted()), missing))
                .containsExactly(new Violation("author-read-your-writes", "s1", "p1", missing));
    }

    @Test
    void notFound_WhenTheDeleteStartsOneTickBeforeTheReadCompletion_IsClean() {
        assertThat(violations(
                APPLIED_5,
                new Delete("s2", "p1", 39, 50, new Deleted()),
                new Read("s1", "p1", 30, 40, new NotFound()))).isEmpty();
    }

    private static List<Violation> violations(Operation... operations) {
        Verdict verdict = new AuthorReadYourWrites().judge(History.of(List.of(operations)));
        assertThat(verdict.anomalies()).isEmpty();
        return verdict.violations();
    }
}
