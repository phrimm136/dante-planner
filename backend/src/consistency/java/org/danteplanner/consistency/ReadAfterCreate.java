package org.danteplanner.consistency;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

final class ReadAfterCreate implements Rule {

    static final String NAME = "read-after-create";

    private final AuthorReadYourWrites authorReadYourWrites = new AuthorReadYourWrites();

    @Override
    public Verdict judge(History history) {
        Set<Operation> reported = authorReadYourWrites.judge(history).violations().stream()
                .map(Violation::operation)
                .collect(Collectors.toSet());
        List<Violation> violations = history.all(Read.class).stream()
                .filter(read -> read.result() instanceof NotFound)
                .filter(read -> !reported.contains(read))
                .filter(read -> history.all(Save.class).stream()
                        .filter(save -> save.key().equals(read.key()))
                        .filter(save -> save.completedAt() < read.invokedAt())
                        .anyMatch(save -> Results.appliedVersion(save.result()).isPresent()))
                .filter(read -> !AuthorReadYourWrites.deleteMayPrecede(read, history))
                .map(read -> new Violation(NAME, read.session(), read.key(), read))
                .toList();
        return new Verdict(violations, List.of());
    }
}
