package org.danteplanner.backend.util;

import jakarta.servlet.http.HttpServletRequest;

import java.util.Set;
import java.util.regex.Pattern;

/**
 * Utility class for resolving client IP addresses from HTTP requests.
 *
 * <p>Handles X-Forwarded-For headers securely by only trusting headers
 * from configured proxy IPs (e.g., nginx). When requests come from untrusted
 * sources, the header is ignored to prevent rate limit bypass attacks.
 *
 * <p>Usage:
 * <pre>{@code
 * String clientIp = ClientIpResolver.resolve(request, trustedProxyIps);
 * }</pre>
 *
 * <p>Security note: Attackers can spoof X-Forwarded-For to bypass IP-based
 * rate limiting. This utility prevents that by validating the direct connection
 * IP before trusting the header.
 */
public final class ClientIpResolver {

    private static final String X_FORWARDED_FOR = "X-Forwarded-For";

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
     * Resolves the client IP address from the request.
     *
     * <p>If the direct connection is from a trusted proxy, parses X-Forwarded-For
     * and returns the first (leftmost) IP, which is the original client.
     * Otherwise, returns getRemoteAddr() to prevent header spoofing.
     *
     * @param request the HTTP request
     * @param trustedProxyIps set of trusted proxy IP addresses (e.g., nginx IPs)
     * @return the resolved client IP address
     */
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
}
