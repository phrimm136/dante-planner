package org.danteplanner.backend.shared.gtid;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.Optional;

import org.springframework.http.ResponseCookie;

/**
 * Builds the read-your-writes GTID cookie emitted via the {@code Set-Cookie} header.
 * The cookie carries the client's last-written GTID so subsequent reads can be routed
 * for causal consistency; a caught-up read clears it.
 *
 * <p>The value is Base64url-encoded: a raw GTID set is not cookie-safe, because a set
 * spanning multiple source UUIDs is comma-separated and RFC 6265 forbids commas in
 * cookie values.</p>
 */
public final class GtidCookie {

    public static final String NAME = "ryw_gtid";

    private static final String PATH = "/";
    private static final String SAME_SITE = "Lax";

    private GtidCookie() {
    }

    public static ResponseCookie of(String gtid) {
        return base(encode(gtid)).build();
    }

    public static ResponseCookie cleared() {
        return base("").maxAge(Duration.ZERO).build();
    }

    /**
     * Decodes a cookie value produced by {@link #of(String)} back into the raw GTID set.
     *
     * <p>Returns empty for blank or undecodable values so a tampered cookie degrades to
     * "no cookie" rather than an error.</p>
     */
    public static Optional<String> decode(String cookieValue) {
        if (cookieValue == null || cookieValue.isBlank()) {
            return Optional.empty();
        }
        try {
            String gtid = new String(
                    Base64.getUrlDecoder().decode(cookieValue), StandardCharsets.UTF_8);
            return gtid.isBlank() ? Optional.empty() : Optional.of(gtid);
        } catch (IllegalArgumentException ex) {
            return Optional.empty();
        }
    }

    private static String encode(String gtid) {
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(gtid.getBytes(StandardCharsets.UTF_8));
    }

    private static ResponseCookie.ResponseCookieBuilder base(String value) {
        return ResponseCookie.from(NAME, value)
                .httpOnly(true)
                .secure(true)
                .sameSite(SAME_SITE)
                .path(PATH);
    }
}
