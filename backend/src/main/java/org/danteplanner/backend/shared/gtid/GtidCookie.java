package org.danteplanner.backend.shared.gtid;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.http.ResponseCookie;

/**
 * A raw GTID set is not cookie-safe: a set spanning multiple source UUIDs is comma-separated and
 * RFC 6265 forbids commas in cookie values.
 */
public final class GtidCookie {

    public static final String NAME = "ryw_gtid";

    private static final String PATH = "/";
    private static final String SAME_SITE = "Lax";

    private static final Pattern UUID_SET_SEPARATOR = Pattern.compile("\\s*,\\s*");
    private static final Pattern UUID = Pattern.compile(
            "\\p{XDigit}{8}-\\p{XDigit}{4}-\\p{XDigit}{4}-\\p{XDigit}{4}-\\p{XDigit}{12}");
    private static final Pattern INTERVAL = Pattern.compile("(\\d+)(?:-(\\d+))?");

    private GtidCookie() {
    }

    public static ResponseCookie of(String gtid) {
        return base(encode(gtid)).build();
    }

    public static ResponseCookie cleared() {
        return base("").maxAge(Duration.ZERO).build();
    }

    public static Optional<String> decode(String cookieValue) {
        return decodeText(cookieValue).filter(GtidCookie::isGtidSet);
    }

    public static boolean isUnusable(String cookieValue) {
        return decodeText(cookieValue).filter(text -> !isGtidSet(text)).isPresent();
    }

    private static boolean isGtidSet(String text) {
        for (String uuidSet : UUID_SET_SEPARATOR.split(text, -1)) {
            if (!isUuidSet(uuidSet)) {
                return false;
            }
        }
        return true;
    }

    private static boolean isUuidSet(String uuidSet) {
        String[] parts = uuidSet.split(":", -1);
        if (parts.length < 2 || !UUID.matcher(parts[0]).matches()) {
            return false;
        }
        for (int i = 1; i < parts.length; i++) {
            if (!isInterval(parts[i])) {
                return false;
            }
        }
        return true;
    }

    private static boolean isInterval(String interval) {
        Matcher matcher = INTERVAL.matcher(interval);
        if (!matcher.matches()) {
            return false;
        }
        try {
            long start = Long.parseLong(matcher.group(1));
            long end = matcher.group(2) == null ? start : Long.parseLong(matcher.group(2));
            return start >= 1 && start <= end;
        } catch (NumberFormatException ex) {
            return false;
        }
    }

    private static Optional<String> decodeText(String cookieValue) {
        if (cookieValue == null || cookieValue.isBlank()) {
            return Optional.empty();
        }
        try {
            String text = new String(
                    Base64.getUrlDecoder().decode(cookieValue), StandardCharsets.UTF_8);
            return text.isBlank() ? Optional.empty() : Optional.of(text);
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
