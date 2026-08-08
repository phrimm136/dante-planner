package org.danteplanner.backend.shared.util;

/**
 * Constants for planner validation.
 * Centralized to ensure consistency across DTOs and services.
 */
public final class PlannerConstants {

    /**
     * Maximum length for a planner title in characters, matching the width of the
     * {@code planner_content.title} column.
     */
    public static final int TITLE_MAX_LENGTH = 255;

    private PlannerConstants() {
        // Utility class - prevent instantiation
    }
}
