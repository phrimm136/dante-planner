package org.danteplanner.backend.auth.token;

import org.danteplanner.backend.auth.exception.InvalidTokenException;
import org.danteplanner.backend.auth.exception.SessionRevokedException;
import org.danteplanner.backend.shared.failure.FailureUnion;

public sealed interface RotationResult extends FailureUnion
        permits RotationResult.Rotated, RotationResult.Revoked, RotationResult.Rejected {

    default Rotated orThrow() {
        return switch (this) {
            case Rotated rotated -> rotated;
            case Revoked revoked -> throw new SessionRevokedException(revoked.familyId());
            case Rejected rejected -> throw rejected.reason() == Rejected.Reason.REVOKED_FAMILY
                    ? new SessionRevokedException()
                    : new InvalidTokenException(InvalidTokenException.Reason.REVOKED);
        };
    }

    record Rotated(String newRefreshJwt, TokenClaims claims) implements RotationResult {
    }

    record Revoked(String familyId) implements RotationResult {
    }

    record Rejected(Reason reason) implements RotationResult {

        public enum Reason {
            REVOKED_FAMILY,
            INVALID
        }
    }
}
