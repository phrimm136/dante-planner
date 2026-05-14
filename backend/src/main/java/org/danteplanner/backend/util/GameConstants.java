package org.danteplanner.backend.util;

/**
 * Constants for game mechanics validation.
 * Centralized to ensure consistency across validators and services.
 */
public final class GameConstants {

    /**
     * Identity level bounds.
     */
    public static final int MIN_LEVEL = 1;
    public static final int MAX_LEVEL = 60;

    /**
     * Identity uptie bounds (upgrade tier).
     */
    public static final int MIN_UPTIE = 1;
    public static final int MAX_UPTIE = 4;

    /**
     * EGO threadspin bounds (upgrade tier). MAX is the global ceiling;
     * per-EGO max is looked up from GameDataRegistry.
     */
    public static final int MIN_THREADSPIN = 1;
    public static final int MAX_THREADSPIN = 5;

    private GameConstants() {
        // Utility class - prevent instantiation
    }
}
