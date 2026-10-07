package org.danteplanner.backend.support;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.danteplanner.backend.moderation.api.PlannerReportPurge;

public class FakeReportPurge implements PlannerReportPurge {

    public record DeleteReportsFor(List<UUID> plannerIds) {
    }

    private final List<Object> journal;

    public FakeReportPurge() {
        this(new ArrayList<>());
    }

    public FakeReportPurge(List<Object> journal) {
        this.journal = journal;
    }

    public List<DeleteReportsFor> calls() {
        return journal.stream()
                .filter(DeleteReportsFor.class::isInstance)
                .map(DeleteReportsFor.class::cast)
                .toList();
    }

    @Override
    public void deleteReportsFor(List<UUID> plannerIds) {
        journal.add(new DeleteReportsFor(List.copyOf(plannerIds)));
    }
}
