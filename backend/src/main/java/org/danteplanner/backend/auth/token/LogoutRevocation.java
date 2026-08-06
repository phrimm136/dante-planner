package org.danteplanner.backend.auth.token;

import java.util.Date;
import java.util.Objects;

/**
 * A credential a logout withdraws.
 *
 * <p>Sealed over the two revocable things a session carries: a token, blacklisted for whatever
 * lifetime it has left, and the refresh lineage family the session belongs to. Every variant is
 * present by construction, so a credential absent from a revocation call is one the session did
 * not carry — never one that silently went unrevoked.</p>
 */
public sealed interface LogoutRevocation
        permits LogoutRevocation.TokenRevocation, LogoutRevocation.FamilyRevocation {

    /**
     * A token to reject from the moment the revocation lands.
     *
     * @param token  the token being withdrawn
     * @param expiry the token's expiration, which bounds the blacklist entry's TTL
     */
    record TokenRevocation(String token, Date expiry) implements LogoutRevocation {

        /**
         * @throws NullPointerException if {@code token} or {@code expiry} is null
         */
        public TokenRevocation {
            Objects.requireNonNull(token, "token");
            Objects.requireNonNull(expiry, "expiry");
        }
    }

    /**
     * A refresh lineage family to revoke, rejecting every token descended from it.
     *
     * @param familyId the family identifier
     */
    record FamilyRevocation(String familyId) implements LogoutRevocation {

        /**
         * @throws NullPointerException if {@code familyId} is null
         */
        public FamilyRevocation {
            Objects.requireNonNull(familyId, "familyId");
        }
    }
}
