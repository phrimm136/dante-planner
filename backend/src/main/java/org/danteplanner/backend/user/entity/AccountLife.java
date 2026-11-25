package org.danteplanner.backend.user.entity;

import java.time.Instant;

import org.jspecify.annotations.Nullable;

public sealed interface AccountLife {

    record Live() implements AccountLife {
    }

    record Deactivated(Instant at, Instant purgeAt) implements AccountLife {
    }

    static AccountLife of(@Nullable Instant deletedAt, @Nullable Instant permanentDeleteScheduledAt) {
        if (deletedAt == null && permanentDeleteScheduledAt == null) {
            return new Live();
        }
        if (deletedAt == null || permanentDeleteScheduledAt == null) {
            throw new IllegalStateException("account is half deactivated: deleted at " + deletedAt
                    + ", permanent deletion at " + permanentDeleteScheduledAt);
        }
        return new Deactivated(deletedAt, permanentDeleteScheduledAt);
    }
}
