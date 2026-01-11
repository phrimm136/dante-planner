package org.danteplanner.backend.config;

import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
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
     * Parsed set of trusted proxy IPs for efficient lookup (exact matches).
     */
    private Set<String> trustedProxyIpSet;

    /**
     * Parsed list of CIDR subnets for range matching.
     */
    private List<CidrRange> trustedProxyCidrRanges;

    /**
     * Parses the comma-separated trusted proxy IPs into a Set on startup.
     * Supports both individual IPs and CIDR notation (e.g., 172.18.0.0/16).
     */
    @PostConstruct
    public void parseTrustedProxyIps() {
        if (trustedProxyIps == null || trustedProxyIps.isBlank()) {
            trustedProxyIpSet = Collections.emptySet();
            trustedProxyCidrRanges = Collections.emptyList();
            log.warn("No trusted proxy IPs configured - X-Forwarded-For header will be ignored");
            return;
        }

        List<String> exactIps = new ArrayList<>();
        List<CidrRange> cidrRanges = new ArrayList<>();

        Arrays.stream(trustedProxyIps.split(","))
                .map(String::trim)
                .filter(ip -> !ip.isEmpty())
                .forEach(entry -> {
                    if (entry.contains("/")) {
                        try {
                            cidrRanges.add(CidrRange.parse(entry));
                            log.debug("Added CIDR range: {}", entry);
                        } catch (IllegalArgumentException e) {
                            log.error("Invalid CIDR notation: {} - {}", entry, e.getMessage());
                        }
                    } else {
                        exactIps.add(entry);
                    }
                });

        trustedProxyIpSet = Set.copyOf(exactIps);
        trustedProxyCidrRanges = List.copyOf(cidrRanges);

        log.info("Configured {} trusted proxy IP(s) and {} CIDR range(s)",
                trustedProxyIpSet.size(), trustedProxyCidrRanges.size());
    }

    /**
     * Checks if the given IP is trusted (exact match or within CIDR range).
     *
     * @param ip IP address to check
     * @return true if trusted
     */
    public boolean isTrustedProxy(String ip) {
        if (ip == null || ip.isBlank()) {
            return false;
        }

        // Check exact match first (fast path)
        if (trustedProxyIpSet.contains(ip)) {
            return true;
        }

        // Check CIDR ranges
        for (CidrRange range : trustedProxyCidrRanges) {
            if (range.contains(ip)) {
                return true;
            }
        }

        return false;
    }

    /**
     * Returns the set of trusted proxy IPs for X-Forwarded-For validation.
     *
     * @return immutable set of trusted proxy IP addresses
     */
    public Set<String> getTrustedProxyIpSet() {
        return trustedProxyIpSet;
    }

    /**
     * CIDR range representation for subnet matching.
     */
    private static class CidrRange {
        private final byte[] networkAddress;
        private final int prefixLength;

        private CidrRange(byte[] networkAddress, int prefixLength) {
            this.networkAddress = networkAddress;
            this.prefixLength = prefixLength;
        }

        static CidrRange parse(String cidr) {
            String[] parts = cidr.split("/");
            if (parts.length != 2) {
                throw new IllegalArgumentException("Invalid CIDR format: " + cidr);
            }

            try {
                InetAddress address = InetAddress.getByName(parts[0]);
                int prefix = Integer.parseInt(parts[1]);

                if (prefix < 0 || prefix > 32) {
                    throw new IllegalArgumentException("Invalid prefix length: " + prefix);
                }

                return new CidrRange(address.getAddress(), prefix);
            } catch (UnknownHostException e) {
                throw new IllegalArgumentException("Invalid IP address: " + parts[0], e);
            }
        }

        boolean contains(String ip) {
            try {
                byte[] ipBytes = InetAddress.getByName(ip).getAddress();

                if (ipBytes.length != networkAddress.length) {
                    return false; // IPv4 vs IPv6 mismatch
                }

                int fullBytes = prefixLength / 8;
                int remainingBits = prefixLength % 8;

                // Compare full bytes
                for (int i = 0; i < fullBytes; i++) {
                    if (ipBytes[i] != networkAddress[i]) {
                        return false;
                    }
                }

                // Compare remaining bits
                if (remainingBits > 0 && fullBytes < ipBytes.length) {
                    int mask = 0xFF << (8 - remainingBits);
                    if ((ipBytes[fullBytes] & mask) != (networkAddress[fullBytes] & mask)) {
                        return false;
                    }
                }

                return true;
            } catch (UnknownHostException e) {
                return false;
            }
        }
    }
}
