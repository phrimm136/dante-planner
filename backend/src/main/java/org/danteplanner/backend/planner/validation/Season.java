package org.danteplanner.backend.planner.validation;

import org.danteplanner.backend.planner.gamedata.GameDataRegistry;
import org.danteplanner.backend.planner.validation.startbuff.StartBuffSeason;
import org.danteplanner.backend.planner.validation.startgift.StartGiftSeason;

import java.util.Set;

public final class Season implements StartBuffSeason, StartGiftSeason {

    private final GameDataRegistry.Snapshot data;
    private final int version;

    Season(GameDataRegistry.Snapshot data, int version) {
        this.data = data;
        this.version = version;
    }

    public int identityMaxLevel() {
        return data.identityMaxLevel(version);
    }

    @Override
    public Set<String> startBuffIds() {
        return data.startBuffIds(version);
    }

    @Override
    public boolean hasStartGiftKeyword(String keyword) {
        return data.hasStartGiftKeyword(version, keyword);
    }

    @Override
    public Set<String> startGiftPool(String keyword) {
        return switch (data.startGiftPool(version, keyword)) {
            case GameDataRegistry.Lookup.Known<Set<String>> known -> known.value();
            case GameDataRegistry.Lookup.Unknown<Set<String>> unknown -> throw new IllegalArgumentException(
                    "season " + version + " has no start gift pool for " + keyword);
        };
    }
}
