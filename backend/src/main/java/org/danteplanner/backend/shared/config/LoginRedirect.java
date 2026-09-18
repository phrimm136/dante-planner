package org.danteplanner.backend.shared.config;

public final class LoginRedirect {

    public static final String ERROR = "/?login=error";

    public static final String RATE_LIMITED = "/?login=rate_limited";

    private LoginRedirect() {
    }
}
