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

    public static final int SESSIONS = 3;
    public static final Path HISTORY_DIRECTORY = Path.of("build", "consistency");

    static final int PERCENT = 100;
    static final int SAVE_PERCENT = 40;
    static final int READ_PERCENT = 50;

    public static final int FAULT_QUARTERS = 4;

    public static final Slot AUTHOR_ONLY_SLOT = Slot.K3;
    public static final List<Slot> SHARED_SLOTS = List.of(Slot.K1, Slot.K2);
    public static final int STALE_SAVE_OFFSET = 0;
    public static final int STALE_READ_OFFSET = 1;

    private ConsistencyBudget() {
    }

    public static Path historyFile(long seed) {
        return HISTORY_DIRECTORY.resolve("seed-" + seed + ".jsonl");
    }

    public static String sessionName(int index) {
        return "s" + (index + 1);
    }
}
