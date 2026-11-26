package org.danteplanner.backend.shared.config;

import jakarta.annotation.PostConstruct;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.web.util.matcher.IpAddressMatcher;
import org.springframework.validation.annotation.Validated;

import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

import org.danteplanner.backend.shared.failure.Parsed;

/**
 * Only X-Forwarded-For headers from the configured proxy IPs are honoured; a misconfigured list
 * lets rate limiting be bypassed by X-Forwarded-For spoofing.
 */
@Configuration
@ConfigurationProperties(prefix = "security")
@Validated
@Setter
@Slf4j
public class SecurityProperties {

    private String trustedProxyIps = "127.0.0.1/32,::1/128";

    private TrustedProxies trustedProxies = TrustedProxies.parse("");

    @PostConstruct
    public void parseTrustedProxyIps() {
        trustedProxies = TrustedProxies.parse(trustedProxyIps);
        if (trustedProxies.ranges().isEmpty()) {
            log.warn("No trusted proxy IPs configured - X-Forwarded-For header will be ignored");
            return;
        }
        log.info("Configured {} trusted proxy range(s)", trustedProxies.ranges().size());
    }

    public TrustedProxies trustedProxies() {
        return trustedProxies;
    }

    @Parsed
    public record TrustedProxies(List<IpAddressMatcher> ranges) {

        public static TrustedProxies parse(String commaSeparated) {
            return new TrustedProxies(Arrays.stream(commaSeparated.split(","))
                    .map(String::trim)
                    .filter(entry -> !entry.isEmpty())
                    .map(TrustedProxies::range)
                    .toList());
        }

        public boolean contains(String peer) {
            try {
                return ranges.stream().anyMatch(range -> range.matches(peer));
            } catch (IllegalArgumentException notAnAddress) {
                return false;
            }
        }

        private static IpAddressMatcher range(String entry) {
            return switch (EntryForm.of(entry)) {
                case RANGE -> matcher(entry);
                case MALFORMED -> throw new IllegalStateException(malformed(entry));
            };
        }

        private static IpAddressMatcher matcher(String entry) {
            try {
                return new IpAddressMatcher(entry);
            } catch (IllegalArgumentException rejected) {
                throw new IllegalStateException(malformed(entry), rejected);
            }
        }

        private static String malformed(String entry) {
            return "security.trusted-proxy-ips entry '" + entry + "' is not a CIDR range";
        }
    }

    private enum EntryForm {
        RANGE,
        MALFORMED;

        private static final Pattern UNSIGNED_PREFIX = Pattern.compile("[^/]+/[0-9]+");

        static EntryForm of(String entry) {
            return UNSIGNED_PREFIX.matcher(entry).matches() ? RANGE : MALFORMED;
        }
    }
}
