package org.danteplanner.backend.service.token;

/**
 * Per-token rotation bookkeeping entry keyed by {@code jti} in the rotation state map.
 *
 * @param state        current lifecycle state of this token
 * @param successorJti jti of the minted successor; non-null only when {@link RotationState#PENDING}
 * @param pendingJwt   full successor JWT; non-null only when {@link RotationState#PENDING}, dropped on use
 * @param familyId     stable family identifier shared across the whole rotation lineage
 * @param issuedAt     epoch milliseconds when this token was issued
 * @param expiryMs     epoch milliseconds when this token expires; used for scheduled cleanup
 */
public record RotationEntry(
        RotationState state,
        String successorJti,
        String pendingJwt,
        String familyId,
        long issuedAt,
        long expiryMs
) {
}
