package org.danteplanner.backend.support;

import org.springframework.jdbc.core.JdbcOperations;

import java.util.UUID;

public final class PlannerContentRows {

    private PlannerContentRows() {
    }

    public static void markDeleted(JdbcOperations jdbc, UUID plannerId) {
        jdbc.update("UPDATE planner_content SET deleted_at = NOW(6) WHERE planner_id = UUID_TO_BIN(?)",
                plannerId.toString());
    }
}
