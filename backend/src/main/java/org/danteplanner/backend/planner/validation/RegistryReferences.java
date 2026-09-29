package org.danteplanner.backend.planner.validation;

import org.danteplanner.backend.planner.gamedata.GameDataRegistry;
import org.danteplanner.backend.planner.validation.ego.EgoReferenceData;
import org.danteplanner.backend.planner.validation.egogift.EgoGiftReferenceData;
import org.danteplanner.backend.planner.validation.identity.IdentityReferenceData;
import org.danteplanner.backend.planner.validation.sinner.SinnerIdValidator;
import org.danteplanner.backend.planner.validation.startgift.StartGiftReferenceData;

import java.util.Optional;

record RegistryReferences(GameDataRegistry registry, SinnerIdValidator sinnerIds)
        implements IdentityReferenceData, EgoReferenceData, EgoGiftReferenceData, StartGiftReferenceData {

    @Override
    public boolean hasIdentity(String identityId) {
        return registry.hasIdentity(identityId);
    }

    @Override
    public boolean hasEgo(String egoId) {
        return registry.hasEgo(egoId);
    }

    @Override
    public boolean belongsToSinner(String sinnerKey, String entityId) {
        return sinnerIds.validateMatch(sinnerKey, entityId);
    }

    @Override
    public Optional<Integer> maxThreadspin(String egoId) {
        return Optional.ofNullable(registry.getEgoMaxThreadspin(egoId));
    }

    @Override
    public boolean hasEgoGift(String giftId) {
        return registry.hasEgoGift(giftId);
    }
}
