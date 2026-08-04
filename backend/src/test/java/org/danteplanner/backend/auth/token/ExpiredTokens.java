package org.danteplanner.backend.auth.token;

import org.danteplanner.backend.shared.config.JwtProperties;
import org.danteplanner.backend.user.entity.UserRole;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

/**
 * Mints tokens that are well-formed and correctly signed, and whose expiry has already passed.
 *
 * <p>Sits in the service's own package because the clock-taking constructor is package-private,
 * and winding a signer back past the configured lifetime is the only way to produce an expired
 * token a test can hand to the live validator — a garbage string exercises the malformed path
 * instead.</p>
 */
public final class ExpiredTokens {

    /** Past the configured lifetime by enough that a slow test still hands over an expired token. */
    private static final long MARGIN_MILLIS = 60_000L;

    private ExpiredTokens() {
    }

    /**
     * An access token whose expiry lies in the past under the system clock.
     *
     * @param properties the running key material and token lifetimes
     * @param userId     the subject the token names
     * @param role       the role claim the token carries
     * @return a signed, expired access token
     */
    public static String accessToken(JwtProperties properties, Long userId, UserRole role) {
        Instant signedAt = Instant.now()
                .minusMillis(properties.getAccessTokenExpiry() + MARGIN_MILLIS);
        JwtTokenService signer =
                new JwtTokenService(properties, Clock.fixed(signedAt, ZoneOffset.UTC));
        return signer.generateAccessToken(userId, role);
    }
}
