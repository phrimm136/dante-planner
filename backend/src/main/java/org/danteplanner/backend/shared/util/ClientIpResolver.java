package org.danteplanner.backend.shared.util;

import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;

import jakarta.servlet.http.HttpServletRequest;
import org.danteplanner.backend.shared.config.SecurityProperties;
import org.danteplanner.backend.shared.config.SecurityProperties.CidrRange;

/**
 * Resolves client IP addresses and rate-limit identifiers from HTTP requests.
 *
 * <p>An {@code X-Forwarded-For} or {@code CF-Connecting-IP} header is honoured only when the
 * direct peer is a configured trusted proxy. Both headers are attacker-controlled otherwise, and
 * trusting either would let any caller choose its own rate-limit bucket.</p>
 *
 * <p>Where every request arrives from a private address — nginx behind Docker NAT — the IP
 * identifies the proxy rather than the caller, so the identifier falls back to the device id.</p>
 */
public final class ClientIpResolver {

    private static final String X_FORWARDED_FOR = "X-Forwarded-For";
    private static final String CF_CONNECTING_IP = "CF-Connecting-IP";

    private static final String IP_IDENTIFIER_PREFIX = "ip:";
    private static final String DEVICE_IDENTIFIER_PREFIX = "device:";
    private static final String UNKNOWN_DEVICE = "unknown";

    private static final Pattern IPV4_PATTERN = Pattern.compile(
            "^((25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\\.){3}(25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)$"
    );

    /** Covers the full, compressed, leading-{@code ::} and trailing-{@code ::} spellings. */
    private static final Pattern IPV6_PATTERN = Pattern.compile(
            "^([0-9a-fA-F]{1,4}:){7}[0-9a-fA-F]{1,4}$|"
            + "^::([0-9a-fA-F]{1,4}:){0,6}[0-9a-fA-F]{1,4}$|"
            + "^([0-9a-fA-F]{1,4}:){1,7}:$|"
            + "^([0-9a-fA-F]{1,4}:){1,6}:[0-9a-fA-F]{1,4}$|"
            + "^::$"
    );

    /** RFC 1918 space plus loopback: not routable, so never a caller's own address. */
    private static final List<CidrRange> PRIVATE_IPV4_RANGES = List.of(
            CidrRange.parse("10.0.0.0/8"),
            CidrRange.parse("172.16.0.0/12"),
            CidrRange.parse("192.168.0.0/16"),
            CidrRange.parse("127.0.0.0/8"));

    private static final List<String> PRIVATE_IPV6_ADDRESSES = List.of("::1", "::");

    private ClientIpResolver() {
    }

    /**
     * Resolves the caller's IP address.
     *
     * <p>Reads the leftmost {@code X-Forwarded-For} hop when the direct peer is a trusted proxy
     * and that hop is a well-formed address; otherwise reports the direct peer, which cannot be
     * spoofed.</p>
     *
     * @param request            the HTTP request
     * @param securityProperties the trusted-proxy configuration
     * @return the caller's IP address
     */
    public static String resolve(HttpServletRequest request, SecurityProperties securityProperties) {
        String directIp = request.getRemoteAddr();
        if (!securityProperties.isTrustedProxy(directIp)) {
            return directIp;
        }

        String forwarded = request.getHeader(X_FORWARDED_FOR);
        if (forwarded == null || forwarded.isBlank()) {
            return directIp;
        }

        String claimedIp = firstHop(forwarded);
        return isValidIp(claimedIp) ? claimedIp : directIp;
    }

    /**
     * Resolves the identifier a request is rate-limited under.
     *
     * <p>Cloudflare's {@code CF-Connecting-IP} wins over {@code X-Forwarded-For} when the peer is
     * trusted. A private resolved address means the proxy, not the caller, so the device id
     * identifies the caller instead.</p>
     *
     * @param request            the HTTP request
     * @param securityProperties the trusted-proxy configuration
     * @param deviceId           the caller's device id from its cookie, may be null
     * @return {@code ip:<address>} or {@code device:<id>}
     */
    public static String resolveClientIdentifier(
            HttpServletRequest request,
            SecurityProperties securityProperties,
            UUID deviceId
    ) {
        String ip = cloudflareIp(request, securityProperties);
        if (ip == null) {
            ip = resolve(request, securityProperties);
        }

        if (isPrivateIp(ip)) {
            return DEVICE_IDENTIFIER_PREFIX + (deviceId != null ? deviceId.toString() : UNKNOWN_DEVICE);
        }
        return IP_IDENTIFIER_PREFIX + ip;
    }

    /**
     * Whether the string is a well-formed IPv4 or IPv6 address.
     *
     * @param ip the string to check
     * @return true when it is an address
     */
    static boolean isValidIp(String ip) {
        if (ip == null || ip.isEmpty()) {
            return false;
        }
        return IPV4_PATTERN.matcher(ip).matches() || IPV6_PATTERN.matcher(ip).matches();
    }

    /**
     * Whether the address is private (RFC 1918 or loopback) and therefore identifies a network
     * rather than a caller.
     *
     * @param ip the address to check
     * @return true when the address is private
     */
    public static boolean isPrivateIp(String ip) {
        if (!isValidIp(ip)) {
            return false;
        }
        if (PRIVATE_IPV6_ADDRESSES.contains(ip)) {
            return true;
        }
        if (!IPV4_PATTERN.matcher(ip).matches()) {
            return false;
        }
        return PRIVATE_IPV4_RANGES.stream().anyMatch(range -> range.contains(ip));
    }

    /**
     * The edge forwards {@code CF-Connecting-IP} verbatim, so it is evidence only when the peer is
     * the edge. {@code isValidIp} checks shape, never provenance.
     */
    private static String cloudflareIp(HttpServletRequest request, SecurityProperties securityProperties) {
        String header = request.getHeader(CF_CONNECTING_IP);
        if (header == null || header.isBlank()) {
            return null;
        }
        String claimedIp = header.trim();
        if (!isValidIp(claimedIp) || !securityProperties.isTrustedProxy(request.getRemoteAddr())) {
            return null;
        }
        return claimedIp;
    }

    /** {@code X-Forwarded-For} is {@code client, proxy1, proxy2}; the leftmost hop is the client. */
    private static String firstHop(String xForwardedFor) {
        int commaIndex = xForwardedFor.indexOf(',');
        return commaIndex > 0
                ? xForwardedFor.substring(0, commaIndex).trim()
                : xForwardedFor.trim();
    }
}
