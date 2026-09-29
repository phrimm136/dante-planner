package org.danteplanner.backend.shared.util;

import java.time.Duration;

public final class PlannerConstants {

    /**
     * Matches the width of the {@code planner_content.title} column.
     */
    public static final int TITLE_MAX_LENGTH = 255;

    public static final int BATCH_PULL_MAX_IDS = 50;

    public static final boolean RETIRED_BOOKMARK_FLAG = false;

    public static final int CATALOG_SYNC_BATCH_SIZE = 200;

    public static final Duration CATALOG_SYNC_PASS_BUDGET = Duration.ofSeconds(60);

    private PlannerConstants() {
    }
}
