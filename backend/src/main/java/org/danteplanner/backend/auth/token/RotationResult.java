package org.danteplanner.backend.auth.token;

import org.danteplanner.backend.auth.exception.InvalidTokenException;
import org.danteplanner.backend.auth.exception.SessionRevokedException;

/**
 * Outcome of a refresh-token rotation attempt.
 *
 * <p>Sealed over the three terminal outcomes the filter/facade layer must distinguish:
 * a successful rotation that yields a new refresh token, a theft-triggered family
 * revocation, and a plain rejection (revoked family or invalid token).</p>
 */
public sealed interface RotationResult
        permits RotationResult.Rotated, RotationResult.Revoked, RotationResult.Rejected {

    /**
     * This outcome as a rotation, or the failure it stands for.
     *
     * <p>The single reading of what a non-rotation means, carried by the outcome rather than by
     * {@link RefreshRotationService} so that both callers share it: the filter chain mocks that
     * service in its unit tests, and a mapping declared there would be intercepted rather than
     * run.</p>
     *
     * @return this outcome, when it is a successful rotation
     * @throws SessionRevokedException if the token's family was revoked, by theft or otherwise
     * @throws InvalidTokenException   if the token is rejected for any other reason
     */
    default Rotated orThrow() {
        return switch (this) {
            case Rotated rotated -> rotated;
            case Revoked revoked -> throw new SessionRevokedException(revoked.familyId());
            case Rejected rejected -> throw rejected.reason() == Rejected.Reason.REVOKED_FAMILY
                    ? new SessionRevokedException(null)
                    : new InvalidTokenException(InvalidTokenException.Reason.REVOKED);
        };
    }

    /**
     * Rotation succeeded; a fresh refresh token was minted.
     *
     * @param newRefreshJwt the newly minted successor refresh JWT to set as a cookie
     * @param claims        parsed claims of {@code newRefreshJwt}
     */
    record Rotated(String newRefreshJwt, TokenClaims claims) implements RotationResult {
    }

    /**
     * Theft detected; the entire token family was revoked.
     *
     * @param familyId the revoked family identifier
     */
    record Revoked(String familyId) implements RotationResult {
    }

    /**
     * Rotation rejected without minting a new token.
     *
     * @param reason machine-readable rejection cause
     */
    record Rejected(Reason reason) implements RotationResult {

        /**
         * Cause of a {@link Rejected} outcome.
         */
        public enum Reason {
            REVOKED_FAMILY,
            INVALID
        }
    }
}
