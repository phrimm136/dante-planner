package org.danteplanner.backend.planner.validation.floor;

import org.danteplanner.backend.planner.gamedata.GameDataRegistry;

public interface FloorReferenceData {

    boolean hasThemePack(String themePackId);

    boolean hasEgoGift(String giftId);

    boolean isGiftAffordableForThemePack(String giftId, String themePackId);

    static FloorReferenceData of(GameDataRegistry registry) {
        return new Registry(registry);
    }

    record Registry(GameDataRegistry registry) implements FloorReferenceData {

        @Override
        public boolean hasThemePack(String themePackId) {
            return registry.hasThemePack(themePackId);
        }

        @Override
        public boolean hasEgoGift(String giftId) {
            return registry.hasEgoGift(giftId);
        }

        @Override
        public boolean isGiftAffordableForThemePack(String giftId, String themePackId) {
            return registry.isGiftAffordableForThemePack(giftId, themePackId);
        }
    }
}
