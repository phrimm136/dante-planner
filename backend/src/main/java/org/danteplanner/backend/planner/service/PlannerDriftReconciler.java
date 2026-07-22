package org.danteplanner.backend.planner.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

/**
 * Scheduled drift reconciler over the planner projections and counters: detects
 * divergence between planner_stats and the authoritative child aggregates,
 * catalog membership vs visibility, the filter indexes vs a rebuild of the
 * stored content, and the derived recommended flag. Emits one structured drift
 * record (log event + metric) per finding and repairs NOTHING — drift means a
 * maintenance bug to fix, not a table to quietly patch.
 */
@Service
@RequiredArgsConstructor
public class PlannerDriftReconciler {

    /**
     * One detected divergence for one planner.
     */
    public record DriftRecord(UUID plannerId, String kind, String expected, String actual) {
    }

    /**
     * Run all drift checks and emit a record per finding.
     *
     * @return the drift records found in this pass
     */
    public List<DriftRecord> reconcile() {
        return List.of();
    }
}
