package org.danteplanner.backend.planner.floor;

import java.util.List;

public record FloorSelection(ThemePack themePack, Difficulty difficulty, List<String> giftIds) {

    public FloorSelection {
        giftIds = List.copyOf(giftIds);
    }
}
