package org.danteplanner.backend.shared.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

import org.danteplanner.backend.shared.failure.Parsed;
import org.jspecify.annotations.Nullable;

/**
 * A client-supplied {@code returnTo} is honored only when its origin is in the configured
 * {@code cors.allowed-origins} allowlist; otherwise the first allowed origin is used.
 */
@Component
public class FrontendProperties {

    private final String defaultUrl;
    private final Set<String> allowedOrigins;

    public FrontendProperties(@Value("${cors.allowed-origins}") String allowedOrigins) {
        Set<String> origins = new LinkedHashSet<>();
        for (String origin : allowedOrigins.split(",")) {
            String trimmed = stripTrailingSlash(origin.trim());
            if (!trimmed.isBlank()) {
                origins.add(trimmed);
            }
        }
        if (origins.isEmpty()) {
            throw new IllegalStateException(
                    "cors.allowed-origins must define a non-blank origin for the SPA redirect");
        }
        this.allowedOrigins = Set.copyOf(origins);
        this.defaultUrl = origins.iterator().next();
    }

    public String getUrl() {
        return defaultUrl;
    }

    public String resolveReturnTo(@Nullable String returnTo) {
        return ReturnUrl.parse(returnTo, allowedOrigins, defaultUrl).value();
    }

    @Parsed
    private record ReturnUrl(String value) {

        static ReturnUrl parse(@Nullable String supplied, Set<String> allowedOrigins, String defaultUrl) {
            String candidate = Objects.requireNonNullElse(supplied, "");
            try {
                URI uri = URI.create(candidate);
                return switch (Origin.of(uri.getScheme(), uri.getHost(), uri.getPort(), allowedOrigins)) {
                    case ALLOWED -> new ReturnUrl(candidate);
                    case FOREIGN -> new ReturnUrl(defaultUrl);
                };
            } catch (IllegalArgumentException unparsable) {
                return new ReturnUrl(defaultUrl);
            }
        }
    }

    private enum Origin {
        ALLOWED,
        FOREIGN;

        static Origin of(@Nullable String scheme, @Nullable String host, int port, Set<String> allowedOrigins) {
            if (scheme == null || host == null) {
                return FOREIGN;
            }
            String origin = scheme + "://" + host + (port == -1 ? "" : ":" + port);
            return allowedOrigins.contains(origin) ? ALLOWED : FOREIGN;
        }
    }

    private static String stripTrailingSlash(String value) {
        String result = value;
        while (result.endsWith("/")) {
            result = result.substring(0, result.length() - 1);
        }
        return result;
    }
}
