package org.danteplanner.backend.util;

/**
 * Constants for comment validation and configuration.
 * Centralized to ensure consistency across DTOs and services.
 */
public final class CommentConstants {

    /**
     * Maximum length for comment content in characters.
     */
    public static final int CONTENT_MAX_LENGTH = 10000;

    private CommentConstants() {
        // Utility class - prevent instantiation
    }
}
