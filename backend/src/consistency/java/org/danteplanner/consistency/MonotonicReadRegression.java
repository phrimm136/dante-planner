package org.danteplanner.consistency;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

final class MonotonicReadRegression implements Rule {

    static final String KIND = "monotonic-read-regression";

    private final AuthorReadYourWrites authorReadYourWrites = new AuthorReadYourWrites();

    @Override
    public Verdict judge(History history) {
        Set<Operation> violating = authorReadYourWrites.judge(history).violations().stream()
                .map(Violation::operation)
                .collect(Collectors.toSet());
        List<FoundRead> found = ByIdRead.all(history).stream()
                .<FoundRead>mapMulti((read, sink) -> Results.foundVersion(read.result())
                        .ifPresent(version -> sink.accept(new FoundRead(read, version))))
                .toList();
        List<Anomaly> anomalies = found.stream()
                .filter(later -> found.stream().anyMatch(earlier -> regresses(earlier, later)))
                .map(FoundRead::read)
                .filter(read -> !violating.contains(read.operation()))
                .map(read -> new Anomaly(KIND, read.session(), read.key(), read.operation()))
                .toList();
        return new Verdict(List.of(), anomalies);
    }

    private static boolean regresses(FoundRead earlier, FoundRead later) {
        return earlier.read().session().equals(later.read().session())
                && earlier.read().key().equals(later.read().key())
                && later.read().invokedAt() > earlier.read().completedAt()
                && later.version() < earlier.version();
    }

    private record FoundRead(ByIdRead read, long version) {
    }
}
