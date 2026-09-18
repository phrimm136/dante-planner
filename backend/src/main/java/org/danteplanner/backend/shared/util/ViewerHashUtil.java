package org.danteplanner.backend.shared.util;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.UUID;

public final class ViewerHashUtil {

    private static final int MAX_USER_AGENT_LENGTH = 256;

    private ViewerHashUtil() {
    }

    public static String hashForAuthenticatedUser(Long userId, UUID plannerId) {
        String input = userId + ":" + plannerId;
        return sha256Hex(input);
    }

    public static String hashForAnonymousUser(String ipAddress, String userAgent, UUID plannerId) {
        String sanitizedUserAgent = sanitizeUserAgent(userAgent);
        String input = ipAddress + ":" + sanitizedUserAgent + ":" + plannerId;
        return sha256Hex(input);
    }

    private static String sanitizeUserAgent(String userAgent) {
        if (userAgent == null) {
            return "";
        }
        if (userAgent.length() > MAX_USER_AGENT_LENGTH) {
            return userAgent.substring(0, MAX_USER_AGENT_LENGTH);
        }
        return userAgent;
    }

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
