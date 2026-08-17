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

    /**
     * Maximum number of planner ids one batch pull may name. Each id resolves to a full content
     * document, so the bound is the same one the bulk import is held to.
     */
    public static final int BATCH_PULL_MAX_IDS = 50;

    private PlannerConstants() {
        // Utility class - prevent instantiation
    }
}
