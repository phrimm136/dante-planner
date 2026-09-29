package org.danteplanner.backend.planner.validation;

import org.danteplanner.backend.planner.gamedata.GameDataRegistry;
import org.danteplanner.backend.planner.validation.startbuff.StartBuffSeason;
import org.danteplanner.backend.planner.validation.startgift.StartGiftSeason;

import java.util.Set;

record SeasonReferences(GameDataRegistry registry, int version) implements StartBuffSeason, StartGiftSeason {

    @Override
    public boolean hasStartBuff(String buffId) {
        return registry.hasStartBuff(version, buffId);
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
