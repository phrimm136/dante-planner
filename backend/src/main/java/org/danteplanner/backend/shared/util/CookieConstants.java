package org.danteplanner.backend.shared.util;

/**
 * Constants for cookie names used in authentication.
 * Centralized to ensure consistency across filter and controller.
 */
public final class CookieConstants {

    /**
     * Cookie name for JWT access token.
     */
    public static final String ACCESS_TOKEN = "accessToken";

    /**
     * Cookie name for JWT refresh token.
     */
    public static final String REFRESH_TOKEN = "refreshToken";

    /**
     * Cookie name for the readable double-submit CSRF token.
     * Non-HttpOnly so the SPA can echo it back in the {@code X-CSRF-Token} header.
     */
    public static final String CSRF = "csrf";

    /**
     * Cookie name for the transient, signed-and-encrypted OAuth handshake scratchpad
     * holding {@code state} + PKCE {@code code_verifier} across Google's redirect.
     * HttpOnly, SameSite=Lax, 90-second lifetime.
     */
    public static final String OAUTH_TX = "oauth_tx";

    private CookieConstants() {
        // Utility class - prevent instantiation
    }
}
