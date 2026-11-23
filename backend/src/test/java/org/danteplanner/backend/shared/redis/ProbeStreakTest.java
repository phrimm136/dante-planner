package org.danteplanner.backend.shared.redis;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ProbeStreakTest {

    private static final int THRESHOLD = 3;
    private static final int MANY = 10;
    private static final boolean ANSWERED = true;
    private static final boolean SILENT = false;

    private ProbeStreak streak = new ProbeStreak(THRESHOLD);

    private List<ProbeStreak.Transition> after(boolean answered, int times) {
        List<ProbeStreak.Transition> transitions = new ArrayList<>();
        for (int i = 0; i < times; i++) {
            transitions.add(after(answered));
        }
        return transitions;
    }

    private ProbeStreak.Transition after(boolean answered) {
        ProbeStreak.Step step = streak.after(answered);
        streak = step.next();
        return step.transition();
    }

    private ProbeStreak.Transition atStartup(boolean answered) {
        ProbeStreak.Step step = streak.atStartup(answered);
        streak = step.next();
        return step.transition();
    }

    @Test
    void after_WhenFailuresReachTheThreshold_OpensExactlyAtTheThresholdAndNotBefore() {
        assertThat(after(SILENT, THRESHOLD)).containsExactly(
                ProbeStreak.Transition.NONE, ProbeStreak.Transition.NONE, ProbeStreak.Transition.OPEN);
    }

    @Test
    void after_WhenASuccessFallsBetweenFailures_RestartsTheCount() {
        after(SILENT, THRESHOLD - 1);
        after(ANSWERED);

        assertThat(after(SILENT, THRESHOLD - 1)).containsOnly(ProbeStreak.Transition.NONE);
        assertThat(after(SILENT)).isEqualTo(ProbeStreak.Transition.OPEN);
    }

    @Test
    void after_WhenOpenAndASuccessArrives_Closes() {
        after(SILENT, THRESHOLD);

        assertThat(after(ANSWERED)).isEqualTo(ProbeStreak.Transition.CLOSE);
    }

    @Test
    void after_WhenOpenAndFailuresContinue_ReturnsNoFurtherTransition() {
        after(SILENT, THRESHOLD);

        assertThat(after(SILENT, MANY)).containsOnly(ProbeStreak.Transition.NONE);
    }

    @Test
    void after_WhenClosedAndSuccessesContinue_ReturnsNoTransition() {
        assertThat(after(ANSWERED, MANY)).containsOnly(ProbeStreak.Transition.NONE);
    }

    @Test
    void after_WhenClosedAgainAfterAnOutage_NeedsTheFullThresholdToReopen() {
        after(SILENT, THRESHOLD);
        after(ANSWERED);

        assertThat(after(SILENT, THRESHOLD)).containsExactly(
                ProbeStreak.Transition.NONE, ProbeStreak.Transition.NONE, ProbeStreak.Transition.OPEN);
    }

    @Test
    void atStartup_WhenTheRoleDoesNotAnswer_OpensAtOnce() {
        assertThat(atStartup(SILENT)).isEqualTo(ProbeStreak.Transition.OPEN);
    }

    @Test
    void atStartup_WhenTheRoleAnswers_StaysClosed() {
        assertThat(atStartup(ANSWERED)).isEqualTo(ProbeStreak.Transition.NONE);
        assertThat(after(ANSWERED)).isEqualTo(ProbeStreak.Transition.NONE);
    }

    @Test
    void after_WhenOpenedAtStartupAndFailuresContinue_ReturnsNoFurtherTransition() {
        atStartup(SILENT);

        assertThat(after(SILENT, MANY)).containsOnly(ProbeStreak.Transition.NONE);
        assertThat(after(ANSWERED)).isEqualTo(ProbeStreak.Transition.CLOSE);
    }
}
