package org.danteplanner.consistency;

import java.util.List;

final class ReadAfterUnpublish implements Rule {

    static final String NAME = "read-after-unpublish";

    @Override
    public Verdict judge(History history) {
        List<Violation> violations = history.all(PublicRead.class).stream()
                .filter(read -> Results.foundVersion(read.result()).isPresent())
                .filter(read -> history.all(Unpublish.class).stream()
                        .filter(unpublish -> unpublish.key().equals(read.key()))
                        .filter(unpublish -> Results.changed(unpublish.result()))
                        .filter(unpublish -> read.invokedAt() > unpublish.completedAt())
                        .anyMatch(unpublish -> !republished(unpublish, read, history)))
                .map(read -> new Violation(NAME, read.session(), read.key(), read))
                .toList();
        return new Verdict(violations, List.of());
    }

    private static boolean republished(Unpublish unpublish, PublicRead read, History history) {
        return history.all(Publish.class).stream()
                .filter(publish -> publish.key().equals(read.key()))
                .filter(publish -> Results.mayHaveChanged(publish.result()))
                .anyMatch(publish -> publish.completedAt() > unpublish.invokedAt()
                        && publish.invokedAt() < read.completedAt());
    }
}
