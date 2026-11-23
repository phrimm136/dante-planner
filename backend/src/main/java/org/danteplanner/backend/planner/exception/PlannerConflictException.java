package org.danteplanner.backend.planner.exception;

import lombok.Getter;
import org.jspecify.annotations.Nullable;
import org.danteplanner.backend.shared.exception.DomainException;
import org.danteplanner.backend.shared.exception.ErrorKind;

@Getter
public class PlannerConflictException extends DomainException {

    private static final String ERROR_CODE = "SYNC_CONFLICT";
    private static final String SERVER_VERSION_PROPERTY = "serverVersion";

    public sealed interface Requested {

        record None() implements Requested {
        }

        record Version(long version) implements Requested {
        }

        static Requested of(@Nullable Long syncVersion) {
            return syncVersion == null ? new None() : new Version(syncVersion);
        }

        default String describe() {
            return switch (this) {
                case None none -> "null";
                case Version named -> Long.toString(named.version());
            };
        }
    }

    private final Long actualVersion;

    public PlannerConflictException(Requested requested, Long actualVersion) {
        super(ErrorKind.CONFLICT, ERROR_CODE,
                "Sync version conflict: expected " + requested.describe() + " but found " + actualVersion);
        getBody().setProperty(SERVER_VERSION_PROPERTY, actualVersion);
        this.actualVersion = actualVersion;
    }
}
