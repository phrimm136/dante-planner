package org.danteplanner.backend.planner.validation;

import org.danteplanner.backend.planner.gamedata.GameDataRegistry;
import org.danteplanner.backend.planner.validation.startbuff.StartBuffSeason;
import org.danteplanner.backend.planner.validation.startgift.StartGiftSeason;

import java.util.Set;

public final class Season implements StartBuffSeason, StartGiftSeason {

    private final GameDataRegistry registry;
    private final int version;

    Season(GameDataRegistry registry, int version) {
        this.registry = registry;
        this.version = version;
    }

    public int identityMaxLevel() {
        return registry.identityMaxLevel(version);
    }

    @Override
    public Set<String> startBuffIds() {
        return registry.startBuffIds(version);
    }

    @Override
    public boolean hasStartGiftKeyword(String keyword) {
        return registry.hasStartGiftKeyword(version, keyword);
    }

    @Override
    public Set<String> startGiftPool(String keyword) {
        return registry.getStartGiftPool(version, keyword);
    }
}
