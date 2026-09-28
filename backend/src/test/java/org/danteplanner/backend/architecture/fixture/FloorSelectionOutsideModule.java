package org.danteplanner.backend.architecture.fixture;

import java.util.List;

import org.danteplanner.backend.planner.floor.Difficulty;
import org.danteplanner.backend.planner.floor.FloorSelection;
import org.danteplanner.backend.planner.floor.ThemePack;

/** Builds a floor selection without the parse boundary. */
public class FloorSelectionOutsideModule {

    public FloorSelection emptyFloor() {
        return new FloorSelection(ThemePack.none(), Difficulty.unset(), List.of());
    }
}
