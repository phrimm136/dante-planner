package org.danteplanner.backend.util;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.UUID;

/**
 * Utility class for generating SHA-256 hashes for viewer deduplication.
 *
 * <p>Produces deterministic hashes for identifying unique viewers:
 * <ul>
 *   <li>Authenticated users: hash of userId + plannerId</li>
 *   <li>Anonymous users: hash of IP + User-Agent + plannerId</li>
 * </ul>
 *
 * <p>All hashes are returned as lowercase hex strings (64 characters).
 */
public final class ViewerHashUtil {

    private static final int MAX_USER_AGENT_LENGTH = 256;

    private ViewerHashUtil() {
        // Utility class - prevent instantiation
    }

    /**
     * Generates a hash for an authenticated user viewing a planner.
     *
     * @param userId the authenticated user's ID
     * @param plannerId the planner being viewed
     * @return lowercase hex string (64 chars)
     */
    public static String hashForAuthenticatedUser(Long userId, UUID plannerId) {
        String input = userId + ":" + plannerId;
        return sha256Hex(input);
    }

    /**
     * Generates a hash for an anonymous user viewing a planner.
     *
     * <p>User-Agent is truncated to 256 characters. Null User-Agent is treated as empty string.
     *
     * @param ipAddress the viewer's IP address
     * @param userAgent the viewer's User-Agent header (may be null)
     * @param plannerId the planner being viewed
     * @return lowercase hex string (64 chars)
     */
    public static String hashForAnonymousUser(String ipAddress, String userAgent, UUID plannerId) {
        String sanitizedUserAgent = sanitizeUserAgent(userAgent);
        String input = ipAddress + ":" + sanitizedUserAgent + ":" + plannerId;
        return sha256Hex(input);
    }

    /**
     * Sanitizes User-Agent: null becomes empty string, truncates to max length.
     */
    private static String sanitizeUserAgent(String userAgent) {
        if (userAgent == null) {
            return "";
        }
        if (userAgent.length() > MAX_USER_AGENT_LENGTH) {
            return userAgent.substring(0, MAX_USER_AGENT_LENGTH);
        }
        return userAgent;
    }

    /**
     * Computes SHA-256 hash and returns as lowercase hex string.
     */
    private static String sha256Hex(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return bytesToHex(hash);
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 is always available in Java
            throw new IllegalStateException("SHA-256 algorithm not available", e);
        }
    }

    /**
     * Converts byte array to lowercase hex string.
     */
    private static String bytesToHex(byte[] bytes) {
        StringBuilder hexString = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            String hex = Integer.toHexString(0xff & b);
            if (hex.length() == 1) {
                hexString.append('0');
            }
            hexString.append(hex);
        }
        return hexString.toString();
    }
}
