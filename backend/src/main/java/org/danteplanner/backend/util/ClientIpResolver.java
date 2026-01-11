package org.danteplanner.backend.util;

import jakarta.servlet.http.HttpServletRequest;
import org.danteplanner.backend.config.SecurityProperties;

import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Utility class for resolving client IP addresses and identifiers from HTTP requests.
 *
 * <p>Handles X-Forwarded-For headers securely by only trusting headers
 * from configured proxy IPs (e.g., nginx). When requests come from untrusted
 * sources, the header is ignored to prevent rate limit bypass attacks.
 *
 * <p>For Docker environments where all requests appear from private IPs (NAT),
 * provides device ID fallback to maintain rate limiting effectiveness.
 *
 * <p>Usage:
 * <pre>{@code
 * String clientIp = ClientIpResolver.resolve(request, trustedProxyIps);
 * String identifier = ClientIpResolver.resolveClientIdentifier(request, trustedProxyIps, deviceId);
 * }</pre>
 *
 * <p>Security note: Attackers can spoof X-Forwarded-For to bypass IP-based
 * rate limiting. This utility prevents that by validating the direct connection
 * IP before trusting the header.
 */
public final class ClientIpResolver {

    private static final String X_FORWARDED_FOR = "X-Forwarded-For";
    private static final String CF_CONNECTING_IP = "CF-Connecting-IP";

    // IPv4: 0-255.0-255.0-255.0-255
    private static final Pattern IPV4_PATTERN = Pattern.compile(
            "^((25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\\.){3}(25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)$"
    );

    // IPv6: Simplified pattern covering common formats (full, compressed, IPv4-mapped)
    private static final Pattern IPV6_PATTERN = Pattern.compile(
            "^([0-9a-fA-F]{1,4}:){7}[0-9a-fA-F]{1,4}$|" +  // Full
            "^::([0-9a-fA-F]{1,4}:){0,6}[0-9a-fA-F]{1,4}$|" +  // Starts with ::
            "^([0-9a-fA-F]{1,4}:){1,7}:$|" +  // Ends with ::
            "^([0-9a-fA-F]{1,4}:){1,6}:[0-9a-fA-F]{1,4}$|" +  // :: in middle
            "^::$"  // Just ::
    );

    private ClientIpResolver() {
        // Utility class - prevent instantiation
    }

    /**
     * Resolves the client IP address from the request using SecurityProperties.
     *
     * <p>Supports both exact IP matching and CIDR notation (e.g., 172.18.0.0/16).
     * If the direct connection is from a trusted proxy, parses X-Forwarded-For
     * and returns the first (leftmost) IP, which is the original client.
     * Otherwise, returns getRemoteAddr() to prevent header spoofing.
     *
     * @param request the HTTP request
     * @param securityProperties security properties with trusted proxy configuration
     * @return the resolved client IP address
     */
    public static String resolve(HttpServletRequest request, SecurityProperties securityProperties) {
        String directIp = request.getRemoteAddr();

        // Only trust X-Forwarded-For if request comes from trusted proxy (supports CIDR)
        if (securityProperties.isTrustedProxy(directIp)) {
            String xForwardedFor = request.getHeader(X_FORWARDED_FOR);
            if (xForwardedFor != null && !xForwardedFor.isBlank()) {
                String parsedIp = parseFirstIp(xForwardedFor);
                // Validate IP format to prevent injection attacks
                if (isValidIp(parsedIp)) {
                    return parsedIp;
                }
                // Invalid IP format - fall back to direct connection
            }
        }

        // Untrusted source, no header, or invalid IP - use direct connection IP
        return directIp;
    }

    /**
     * Resolves the client IP address from the request.
     *
     * <p>If the direct connection is from a trusted proxy, parses X-Forwarded-For
     * and returns the first (leftmost) IP, which is the original client.
     * Otherwise, returns getRemoteAddr() to prevent header spoofing.
     *
     * @param request the HTTP request
     * @param trustedProxyIps set of trusted proxy IP addresses (e.g., nginx IPs)
     * @return the resolved client IP address
     * @deprecated Use {@link #resolve(HttpServletRequest, SecurityProperties)} for CIDR support
     */
    @Deprecated
    public static String resolve(HttpServletRequest request, Set<String> trustedProxyIps) {
        String directIp = request.getRemoteAddr();

        // Only trust X-Forwarded-For if request comes from trusted proxy
        if (trustedProxyIps.contains(directIp)) {
            String xForwardedFor = request.getHeader(X_FORWARDED_FOR);
            if (xForwardedFor != null && !xForwardedFor.isBlank()) {
                String parsedIp = parseFirstIp(xForwardedFor);
                // Validate IP format to prevent injection attacks
                if (isValidIp(parsedIp)) {
                    return parsedIp;
                }
                // Invalid IP format - fall back to direct connection
            }
        }

        // Untrusted source, no header, or invalid IP - use direct connection IP
        return directIp;
    }

    /**
     * Validates that the string is a valid IPv4 or IPv6 address.
     *
     * <p>Prevents injection of malicious strings through X-Forwarded-For header.
     *
     * @param ip the string to validate
     * @return true if valid IP format, false otherwise
     */
    static boolean isValidIp(String ip) {
        if (ip == null || ip.isEmpty()) {
            return false;
        }
        return IPV4_PATTERN.matcher(ip).matches() || IPV6_PATTERN.matcher(ip).matches();
    }

    /**
     * Parses the first IP from X-Forwarded-For header.
     *
     * <p>X-Forwarded-For format: "client, proxy1, proxy2"
     * The leftmost IP is the original client.
     *
     * @param xForwardedFor the header value
     * @return the first IP address, trimmed
     */
    private static String parseFirstIp(String xForwardedFor) {
        int commaIndex = xForwardedFor.indexOf(',');
        if (commaIndex > 0) {
            return xForwardedFor.substring(0, commaIndex).trim();
        }
        return xForwardedFor.trim();
    }

    /**
     * Checks if an IP address is in a private (RFC 1918) range.
     *
     * <p>Private IP ranges (not routable on public internet):
     * <ul>
     *   <li>10.0.0.0/8 (10.0.0.0 - 10.255.255.255)</li>
     *   <li>172.16.0.0/12 (172.16.0.0 - 172.31.255.255)</li>
     *   <li>192.168.0.0/16 (192.168.0.0 - 192.168.255.255)</li>
     *   <li>127.0.0.0/8 (loopback)</li>
     * </ul>
     *
     * <p>Used to detect Docker NAT scenarios where all requests appear from
     * nginx container IP (e.g., 172.18.0.2). In such cases, rate limiting
     * falls back to device ID instead of IP.
     *
     * @param ip the IP address to check
     * @return true if IP is private, false otherwise
     */
    public static boolean isPrivateIp(String ip) {
        if (ip == null || !isValidIp(ip)) {
            return false;
        }

        // IPv6 localhost
        if (ip.equals("::1") || ip.equals("::")) {
            return true;
        }

        // Only check IPv4 private ranges
        if (!IPV4_PATTERN.matcher(ip).matches()) {
            return false;
        }

        String[] parts = ip.split("\\.");
        int first = Integer.parseInt(parts[0]);
        int second = Integer.parseInt(parts[1]);

        // 10.0.0.0/8
        if (first == 10) {
            return true;
        }

        // 172.16.0.0/12 (172.16.0.0 - 172.31.255.255)
        if (first == 172 && second >= 16 && second <= 31) {
            return true;
        }

        // 192.168.0.0/16
        if (first == 192 && second == 168) {
            return true;
        }

        // 127.0.0.0/8 (loopback)
        if (first == 127) {
            return true;
        }

        return false;
    }

    /**
     * Resolves a client identifier for rate limiting using SecurityProperties.
     *
     * <p>Supports CIDR notation for trusted proxy configuration (e.g., 172.18.0.0/16).
     *
     * <p>Strategy:
     * <ol>
     *   <li>Check CF-Connecting-IP (Cloudflare header, highest priority)</li>
     *   <li>Check X-Forwarded-For (trusted proxies only)</li>
     *   <li>Use RemoteAddr as fallback</li>
     *   <li>If IP is private (Docker NAT), return "device:{deviceId}"</li>
     *   <li>If IP is public, return "ip:{ipAddress}"</li>
     * </ol>
     *
     * @param request the HTTP request
     * @param securityProperties security properties with trusted proxy configuration
     * @param deviceId client device ID from cookie
     * @return identifier in format "ip:xxx" or "device:xxx"
     */
    public static String resolveClientIdentifier(
            HttpServletRequest request,
            SecurityProperties securityProperties,
            UUID deviceId
    ) {
        String ip = null;

        // Priority 1: CF-Connecting-IP (Cloudflare)
        String cfIp = request.getHeader(CF_CONNECTING_IP);
        if (cfIp != null && !cfIp.isBlank() && isValidIp(cfIp.trim())) {
            ip = cfIp.trim();
        }

        // Priority 2: X-Forwarded-For (trusted proxies only, with CIDR support)
        if (ip == null) {
            ip = resolve(request, securityProperties);
        }

        // Fallback to device ID if IP is private
        if (isPrivateIp(ip)) {
            return "device:" + (deviceId != null ? deviceId.toString() : "unknown");
        }

        // Use IP for public addresses
        return "ip:" + ip;
    }

    /**
     * Resolves a client identifier for rate limiting.
     *
     * <p>Strategy:
     * <ol>
     *   <li>Check CF-Connecting-IP (Cloudflare header, highest priority)</li>
     *   <li>Check X-Forwarded-For (trusted proxies only)</li>
     *   <li>Use RemoteAddr as fallback</li>
     *   <li>If IP is private (Docker NAT), return "device:{deviceId}"</li>
     *   <li>If IP is public, return "ip:{ipAddress}"</li>
     * </ol>
     *
     * <p>Docker NAT scenario: When nginx runs in Docker, all requests appear
     * from 172.18.0.x (bridge network). Without device ID fallback, all users
     * would share one rate limit bucket (denial of service).
     *
     * @param request the HTTP request
     * @param trustedProxyIps set of trusted proxy IP addresses
     * @param deviceId client device ID from cookie
     * @return identifier in format "ip:xxx" or "device:xxx"
     * @deprecated Use {@link #resolveClientIdentifier(HttpServletRequest, SecurityProperties, UUID)} for CIDR support
     */
    @Deprecated
    public static String resolveClientIdentifier(
            HttpServletRequest request,
            Set<String> trustedProxyIps,
            UUID deviceId
    ) {
        String ip = null;

        // Priority 1: CF-Connecting-IP (Cloudflare)
        String cfIp = request.getHeader(CF_CONNECTING_IP);
        if (cfIp != null && !cfIp.isBlank() && isValidIp(cfIp.trim())) {
            ip = cfIp.trim();
        }

        // Priority 2: X-Forwarded-For (trusted proxies only)
        if (ip == null) {
            ip = resolve(request, trustedProxyIps);
        }

        // Fallback to device ID if IP is private
        if (isPrivateIp(ip)) {
            return "device:" + (deviceId != null ? deviceId.toString() : "unknown");
        }

        // Use IP for public addresses
        return "ip:" + ip;
    }
}
