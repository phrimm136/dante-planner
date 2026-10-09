package org.danteplanner.consistency;

import java.util.List;

final class ReadAfterPublish implements Rule {

    static final String NAME = "read-after-publish";

    @Override
    public Verdict judge(History history) {
        List<Violation> violations = history.all(PublicRead.class).stream()
                .filter(read -> read.result() instanceof NotFound)
                .filter(read -> history.all(Publish.class).stream()
                        .filter(publish -> publish.key().equals(read.key()))
                        .filter(publish -> Results.changed(publish.result()))
                        .filter(publish -> read.invokedAt() > publish.completedAt())
                        .anyMatch(publish -> !withdrawn(publish, read, history)))
                .map(read -> new Violation(NAME, read.session(), read.key(), read))
                .toList();
        return new Verdict(violations, List.of());
    }

    private static boolean withdrawn(Publish publish, PublicRead read, History history) {
        boolean unpublished = history.all(Unpublish.class).stream()
                .filter(unpublish -> unpublish.key().equals(read.key()))
                .filter(unpublish -> Results.mayHaveChanged(unpublish.result()))
                .anyMatch(unpublish -> unpublish.completedAt() > publish.invokedAt()
                        && unpublish.invokedAt() < read.completedAt());
        boolean deleted = history.all(Delete.class).stream()
                .filter(delete -> delete.key().equals(read.key()))
                .filter(delete -> Results.mayHaveDeleted(delete.result()))
                .anyMatch(delete -> delete.completedAt() > publish.invokedAt()
                        && delete.invokedAt() < read.completedAt());
        return unpublished || deleted;
    }
}
