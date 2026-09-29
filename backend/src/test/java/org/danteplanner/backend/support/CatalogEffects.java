package org.danteplanner.backend.support;

import org.danteplanner.backend.planner.service.PlannerCatalogSyncService;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.UUID;
import java.util.function.IntSupplier;

import static org.assertj.core.api.Assertions.assertThat;

public final class CatalogEffects {

    private static final int MAX_PASSES = 20;

    private static final String PENDING = "SELECT COUNT(*) FROM domain_events "
            + "WHERE event_type = 'PLANNER_CATALOG_CHANGED' AND dispatched_at IS NULL AND attempts < 10";

    private CatalogEffects() {
    }

    public static void drain(PlannerCatalogSyncService syncService, JdbcTemplate jdbc, UUID plannerId) {
        drainUntilNone(syncService, () -> jdbc.queryForObject(PENDING + " AND aggregate_id = UUID_TO_BIN(?)",
                Integer.class, plannerId.toString()), plannerId.toString());
    }

    public static void drainAll(PlannerCatalogSyncService syncService, JdbcTemplate jdbc) {
        drainUntilNone(syncService, () -> jdbc.queryForObject(PENDING, Integer.class), "any planner");
    }

    private static void drainUntilNone(PlannerCatalogSyncService syncService, IntSupplier pending, String subject) {
        for (int pass = 0; pass < MAX_PASSES && pending.getAsInt() > 0; pass++) {
            syncService.drainPending();
        }
        assertThat(pending.getAsInt())
                .as("PLANNER_CATALOG_CHANGED rows for %s still pending after %d passes", subject, MAX_PASSES)
                .isZero();
    }
}
