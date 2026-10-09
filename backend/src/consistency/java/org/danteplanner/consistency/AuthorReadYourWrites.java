package org.danteplanner.consistency;

import java.util.List;
import java.util.OptionalLong;

final class AuthorReadYourWrites implements Rule {

    static final String NAME = "author-read-your-writes";

    @Override
    public Verdict judge(History history) {
        List<Violation> violations = history.all(Read.class).stream()
                .filter(read -> violates(read, history))
                .map(read -> new Violation(NAME, read.session(), read.key(), read))
                .toList();
        return new Verdict(violations, List.of());
    }

    private static boolean violates(Read read, History history) {
        OptionalLong written = history.all(Save.class).stream()
                .filter(save -> save.session().equals(read.session()))
                .filter(save -> save.key().equals(read.key()))
                .filter(save -> save.completedAt() < read.invokedAt())
                .flatMapToLong(save -> Results.appliedVersion(save.result()).stream())
                .max();
        if (written.isEmpty()) {
            return false;
        }
        return switch (read.result()) {
            case Found found -> found.syncVersion() < written.getAsLong();
            case NotFound notFound -> !deleteMayPrecede(read, history);
            case ReadFailed failed -> false;
        };
    }

    private static boolean deleteMayPrecede(Read read, History history) {
        return history.all(Delete.class).stream()
                .filter(delete -> delete.key().equals(read.key()))
                .filter(delete -> delete.invokedAt() < read.completedAt())
                .anyMatch(delete -> Results.mayHaveDeleted(delete.result()));
    }
}
