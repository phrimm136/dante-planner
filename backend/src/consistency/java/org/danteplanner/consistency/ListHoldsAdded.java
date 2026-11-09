package org.danteplanner.consistency;

import java.util.List;

final class ListHoldsAdded implements Rule {

    static final String NAME = "owner-list-after-create";

    @Override
    public Verdict judge(History history) {
        List<Violation> violations = history.all(ListRead.class).stream()
                .filter(list -> list.listing() == ListRead.Listing.OWNER)
                .filter(ListRead::whole)
                .<Violation>mapMulti((list, sink) -> history.all(Save.class).stream()
                        .filter(save -> save.completedAt() < list.invokedAt())
                        .filter(save -> Results.appliedVersion(save.result()).isPresent())
                        .map(Save::key)
                        .filter(key -> !list.keys().contains(key))
                        .filter(key -> !deleteMayPrecede(key, list, history))
                        .distinct()
                        .forEach(key -> sink.accept(new Violation(NAME, list.session(), key, list))))
                .toList();
        return new Verdict(violations, List.of());
    }

    private static boolean deleteMayPrecede(String key, ListRead list, History history) {
        return history.all(Delete.class).stream()
                .filter(delete -> delete.key().equals(key))
                .filter(delete -> delete.invokedAt() < list.completedAt())
                .anyMatch(delete -> Results.mayHaveDeleted(delete.result()));
    }
}
