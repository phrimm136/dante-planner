package org.danteplanner.backend.auth.token;

import org.danteplanner.backend.auth.exception.InvalidTokenException;
import org.danteplanner.backend.auth.exception.SessionRevokedException;
import org.danteplanner.backend.shared.failure.CommandOutcome;
import org.danteplanner.backend.shared.failure.FailureUnion;

public sealed interface RotationOutcome extends FailureUnion, CommandOutcome
        permits RotationOutcome.Rotated, RotationOutcome.Revoked, RotationOutcome.Rejected {

    default Rotated orThrow() {
        return switch (this) {
            case Rotated rotated -> rotated;
            case Revoked revoked -> throw new SessionRevokedException(revoked.familyId());
            case Rejected rejected -> throw rejected.reason() == Rejected.Reason.REVOKED_FAMILY
                    ? new SessionRevokedException()
                    : new InvalidTokenException(InvalidTokenException.Reason.MISSING_CLAIMS);
        };
    }

    record Rotated(String newRefreshJwt, RefreshClaims claims) implements RotationOutcome {
    }

    record Revoked(String familyId) implements RotationOutcome {
    }

    record Rejected(Reason reason) implements RotationOutcome {

        public enum Reason {
            REVOKED_FAMILY,
            INVALID
        }
    }
}
