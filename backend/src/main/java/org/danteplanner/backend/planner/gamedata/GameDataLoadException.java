package org.danteplanner.backend.planner.gamedata;

import java.nio.file.Path;

public class GameDataLoadException extends RuntimeException {

    public GameDataLoadException(Path filePath, Throwable cause) {
        super("Unreadable game data file: " + filePath, cause);
    }
}
