package org.danteplanner.backend.planner.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Runtime-mutable feature flag gating whether planner counter fields are served from the
 * new {@code planner_stats} store instead of the legacy {@code planners} columns.
 *
 * <p>Seeded at startup from {@code planner.stats.reads-enabled} (env
 * {@code PLANNER_STATS_READS_ENABLED}, default {@code false}) and backed by an
 * {@link AtomicBoolean} so the cutover flip is visible across the read threads.</p>
 */
@Component
public class StatsReadsFlag {

    private final AtomicBoolean enabled;

    public StatsReadsFlag(@Value("${planner.stats.reads-enabled:false}") boolean initial) {
        this.enabled = new AtomicBoolean(initial);
    }

    public boolean isEnabled() {
        return enabled.get();
    }

    public void setEnabled(boolean value) {
        this.enabled.set(value);
    }
}
