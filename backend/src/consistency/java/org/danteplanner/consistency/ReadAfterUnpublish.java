package org.danteplanner.consistency;

import java.util.List;

final class ReadAfterUnpublish implements Rule {

    static final String NAME = "read-after-unpublish";

    @Override
    public Verdict judge(History history) {
        List<Violation> violations = PublishedVisibilityRead.all(history).stream()
                .filter(read -> read.seen() == PublishedVisibilityRead.Seen.SHOWN)
                .filter(read -> history.all(Unpublish.class).stream()
                        .filter(unpublish -> unpublish.key().equals(read.key()))
                        .filter(unpublish -> Results.changed(unpublish.result()))
                        .filter(unpublish -> read.invokedAt() > unpublish.completedAt())
                        .anyMatch(unpublish -> !republished(unpublish, read, history)))
                .map(read -> new Violation(NAME, read.session(), read.key(), read.operation()))
                .toList();
        return new Verdict(violations, List.of());
    }

    private static boolean republished(Unpublish unpublish, PublishedVisibilityRead read, History history) {
        return history.all(Publish.class).stream()
                .filter(publish -> publish.key().equals(read.key()))
                .filter(publish -> Results.mayHaveChanged(publish.result()))
                .anyMatch(publish -> publish.completedAt() > unpublish.invokedAt()
                        && publish.invokedAt() < read.completedAt());
    }
}
