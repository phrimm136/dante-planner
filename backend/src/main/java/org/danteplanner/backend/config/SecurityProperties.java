package org.danteplanner.backend.config;

import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.Arrays;
import java.util.Collections;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Security configuration properties.
 *
 * <p>Properties bound from application.properties:
 * <ul>
 *   <li>security.trusted-proxy-ips: Comma-separated list of trusted reverse proxy IPs
 *       (e.g., nginx). Only X-Forwarded-For headers from these IPs are trusted.</li>
 * </ul>
 *
 * <p>Security note: If trusted-proxy-ips is misconfigured, rate limiting may be
 * bypassed via X-Forwarded-For spoofing. In production, set this to your nginx IP(s).
 */
@Configuration
@ConfigurationProperties(prefix = "security")
@Getter
@Setter
@Slf4j
public class SecurityProperties {

    /**
     * Comma-separated list of trusted proxy IP addresses.
     * Default: "127.0.0.1" for local development.
     */
    private String trustedProxyIps = "127.0.0.1";

    /**
     * Parsed set of trusted proxy IPs for efficient lookup.
     */
    private Set<String> trustedProxyIpSet;

    /**
     * Parses the comma-separated trusted proxy IPs into a Set on startup.
     */
    @PostConstruct
    public void parseTrustedProxyIps() {
        if (trustedProxyIps == null || trustedProxyIps.isBlank()) {
            trustedProxyIpSet = Collections.emptySet();
            log.warn("No trusted proxy IPs configured - X-Forwarded-For header will be ignored");
            return;
        }

        trustedProxyIpSet = Arrays.stream(trustedProxyIps.split(","))
                .map(String::trim)
                .filter(ip -> !ip.isEmpty())
                .collect(Collectors.toUnmodifiableSet());

        log.info("Configured {} trusted proxy IP(s)", trustedProxyIpSet.size());
        log.debug("Trusted proxy IPs: {}", trustedProxyIpSet);
    }

    /**
     * Returns the set of trusted proxy IPs for X-Forwarded-For validation.
     *
     * @return immutable set of trusted proxy IP addresses
     */
    public Set<String> getTrustedProxyIpSet() {
        return trustedProxyIpSet;
    }
}
