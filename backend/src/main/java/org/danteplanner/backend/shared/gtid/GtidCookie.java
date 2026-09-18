package org.danteplanner.backend.shared.gtid;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.Optional;

import org.springframework.http.ResponseCookie;

/**
 * A raw GTID set is not cookie-safe: a set spanning multiple source UUIDs is comma-separated and
 * RFC 6265 forbids commas in cookie values.
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
