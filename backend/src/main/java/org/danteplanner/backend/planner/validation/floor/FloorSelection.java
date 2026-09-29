package org.danteplanner.backend.planner.validation.floor;

import java.util.List;

public record FloorSelection(ThemePack themePack, Difficulty difficulty, List<String> giftIds) {

    public FloorSelection {
        giftIds = List.copyOf(giftIds);
    }
}
