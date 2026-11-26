package org.danteplanner.backend.shared.util;

import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.regex.Pattern;

import jakarta.servlet.http.HttpServletRequest;
import org.jspecify.annotations.Nullable;

import org.danteplanner.backend.shared.config.SecurityProperties;
import org.danteplanner.backend.shared.config.SecurityProperties.CidrRange;

/**
 * An {@code X-Forwarded-For} or {@code CF-Connecting-IP} header is honoured only when the direct
 * peer is a configured trusted proxy. Both headers are attacker-controlled otherwise.
 */
public final class ClientIpResolver {

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
        Peer peer = Peer.of(securityProperties.isTrustedProxy(request.getRemoteAddr()));
        return forwardedOrDirect(request, peer);
    }

    public static String resolveClientIdentifier(
            HttpServletRequest request,
            SecurityProperties securityProperties,
            Supplier<UUID> deviceId
    ) {
        Peer peer = Peer.of(securityProperties.isTrustedProxy(request.getRemoteAddr()));
        IpClaim edge = IpClaim.edge(RequestHeaders.cfConnectingIp(request));
        String forwardedOrDirect = forwardedOrDirect(request, peer);
        String ip = ipOf(peer, edge, forwardedOrDirect);

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

    private static String forwardedOrDirect(HttpServletRequest request, Peer peer) {
        IpClaim forwarded = IpClaim.forwarded(RequestHeaders.forwardedFor(request));
        return ipOf(peer, forwarded, request.getRemoteAddr());
    }

    /**
     * A claimed address counts only when the peer is a trusted proxy: the edge forwards
     * {@code CF-Connecting-IP} verbatim and a proxy appends to {@code X-Forwarded-For}.
     * {@code isValidIp} checks shape, never provenance.
     */
    private static String ipOf(Peer peer, IpClaim claim, String fallback) {
        return switch (claim) {
            case IpClaim.Claimed claimed when peer == Peer.TRUSTED_PROXY && isValidIp(claimed.ip()) -> claimed.ip();
            case IpClaim.Claimed claimed -> fallback;
            case IpClaim.Absent absent -> fallback;
        };
    }

    private enum Peer {
        TRUSTED_PROXY,
        DIRECT;

        static Peer of(boolean trustedProxy) {
            return trustedProxy ? TRUSTED_PROXY : DIRECT;
        }
    }

    private sealed interface IpClaim {

        record Claimed(String ip) implements IpClaim {
        }

        record Absent() implements IpClaim {
        }

        static IpClaim edge(@Nullable String header) {
            return header == null || header.isBlank() ? new Absent() : new Claimed(header.trim());
        }

        static IpClaim forwarded(@Nullable String header) {
            return header == null || header.isBlank() ? new Absent() : new Claimed(firstHop(header));
        }
    }

    /** {@code X-Forwarded-For} is {@code client, proxy1, proxy2}; the leftmost hop is the client. */
    private static String firstHop(String xForwardedFor) {
        int commaIndex = xForwardedFor.indexOf(',');
        return commaIndex > 0
                ? xForwardedFor.substring(0, commaIndex).trim()
                : xForwardedFor.trim();
    }
}
