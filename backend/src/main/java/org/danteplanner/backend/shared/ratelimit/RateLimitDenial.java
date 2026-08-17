package org.danteplanner.backend.shared.ratelimit;

/**
 * How a refused request is rendered back to the caller that made it.
 */
public enum RateLimitDenial {

    /** The refusal travels as an exception the global handler renders as 429. */
    RESPOND,

    /**
     * The refusal redirects to the SPA login route under its own code. A top-level browser
     * navigation cannot render a 429 body usefully — the user would see a bare error page — so the
     * outcome has to travel as a URL the SPA can read.
     */
    REDIRECT_LOGIN
}
