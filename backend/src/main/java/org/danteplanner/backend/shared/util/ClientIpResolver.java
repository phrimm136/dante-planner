package org.danteplanner.backend.shared.util;

import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.regex.Pattern;

import jakarta.servlet.http.HttpServletRequest;
import org.danteplanner.backend.shared.config.SecurityProperties;
import org.danteplanner.backend.shared.config.SecurityProperties.CidrRange;

/**
 * An {@code X-Forwarded-For} or {@code CF-Connecting-IP} header is honoured only when the direct
 * peer is a configured trusted proxy. Both headers are attacker-controlled otherwise.
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

    public static String resolveClientIdentifier(
            HttpServletRequest request,
            SecurityProperties securityProperties,
            Supplier<UUID> deviceId
    ) {
        String ip = cloudflareIp(request, securityProperties);
        if (ip == null) {
            ip = resolve(request, securityProperties);
        }

        if (isPrivateIp(ip)) {
            UUID resolvedDeviceId = deviceId.get();
            return DEVICE_IDENTIFIER_PREFIX + (resolvedDeviceId != null ? resolvedDeviceId.toString() : UNKNOWN_DEVICE);
        }
        return IP_IDENTIFIER_PREFIX + ip;
    }

    static boolean isValidIp(String ip) {
        if (ip == null || ip.isEmpty()) {
            return false;
        }
        return IPV4_PATTERN.matcher(ip).matches() || IPV6_PATTERN.matcher(ip).matches();
    }

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
