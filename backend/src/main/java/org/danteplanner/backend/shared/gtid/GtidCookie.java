package org.danteplanner.backend.shared.gtid;

import java.time.Duration;

import org.springframework.http.ResponseCookie;

/**
 * Builds the read-your-writes GTID cookie emitted via the {@code Set-Cookie} header.
 * The cookie carries the client's last-written GTID so subsequent reads can be routed
 * for causal consistency; a caught-up read clears it.
 */
public final class GtidCookie {

    public static final String NAME = "ryw_gtid";

    private static final String PATH = "/";
    private static final String SAME_SITE = "Lax";

    private GtidCookie() {
    }

    public static ResponseCookie of(String gtid) {
        return base(gtid).build();
    }

    public static ResponseCookie cleared() {
        return base("").maxAge(Duration.ZERO).build();
    }

    private static ResponseCookie.ResponseCookieBuilder base(String value) {
        return ResponseCookie.from(NAME, value)
                .httpOnly(true)
                .secure(true)
                .sameSite(SAME_SITE)
                .path(PATH);
    }
}
