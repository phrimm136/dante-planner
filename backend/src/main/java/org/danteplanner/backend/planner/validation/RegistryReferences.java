package org.danteplanner.backend.planner.validation;

import org.danteplanner.backend.planner.gamedata.GameDataRegistry;
import org.danteplanner.backend.planner.validation.ego.EgoReferenceData;
import org.danteplanner.backend.planner.validation.egogift.EgoGiftReferenceData;
import org.danteplanner.backend.planner.validation.identity.IdentityReferenceData;
import org.danteplanner.backend.planner.validation.sinner.SinnerIdValidator;
import org.danteplanner.backend.planner.validation.startgift.StartGiftReferenceData;

import java.util.Optional;

record RegistryReferences(GameDataRegistry.Snapshot data, SinnerIdValidator sinnerIds)
        implements IdentityReferenceData, EgoReferenceData, EgoGiftReferenceData, StartGiftReferenceData {

    @Override
    public boolean hasIdentity(String identityId) {
        return data.hasIdentity(identityId);
    }

    @Override
    public boolean hasEgo(String egoId) {
        return data.hasEgo(egoId);
    }

    @Override
    public boolean belongsToSinner(String sinnerKey, String entityId) {
        return sinnerIds.validateMatch(sinnerKey, entityId);
    }

    @Override
    public Optional<Integer> maxThreadspin(String egoId) {
        return switch (data.maxThreadspinOf(egoId)) {
            case GameDataRegistry.Lookup.Known<Integer> known -> Optional.of(known.value());
            case GameDataRegistry.Lookup.Unknown<Integer> unknown -> Optional.empty();
        };
    }

    @Override
    public boolean hasEgoGift(String giftId) {
        return data.hasEgoGift(giftId);
    }
}
