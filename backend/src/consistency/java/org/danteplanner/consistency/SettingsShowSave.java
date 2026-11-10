package org.danteplanner.consistency;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

final class SettingsShowSave implements Rule {

    static final String NAME = "settings-after-save";
    static final String KEY = "settings";

    @Override
    public Verdict judge(History history) {
        List<Violation> violations = history.all(SettingsRead.class).stream()
                .filter(read -> read.result() instanceof Seen seen
                        && lastSaveBefore(read, history)
                                .filter(save -> !overtaken(save, read, history))
                                .filter(save -> save.value() != seen.value())
                                .isPresent())
                .map(read -> new Violation(NAME, read.session(), KEY, read))
                .toList();
        return new Verdict(violations, List.of());
    }

    private static Optional<SettingsSave> lastSaveBefore(SettingsRead read, History history) {
        return history.all(SettingsSave.class).stream()
                .filter(save -> Results.changed(save.result()))
                .filter(save -> save.completedAt() < read.invokedAt())
                .max(Comparator.comparingLong(SettingsSave::completedAt));
    }

    private static boolean overtaken(SettingsSave save, SettingsRead read, History history) {
        return history.all(SettingsSave.class).stream()
                .filter(later -> Results.mayHaveChanged(later.result()))
                .anyMatch(later -> later.invokedAt() > save.completedAt() && later.invokedAt() < read.completedAt());
    }
}
