package org.danteplanner.backend.shared.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.LinkedHashSet;
import java.util.Set;

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

    public String resolveReturnTo(String returnTo) {
        if (returnTo == null || returnTo.isBlank()) {
            return defaultUrl;
        }
        try {
            URI uri = URI.create(returnTo);
            if (uri.getScheme() != null && uri.getHost() != null
                    && allowedOrigins.contains(originOf(uri))) {
                return returnTo;
            }
        } catch (IllegalArgumentException ignored) {
        }
        return defaultUrl;
    }

    private static String originOf(URI uri) {
        String origin = uri.getScheme() + "://" + uri.getHost();
        if (uri.getPort() != -1) {
            origin += ":" + uri.getPort();
        }
        return origin;
    }

    private static String stripTrailingSlash(String value) {
        String result = value;
        while (result.endsWith("/")) {
            result = result.substring(0, result.length() - 1);
        }
        return result;
    }
}
