package org.danteplanner.backend.planner.validation;

import java.nio.file.Path;

/**
 * Thrown when a game data file exists but cannot be read or parsed.
 *
 * <p>An empty registry would answer every id lookup with "unknown", so the application fails to
 * start rather than serve a validator that rejects all planner content.
 */
public class GameDataLoadException extends RuntimeException {

    /**
     * @param filePath the file that could not be read
     * @param cause    the underlying I/O or parse failure
     */
    public GameDataLoadException(Path filePath, Throwable cause) {
        super("Unreadable game data file: " + filePath, cause);
    }
}
