package org.danteplanner.backend.shared.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Resolves the SPA origin the BFF redirects the browser back to after the OAuth handshake.
 *
 * <p>The post-login redirect returns the user to where they started auth (a client-supplied
 * {@code returnTo}). To prevent open-redirect abuse, a {@code returnTo} is honored only when its
 * origin is in the configured {@code cors.allowed-origins} allowlist; otherwise the first allowed
 * origin is used as a safe default.</p>
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

    /**
     * @return the default SPA base origin (first allowed origin), used when no safe returnTo exists
     */
    public String getUrl() {
        return defaultUrl;
    }

    /**
     * Resolves a client-supplied {@code returnTo} to a safe absolute redirect target.
     *
     * <p>Returns {@code returnTo} unchanged only when it parses to an absolute URL whose origin
     * (scheme + host + port) exactly matches an allowed origin; otherwise returns the default
     * origin. This is the open-redirect guard — a {@code returnTo} pointing at a foreign host
     * (or a protocol-relative / malformed value) is never honored.</p>
     *
     * @param returnTo the client-supplied return URL (nullable)
     * @return a safe absolute redirect target
     */
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
            // malformed returnTo → fall through to the default origin
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
