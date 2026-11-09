package org.danteplanner.consistency;

import java.util.List;

final class ListOmitsRemoved implements Rule {

    static final String OWNER_NAME = "owner-list-after-delete";
    static final String CATALOG_NAME = "catalog-after-withdrawal";

    private final ListRead.Listing listing;

    ListOmitsRemoved(ListRead.Listing listing) {
        this.listing = listing;
    }

    @Override
    public Verdict judge(History history) {
        List<Violation> violations = history.all(ListRead.class).stream()
                .filter(list -> list.listing() == listing)
                .<Violation>mapMulti((list, sink) -> list.keys().stream()
                        .filter(key -> removedBefore(key, list, history))
                        .forEach(key -> sink.accept(new Violation(name(), list.session(), key, list))))
                .toList();
        return new Verdict(violations, List.of());
    }

    private String name() {
        return switch (listing) {
            case OWNER -> OWNER_NAME;
            case CATALOG -> CATALOG_NAME;
        };
    }

    private boolean removedBefore(String key, ListRead list, History history) {
        boolean deleted = ReadAfterDelete.completedDeletes(key, history).stream()
                .anyMatch(delete -> delete.completedAt() < list.invokedAt())
                && !ReadAfterDelete.saveOutlivesADelete(key, history);
        return switch (listing) {
            case OWNER -> deleted;
            case CATALOG -> deleted || unpublishedBefore(key, list, history);
        };
    }

    private static boolean unpublishedBefore(String key, ListRead list, History history) {
        return history.all(Unpublish.class).stream()
                .filter(unpublish -> unpublish.key().equals(key))
                .filter(unpublish -> Results.changed(unpublish.result()))
                .filter(unpublish -> unpublish.completedAt() < list.invokedAt())
                .anyMatch(unpublish -> history.all(Publish.class).stream()
                        .filter(publish -> publish.key().equals(key))
                        .filter(publish -> Results.mayHaveChanged(publish.result()))
                        .noneMatch(publish -> publish.completedAt() > unpublish.invokedAt()
                                && publish.invokedAt() < list.completedAt()));
    }
}
