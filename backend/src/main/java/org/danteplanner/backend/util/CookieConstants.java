package org.danteplanner.backend.util;

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

    private CookieConstants() {
        // Utility class - prevent instantiation
    }
}
