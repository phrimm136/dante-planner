package org.danteplanner.backend.integration.consistency;

import java.nio.file.Path;
import java.util.List;

public final class ConsistencyBudget {

    public static final String SEED_FROM = "consistency.seedFrom";
    public static final String SEED_COUNT = "consistency.seedCount";
    public static final String OPS_PER_SEED = "consistency.opsPerSeed";
    public static final String STOP_AT_FIRST_VIOLATION = "consistency.stopAtFirstViolation";

    public static final long DEFAULT_SEED_FROM = 1;
    public static final int DEFAULT_SEED_COUNT = 3;
    public static final int DEFAULT_OPS_PER_SEED = 100;
    public static final boolean DEFAULT_STOP_AT_FIRST_VIOLATION = false;

    public static final int PUBLIC_READ_BUDGET = 40;
    public static final int PROBE_BATCH_BUDGET = 5;
    public static final int DOUBLING_BUDGET = 12;
    public static final int BISECTION_DEPTH_BUDGET = 6;

    public static final List<String> SESSIONS = List.of("s1", "s2", "s3", "viewer", "anon");
    public static final int AUTHOR_DEVICES = 4;
    public static final int CREATOR = 0;
    public static final int VIEWER = 3;
    public static final int ANONYMOUS = 4;
    public static final int SETUP_PLANNERS = 2;

    public static final Path HISTORY_DIRECTORY = Path.of("build", "consistency");

    private ConsistencyBudget() {
    }

    public static Path historyFile(long seed) {
        return HISTORY_DIRECTORY.resolve("seed-" + seed + ".jsonl");
    }

    public static Path minimalHistoryFile(long seed) {
        return HISTORY_DIRECTORY.resolve("seed-" + seed + ".min.jsonl");
    }
}
