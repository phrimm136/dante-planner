package org.danteplanner.consistency;

import java.util.List;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MonotonicReadRegressionTest {

    @Test
    void lowerRead_WhenInvokedAtTheEarlierReadsCompletion_IsClean() {
        assertThat(anomalies(
                new Read("s2", "p1", 10, 30, new Found(5)),
                new Read("s2", "p1", 30, 40, new Found(4)))).isEmpty();
    }

    @Test
    void lowerRead_WhenInvokedOneTickAfterTheEarlierReadsCompletion_IsAnAnomaly() {
        Read regressed = new Read("s2", "p1", 31, 40, new Found(4));

        assertThat(anomalies(new Read("s2", "p1", 10, 30, new Found(5)), regressed))
                .containsExactly(new Anomaly("monotonic-read-regression", "s2", "p1", regressed));
    }

    @Test
    void laterRead_WhenItSeesTheSameOrANewerVersion_IsClean() {
        assertThat(anomalies(
                new Read("s2", "p1", 10, 20, new Found(5)),
                new Read("s2", "p1", 30, 40, new Found(5)),
                new Read("s2", "p1", 50, 60, new Found(6)))).isEmpty();
    }

    @Test
    void lowerRead_WhenByAnotherSession_IsClean() {
        assertThat(anomalies(
                new Read("s2", "p1", 10, 20, new Found(5)),
                new Read("s3", "p1", 30, 40, new Found(4)))).isEmpty();
    }

    @Test
    void lowerRead_WhenOfAnotherKey_IsClean() {
        assertThat(anomalies(
                new Read("s2", "p1", 10, 20, new Found(5)),
                new Read("s2", "p2", 30, 40, new Found(4)))).isEmpty();
    }

    @Test
    void lowerRead_WhenRecordedBeforeTheEarlierRead_IsStillTheAnomaly() {
        Read regressed = new Read("s2", "p1", 30, 40, new Found(4));

        assertThat(anomalies(regressed, new Read("s2", "p1", 10, 20, new Found(5))))
                .containsExactly(new Anomaly("monotonic-read-regression", "s2", "p1", regressed));
    }

    @Test
    void lowerRead_WhenBelowTwoEarlierReads_IsOneAnomaly() {
        Read regressed = new Read("s2", "p1", 50, 60, new Found(4));

        assertThat(anomalies(
                new Read("s2", "p1", 10, 20, new Found(5)),
                new Read("s2", "p1", 30, 40, new Found(6)),
                regressed)).containsExactly(new Anomaly("monotonic-read-regression", "s2", "p1", regressed));
    }

    @Test
    void reads_WhenNotFoundOrFailed_AreNotCompared() {
        assertThat(anomalies(
                new Read("s2", "p1", 10, 20, new Found(5)),
                new Read("s2", "p1", 30, 40, new NotFound()),
                new Read("s2", "p1", 50, 60, new ReadFailed(500)))).isEmpty();
    }

    @Test
    void authorRead_WhenAlreadyAReadYourWritesViolation_IsNotAnAnomaly() {
        assertThat(anomalies(
                new Save("s1", "p1", 1, 5, new Applied(5)),
                new Read("s1", "p1", 10, 20, new Found(5)),
                new Read("s1", "p1", 30, 40, new Found(4)))).isEmpty();
    }

    @Test
    void authorRead_WhenNotAReadYourWritesViolation_IsAnAnomaly() {
        Read regressed = new Read("s1", "p1", 30, 40, new Found(4));

        assertThat(anomalies(
                new Save("s1", "p1", 1, 5, new Applied(4)),
                new Read("s1", "p1", 10, 20, new Found(5)),
                regressed)).containsExactly(new Anomaly("monotonic-read-regression", "s1", "p1", regressed));
    }

    private static List<Anomaly> anomalies(Operation... operations) {
        Verdict verdict = new MonotonicReadRegression().judge(History.of(List.of(operations)));
        assertThat(verdict.violations()).isEmpty();
        return verdict.anomalies();
    }
}
