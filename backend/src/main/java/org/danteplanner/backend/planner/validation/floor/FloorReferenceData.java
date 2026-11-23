package org.danteplanner.backend.planner.validation.floor;

import org.danteplanner.backend.planner.gamedata.GameDataRegistry;

public interface FloorReferenceData {

    boolean hasThemePack(String themePackId);

    boolean hasEgoGift(String giftId);

    boolean isGiftAffordableForThemePack(String giftId, String themePackId);

    static FloorReferenceData of(GameDataRegistry.Snapshot data) {
        return new Registry(data);
    }

    record Registry(GameDataRegistry.Snapshot data) implements FloorReferenceData {

        @Override
        public boolean hasThemePack(String themePackId) {
            return data.hasThemePack(themePackId);
        }

        @Override
        public boolean hasEgoGift(String giftId) {
            return data.hasEgoGift(giftId);
        }

        @Override
        public boolean isGiftAffordableForThemePack(String giftId, String themePackId) {
            return data.isGiftAffordableForThemePack(giftId, themePackId);
        }
    }
}
