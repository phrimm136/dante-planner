package org.danteplanner.consistency;

import java.util.List;

final class ReadAfterReactivation implements Rule {

    static final String NAME = "read-after-reactivation";

    @Override
    public Verdict judge(History history) {
        List<Violation> violations = PublishedVisibilityRead.all(history).stream()
                .filter(read -> read.seen() == PublishedVisibilityRead.Seen.MISSING)
                .filter(read -> shown(read.key(), read.invokedAt(), read.completedAt(), history))
                .map(read -> new Violation(NAME, read.session(), read.key(), read.operation()))
                .toList();
        return new Verdict(violations, List.of());
    }

    private static boolean shown(String key, long invokedAt, long completedAt, History history) {
        return history.all(Reactivate.class).stream()
                .filter(reactivate -> Results.changed(reactivate.result()))
                .filter(reactivate -> reactivate.completedAt() < invokedAt)
                .filter(reactivate -> !deactivatedAgain(reactivate, completedAt, history))
                .filter(reactivate -> history.all(Deactivate.class).stream()
                        .filter(deactivate -> Results.changed(deactivate.result()))
                        .anyMatch(deactivate -> deactivate.completedAt() <= reactivate.invokedAt()))
                .anyMatch(reactivate -> publishedThrough(key, reactivate, completedAt, history));
    }

    private static boolean deactivatedAgain(Reactivate reactivate, long readCompletedAt, History history) {
        return history.all(Deactivate.class).stream()
                .filter(deactivate -> Results.mayHaveChanged(deactivate.result()))
                .anyMatch(deactivate -> deactivate.completedAt() > reactivate.invokedAt()
                        && deactivate.invokedAt() < readCompletedAt);
    }

    private static boolean publishedThrough(String key, Reactivate reactivate, long readCompletedAt,
            History history) {
        return history.all(Publish.class).stream()
                .filter(publish -> publish.key().equals(key))
                .filter(publish -> Results.changed(publish.result()))
                .filter(publish -> publish.completedAt() < reactivate.invokedAt())
                .anyMatch(publish -> !withdrawnBetween(publish, readCompletedAt, history));
    }

    private static boolean withdrawnBetween(Publish publish, long readCompletedAt, History history) {
        boolean unpublished = history.all(Unpublish.class).stream()
                .filter(unpublish -> unpublish.key().equals(publish.key()))
                .filter(unpublish -> Results.mayHaveChanged(unpublish.result()))
                .anyMatch(unpublish -> unpublish.completedAt() > publish.invokedAt()
                        && unpublish.invokedAt() < readCompletedAt);
        boolean deleted = history.all(Delete.class).stream()
                .filter(delete -> delete.key().equals(publish.key()))
                .filter(delete -> Results.mayHaveDeleted(delete.result()))
                .anyMatch(delete -> delete.completedAt() > publish.invokedAt()
                        && delete.invokedAt() < readCompletedAt);
        return unpublished || deleted;
    }
}
