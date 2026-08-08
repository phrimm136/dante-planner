package org.danteplanner.backend.shared.util;

/**
 * Constants for comment validation and configuration.
 * Centralized to ensure consistency across DTOs and services.
 */
public final class CommentConstants {

    /**
     * Maximum length for comment content in characters.
     */
    public static final int CONTENT_MAX_LENGTH = 10000;

    /**
     * Maximum reply nesting depth, capped at the TINYINT column ceiling; the frontend handles
     * visual flattening.
     */
    public static final int MAX_DEPTH = 127;

    /**
     * Content stored for a withdrawn comment, and rendered in place of any value a viewer must
     * not see.
     */
    public static final String DELETED_CONTENT = "";

    private CommentConstants() {
        // Utility class - prevent instantiation
    }
}
