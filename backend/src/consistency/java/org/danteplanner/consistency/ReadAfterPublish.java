package org.danteplanner.consistency;

import java.util.List;

final class ReadAfterPublish implements Rule {

    static final String NAME = "read-after-publish";

    @Override
    public Verdict judge(History history) {
        List<Violation> violations = PublishedVisibilityRead.all(history).stream()
                .filter(read -> read.seen() == PublishedVisibilityRead.Seen.MISSING)
                .filter(read -> history.all(Publish.class).stream()
                        .filter(publish -> publish.key().equals(read.key()))
                        .filter(publish -> Results.changed(publish.result()))
                        .filter(publish -> read.invokedAt() > publish.completedAt())
                        .anyMatch(publish -> !withdrawn(publish, read.completedAt(), history)))
                .map(read -> new Violation(NAME, read.session(), read.key(), read.operation()))
                .toList();
        return new Verdict(violations, List.of());
    }

    private static boolean withdrawn(Publish publish, long readCompletedAt, History history) {
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
        boolean deactivated = history.all(Deactivate.class).stream()
                .filter(deactivate -> Results.mayHaveChanged(deactivate.result()))
                .anyMatch(deactivate -> deactivate.completedAt() > publish.invokedAt()
                        && deactivate.invokedAt() < readCompletedAt);
        return unpublished || deleted || deactivated;
    }
}
