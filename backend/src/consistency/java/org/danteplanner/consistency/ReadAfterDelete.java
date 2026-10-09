package org.danteplanner.consistency;

import java.util.List;

final class ReadAfterDelete implements Rule {

    static final String NAME = "read-after-delete";

    @Override
    public Verdict judge(History history) {
        List<Violation> violations = history.all(Read.class).stream()
                .filter(read -> Results.foundVersion(read.result()).isPresent())
                .filter(read -> !saveOutlivesADelete(read.key(), history))
                .filter(read -> completedDeletes(read.key(), history).stream()
                        .anyMatch(delete -> read.invokedAt() > delete.completedAt()))
                .map(read -> new Violation(NAME, read.session(), read.key(), read))
                .toList();
        return new Verdict(violations, List.of());
    }

    private static boolean saveOutlivesADelete(String key, History history) {
        return completedDeletes(key, history).stream()
                .anyMatch(delete -> history.all(Save.class).stream()
                        .filter(save -> save.key().equals(key))
                        .filter(save -> mayHaveApplied(save.result()))
                        .anyMatch(save -> save.completedAt() > delete.invokedAt()));
    }

    private static boolean mayHaveApplied(SaveResult result) {
        return switch (result) {
            case Applied applied -> true;
            case Rejected rejected -> false;
            case Unknown unknown -> true;
        };
    }

    static List<Delete> completedDeletes(String key, History history) {
        return history.all(Delete.class).stream()
                .filter(delete -> delete.key().equals(key))
                .filter(delete -> Results.deleted(delete.result()))
                .toList();
    }
}
