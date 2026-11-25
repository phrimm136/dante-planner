package org.danteplanner.backend.planner.entity;

import org.danteplanner.backend.shared.failure.CommandOutcome;
import org.danteplanner.backend.shared.failure.WriteOutcome;

public enum WithdrawalOutcome implements CommandOutcome {

    NONE,

    WITHDRAWN;

    public static WithdrawalOutcome of(WriteOutcome write) {
        return switch (write) {
            case APPLIED -> WITHDRAWN;
            case NOTHING -> NONE;
        };
    }

    public WithdrawalOutcome or(WithdrawalOutcome other) {
        return switch (this) {
            case WITHDRAWN -> WITHDRAWN;
            case NONE -> other;
        };
    }
}
