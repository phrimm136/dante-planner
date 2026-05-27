package org.danteplanner.backend.service.token;

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
