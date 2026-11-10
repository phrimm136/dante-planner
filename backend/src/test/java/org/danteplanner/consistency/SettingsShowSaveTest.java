package org.danteplanner.consistency;

import java.util.List;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SettingsShowSaveTest {

    private static final SettingsSave SAVED_TRUE = new SettingsSave("s1", true, 10, 20, new Changed());

    @Test
    void read_WhenItStartsAfterTheSaveResponseAndShowsTheOldValue_IsAViolation() {
        SettingsRead read = read(21, 30, false);

        assertThat(violations(SAVED_TRUE, read))
                .containsExactly(new Violation("settings-after-save", "s2", "settings", read));
    }

    @Test
    void read_WhenItShowsTheSavedValue_IsClean() {
        assertThat(violations(SAVED_TRUE, read(21, 30, true))).isEmpty();
    }

    @Test
    void read_WhenNoSaveCompletedBeforeIt_IsNotJudged() {
        assertThat(violations(read(5, 9, true), SAVED_TRUE)).isEmpty();
    }

    @Test
    void read_WhenALaterSaveWasInvokedBeforeItCompleted_IsExcused() {
        assertThat(violations(SAVED_TRUE, new SettingsSave("s1", false, 25, 40, new Unknown()),
                read(21, 30, false))).isEmpty();
    }

    @Test
    void read_WhenTheLaterSaveInItsWindowWasRejected_IsStillAViolation() {
        SettingsRead read = read(21, 30, false);

        assertThat(violations(SAVED_TRUE, new SettingsSave("s1", false, 25, 28, new Rejected(503)), read))
                .containsExactly(new Violation("settings-after-save", "s2", "settings", read));
    }

    @Test
    void read_WhenItFailed_IsClean() {
        assertThat(violations(SAVED_TRUE, new SettingsRead("s2", 21, 30, new ReadFailed(503)))).isEmpty();
    }

    @Test
    void read_WhenItStartsAtTheSaveCompletion_IsNotJudged() {
        assertThat(violations(SAVED_TRUE, read(20, 30, false))).isEmpty();
    }

    @Test
    void read_WhenTwoSavesCompletedBeforeIt_IsJudgedAgainstTheLaterOne() {
        SettingsSave savedFalse = new SettingsSave("s1", false, 30, 40, new Changed());
        SettingsRead stale = read(41, 50, true);

        assertThat(violations(SAVED_TRUE, savedFalse, stale, read(41, 50, false)))
                .containsExactly(new Violation("settings-after-save", "s2", "settings", stale));
    }

    @Test
    void read_WhenALaterChangedSaveWasInvokedBeforeItCompleted_IsExcused() {
        assertThat(violations(SAVED_TRUE, new SettingsSave("s1", false, 25, 40, new Changed()),
                read(21, 30, false))).isEmpty();
    }

    private static SettingsRead read(long invokedAt, long completedAt, boolean value) {
        return new SettingsRead("s2", invokedAt, completedAt, new Seen(value));
    }

    private static List<Violation> violations(Operation... operations) {
        Verdict verdict = new SettingsShowSave().judge(History.of(List.of(operations)));
        assertThat(verdict.anomalies()).isEmpty();
        return verdict.violations();
    }
}
