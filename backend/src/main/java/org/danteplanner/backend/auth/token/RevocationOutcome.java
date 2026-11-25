package org.danteplanner.backend.auth.token;

import org.danteplanner.backend.shared.failure.CommandOutcome;

public enum RevocationOutcome implements CommandOutcome {
    CLEAR,
    REVOKED,
    SKIPPED
}
