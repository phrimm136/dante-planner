package org.danteplanner.consistency;

import java.util.List;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ExposureDuringDeleteTest {

    private static final Delete DELETED = new Delete("s1", "p1", 10, 20, new Deleted());

    @Test
    void found_WhenItCompletesAsTheDeleteBegins_IsAnAnomaly() {
        Read touching = new Read("s2", "p1", 5, 10, new Found(3));

        assertThat(anomalies(DELETED, touching))
                .containsExactly(new Anomaly("exposure-during-delete", "s2", "p1", touching));
    }

    @Test
    void found_WhenItCompletesOneTickBeforeTheDeleteBegins_IsClean() {
        assertThat(anomalies(DELETED, new Read("s2", "p1", 5, 9, new Found(3)))).isEmpty();
    }

    @Test
    void found_WhenInvokedAtTheDeleteCompletion_IsAnAnomaly() {
        Read touching = new Read("s2", "p1", 20, 25, new Found(3));

        assertThat(anomalies(DELETED, touching))
                .containsExactly(new Anomaly("exposure-during-delete", "s2", "p1", touching));
    }

    @Test
    void found_WhenInvokedOneTickAfterTheDeleteCompletion_IsClean() {
        assertThat(anomalies(DELETED, new Read("s2", "p1", 21, 25, new Found(3)))).isEmpty();
    }

    @Test
    void found_WhenItSpansTheWholeDelete_IsAnAnomaly() {
        Read spanning = new Read("s2", "p1", 5, 25, new Found(3));

        assertThat(anomalies(DELETED, spanning))
                .containsExactly(new Anomaly("exposure-during-delete", "s2", "p1", spanning));
    }

    @Test
    void found_WhenItOverlapsTwoDeletes_IsOneAnomaly() {
        Read overlapping = new Read("s2", "p1", 15, 18, new Found(3));

        assertThat(anomalies(DELETED, new Delete("s3", "p1", 12, 30, new Deleted()), overlapping))
                .containsExactly(new Anomaly("exposure-during-delete", "s2", "p1", overlapping));
    }

    @Test
    void read_WhenOfAnotherKeyOrNotFound_IsClean() {
        assertThat(anomalies(
                DELETED,
                new Read("s2", "p2", 15, 18, new Found(3)),
                new Read("s2", "p1", 15, 18, new NotFound()))).isEmpty();
    }

    @Test
    void found_WhenTheOverlappedDeleteWasRejectedOrUnknown_IsClean() {
        assertThat(anomalies(
                new Delete("s1", "p1", 10, 20, new Rejected(404)),
                new Delete("s1", "p1", 10, 20, new Unknown()),
                new Read("s2", "p1", 15, 18, new Found(3)))).isEmpty();
    }

    private static List<Anomaly> anomalies(Operation... operations) {
        Verdict verdict = new ExposureDuringDelete().judge(History.of(List.of(operations)));
        assertThat(verdict.violations()).isEmpty();
        return verdict.anomalies();
    }
}
