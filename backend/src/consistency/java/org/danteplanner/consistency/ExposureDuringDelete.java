package org.danteplanner.consistency;

import java.util.List;

final class ExposureDuringDelete implements Rule {

    static final String KIND = "exposure-during-delete";

    @Override
    public Verdict judge(History history) {
        List<Anomaly> anomalies = history.all(Read.class).stream()
                .filter(read -> Results.foundVersion(read.result()).isPresent())
                .filter(read -> ReadAfterDelete.completedDeletes(read.key(), history).stream()
                        .anyMatch(delete -> read.invokedAt() <= delete.completedAt()
                                && read.completedAt() >= delete.invokedAt()))
                .map(read -> new Anomaly(KIND, read.session(), read.key(), read))
                .toList();
        return new Verdict(List.of(), anomalies);
    }
}
