package org.danteplanner.backend.moderation.util;

/**
 * Constants for moderation validation.
 * Centralized to ensure consistency across DTOs and services.
 */
public final class ModerationConstants {

    /**
     * Maximum length for a moderator-supplied action reason in characters.
     */
    public static final int ACTION_REASON_MAX_LENGTH = 500;

    /**
     * Minimum length for a reporter-supplied report reason in characters.
     */
    public static final int REPORT_REASON_MIN_LENGTH = 1;

    /**
     * Maximum length for a reporter-supplied report reason in characters.
     */
    public static final int REPORT_REASON_MAX_LENGTH = 50;

    /**
     * Maximum timeout duration in minutes, thirty days.
     */
    public static final int TIMEOUT_MAX_MINUTES = 43200;

    private ModerationConstants() {
        // Utility class - prevent instantiation
    }
}
