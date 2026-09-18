package org.danteplanner.backend.shared.config;

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

/**
 * Only X-Forwarded-For headers from the configured proxy IPs are honoured; a misconfigured list
 * lets rate limiting be bypassed by X-Forwarded-For spoofing.
 */
@Configuration
@ConfigurationProperties(prefix = "security")
@Getter
@Setter
@Slf4j
public class SecurityProperties {

    private String trustedProxyIps = "127.0.0.1";

    private Set<String> trustedProxyIpSet;

    private List<CidrRange> trustedProxyCidrRanges;

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

    public boolean isTrustedProxy(String ip) {
        if (ip == null || ip.isBlank()) {
            return false;
        }

        if (trustedProxyIpSet.contains(ip)) {
            return true;
        }

        for (CidrRange range : trustedProxyCidrRanges) {
            if (range.contains(ip)) {
                return true;
            }
        }

        return false;
    }

    public Set<String> getTrustedProxyIpSet() {
        return trustedProxyIpSet;
    }

    public static final class CidrRange {
        private final byte[] networkAddress;
        private final int prefixLength;

        private CidrRange(byte[] networkAddress, int prefixLength) {
            this.networkAddress = networkAddress;
            this.prefixLength = prefixLength;
        }

        public static CidrRange parse(String cidr) {
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

        public boolean contains(String ip) {
            try {
                byte[] ipBytes = InetAddress.getByName(ip).getAddress();

                if (ipBytes.length != networkAddress.length) {
                    return false; // IPv4 vs IPv6 mismatch
                }

                int fullBytes = prefixLength / 8;
                int remainingBits = prefixLength % 8;

                for (int i = 0; i < fullBytes; i++) {
                    if (ipBytes[i] != networkAddress[i]) {
                        return false;
                    }
                }

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
