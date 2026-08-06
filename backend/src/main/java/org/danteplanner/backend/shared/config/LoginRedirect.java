package org.danteplanner.backend.shared.config;

/**
 * The SPA routes a rejected login lands the browser on.
 *
 * <p>Held together because the codes are only useful as siblings: a caller turned away by the rate
 * limiter and a caller who declined consent must not arrive at the same URL, or the SPA cannot
 * tell a temporary refusal from a deliberate one.</p>
 */
public final class LoginRedirect {

    /** The login attempt failed: declined consent, a tampered transaction, or an exchange failure. */
    public static final String ERROR = "/?login=error";

    /** The login attempt was refused by the rate limiter and is worth retrying. */
    public static final String RATE_LIMITED = "/?login=rate_limited";

    private LoginRedirect() {
    }
}
