package org.danteplanner.backend.shared.failure;

public enum WriteOutcome implements CommandOutcome {
    APPLIED,
    NOTHING;

    public static WriteOutcome of(int matchedRows) {
        return switch (matchedRows) {
            case 0 -> NOTHING;
            default -> APPLIED;
        };
    }
}
