package org.danteplanner.backend.planner.validation.startgift;

import java.util.Set;

public interface StartGiftSeason {

    boolean hasStartGiftKeyword(String keyword);

    Set<String> startGiftPool(String keyword);
}
