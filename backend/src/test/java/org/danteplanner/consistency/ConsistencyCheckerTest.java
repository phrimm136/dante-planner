package org.danteplanner.consistency;

import java.util.List;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ConsistencyCheckerTest {

    @Test
    void authorRead_WhenOlderThanACompletedSave_IsOneViolation() {
        Read stale = new Read("s1", "p1", 30, 40, new Found(4));

        Verdict verdict = ConsistencyChecker.check(History.of(List.of(
                new Save("s1", "p1", 10, 20, new Applied(5)),
                stale)));

        assertThat(verdict.violations())
                .containsExactly(new Violation("author-read-your-writes", "s1", "p1", stale));
        assertThat(verdict.anomalies()).isEmpty();
    }

    @Test
    void authorRead_WhenANoOpSaveAcknowledgedTheCurrentVersion_IsClean() {
        Verdict verdict = ConsistencyChecker.check(History.of(List.of(
                new Save("s1", "p1", 10, 20, new Applied(5)),
                new Save("s1", "p1", 30, 40, new Applied(5)),
                new Read("s1", "p1", 50, 60, new Found(5)))));

        assertThat(verdict.violations()).isEmpty();
        assertThat(verdict.anomalies()).isEmpty();
    }

    @Test
    void reads_WhenASaveOutcomeIsUnknown_AreClean() {
        Verdict verdict = ConsistencyChecker.check(History.of(List.of(
                new Save("s1", "p1", 10, 20, new Applied(5)),
                new Save("s1", "p1", 30, 40, new Unknown()),
                new Read("s1", "p1", 50, 60, new Found(5)),
                new Read("s2", "p1", 70, 80, new Found(6)))));

        assertThat(verdict.violations()).isEmpty();
        assertThat(verdict.anomalies()).isEmpty();
    }

    @Test
    void authorRead_WhenALaterSaveWasRejected_IsClean() {
        Verdict verdict = ConsistencyChecker.check(History.of(List.of(
                new Save("s1", "p1", 10, 20, new Applied(5)),
                new Save("s1", "p1", 30, 40, new Rejected(409)),
                new Read("s1", "p1", 50, 60, new Found(5)))));

        assertThat(verdict.violations()).isEmpty();
    }

    @Test
    void read_WhenItStartsAfterTheDeleteResponseAndReturnsTheRow_IsOneViolation() {
        Read resurrected = new Read("s2", "p1", 25, 30, new Found(3));

        Verdict verdict = ConsistencyChecker.check(History.of(List.of(
                new Delete("s1", "p1", 10, 20, new Deleted()),
                resurrected)));

        assertThat(verdict.violations())
                .containsExactly(new Violation("read-after-delete", "s2", "p1", resurrected));
    }

    @Test
    void read_WhenItOverlapsTheDeleteAndReturnsTheRow_IsOneAnomaly() {
        Read overlapping = new Read("s2", "p1", 15, 18, new Found(3));

        Verdict verdict = ConsistencyChecker.check(History.of(List.of(
                new Delete("s1", "p1", 10, 20, new Deleted()),
                overlapping)));

        assertThat(verdict.violations()).isEmpty();
        assertThat(verdict.anomalies())
                .containsExactly(new Anomaly("exposure-during-delete", "s2", "p1", overlapping));
    }

    @Test
    void nonAuthorRead_WhenOlderThanItsEarlierRead_IsOneAnomaly() {
        Read regressed = new Read("s2", "p1", 30, 40, new Found(4));

        Verdict verdict = ConsistencyChecker.check(History.of(List.of(
                new Read("s2", "p1", 10, 20, new Found(5)),
                regressed)));

        assertThat(verdict.violations()).isEmpty();
        assertThat(verdict.anomalies())
                .containsExactly(new Anomaly("monotonic-read-regression", "s2", "p1", regressed));
    }

    @Test
    void listRead_WhenItOmitsASavedPlanner_IsNotJudged() {
        Verdict verdict = ConsistencyChecker.check(History.of(List.of(
                new Save("s1", "p1", 10, 20, new Applied(1)),
                new ListRead("s1", 30, 40, List.of()))));

        assertThat(verdict.violations()).isEmpty();
        assertThat(verdict.anomalies()).isEmpty();
    }

    @Test
    void check_WhenSeveralRulesFire_ConcatenatesTheirFindingsInRuleOrder() {
        Read stale = new Read("s1", "p1", 30, 40, new Found(4));
        Read resurrected = new Read("s2", "p2", 25, 30, new Found(3));
        Read overlapping = new Read("s3", "p2", 15, 18, new Found(3));
        Read regressed = new Read("s4", "p3", 30, 40, new Found(4));

        Verdict verdict = ConsistencyChecker.check(History.of(List.of(
                new Save("s1", "p1", 10, 20, new Applied(5)),
                stale,
                new Delete("s1", "p2", 10, 20, new Deleted()),
                resurrected,
                overlapping,
                new Read("s4", "p3", 10, 20, new Found(5)),
                regressed)));

        assertThat(verdict.violations()).containsExactly(
                new Violation("author-read-your-writes", "s1", "p1", stale),
                new Violation("read-after-delete", "s2", "p2", resurrected));
        assertThat(verdict.anomalies()).containsExactly(
                new Anomaly("exposure-during-delete", "s3", "p2", overlapping),
                new Anomaly("monotonic-read-regression", "s4", "p3", regressed));
    }

    @Test
    void check_WhenTheHistoryIsEmpty_IsClean() {
        Verdict verdict = ConsistencyChecker.check(History.of(List.of()));

        assertThat(verdict.violations()).isEmpty();
        assertThat(verdict.anomalies()).isEmpty();
    }
}
