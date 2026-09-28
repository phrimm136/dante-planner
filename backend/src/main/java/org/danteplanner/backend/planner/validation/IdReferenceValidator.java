package org.danteplanner.backend.planner.validation;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import org.danteplanner.backend.planner.floor.FloorSelection;
import org.danteplanner.backend.planner.floor.ThemePack;
import org.danteplanner.backend.shared.util.GameConstants;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.danteplanner.backend.planner.validation.JsonTraversal.arrayField;
import static org.danteplanner.backend.planner.validation.JsonTraversal.eachObjectProperty;
import static org.danteplanner.backend.planner.validation.JsonTraversal.eachUniqueString;
import static org.danteplanner.backend.planner.validation.JsonTraversal.isInt;

@Component
@RequiredArgsConstructor
class IdReferenceValidator {

    private final GameDataRegistry gameDataRegistry;
    private final SinnerIdValidator sinnerIdValidator;

    void validateEquipmentIds(JsonNode root, ValidationContext context) {
        eachObjectProperty(root.path("equipment"), (sinnerKey, sinnerEquipment) -> {
            validateIdentityId(sinnerKey, sinnerEquipment, context);
            validateEgoIds(sinnerKey, sinnerEquipment, context);
        });
    }

    private void validateIdentityId(String sinnerKey, JsonNode sinnerEquipment, ValidationContext context) {
        JsonNode identity = sinnerEquipment.path("identity");
        JsonNode idNode = identity.path("id");
        if (!idNode.isTextual()) {
            return;
        }

        String identityId = idNode.asText();

        if (!validateIdentityIsKnown(identityId, context)) {
            return;
        }

        if (!validateIdentityBelongsToSinner(sinnerKey, identityId, context)) {
            return;
        }

        validateIdentityLevelAndUptie(sinnerKey, identity, context);
    }

    private boolean validateIdentityIsKnown(String identityId, ValidationContext context) {
        if (gameDataRegistry.hasIdentity(identityId)) {
            return true;
        }

        context.reject("identity", p -> ValidationErrors.unknownId(ErrorCode.IDENTITY_UNKNOWN_ID, p, identityId));
        return false;
    }

    private boolean validateIdentityBelongsToSinner(String sinnerKey, String identityId,
                                                    ValidationContext context) {
        if (sinnerIdValidator.validateMatch(sinnerKey, identityId)) {
            return true;
        }

        context.reject("identity for sinner " + sinnerKey,
                p -> ValidationErrors.invalidIdReference(p, identityId));
        return false;
    }

    private void validateIdentityLevelAndUptie(String sinnerKey, JsonNode identity, ValidationContext context) {
        String identityPath = "equipment[" + sinnerKey + "].identity";
        requireInRange(identity, identityPath, "level", GameConstants.MIN_LEVEL, GameConstants.MAX_LEVEL, context);
        requireInRange(identity, identityPath, "uptie", GameConstants.MIN_UPTIE, GameConstants.MAX_UPTIE, context);
    }

    private void requireInRange(JsonNode owner, String ownerPath, String field, int min, int max,
                                ValidationContext context) {
        JsonNode node = owner.path(field);
        if (node.isMissingNode()) {
            return;
        }
        if (!isInt(node)) {
            context.reject(ownerPath + "." + field, p -> ValidationErrors.invalidFieldType(p, "number", node));
            return;
        }

        int value = node.asInt();
        if (value < min || value > max) {
            context.reject(ownerPath + "." + field, p -> ValidationErrors.valueOutOfRange(field, value, min, max));
        }
    }

    private void validateEgoIds(String sinnerKey, JsonNode sinnerEquipment, ValidationContext context) {
        eachObjectProperty(sinnerEquipment.path("egos"),
                (egoType, ego) -> validateEgo(sinnerKey, egoType, ego, context));
    }

    private void validateEgo(String sinnerKey, String egoType, JsonNode ego, ValidationContext context) {
        JsonNode idNode = ego.path("id");
        if (idNode.isMissingNode() || idNode.isNull()) {
            return;
        }
        if (!idNode.isTextual()) {
            context.reject("equipment." + sinnerKey + ".egos." + egoType + ".id",
                    p -> ValidationErrors.invalidFieldType(p, "string", idNode));
            return;
        }

        String egoId = idNode.asText();

        if (!validateEgoIsKnown(egoId, context)) {
            return;
        }

        if (!validateEgoBelongsToSinner(sinnerKey, egoId, context)) {
            return;
        }

        validateThreadspin(ego, sinnerKey, egoType, egoId, context);
    }

    private boolean validateEgoIsKnown(String egoId, ValidationContext context) {
        if (gameDataRegistry.hasEgo(egoId)) {
            return true;
        }

        context.reject("EGO", p -> ValidationErrors.unknownId(ErrorCode.EGO_UNKNOWN_ID, p, egoId));
        return false;
    }

    private boolean validateEgoBelongsToSinner(String sinnerKey, String egoId, ValidationContext context) {
        if (sinnerIdValidator.validateMatch(sinnerKey, egoId)) {
            return true;
        }

        context.reject("EGO for sinner " + sinnerKey, p -> ValidationErrors.invalidIdReference(p, egoId));
        return false;
    }

    private void validateThreadspin(JsonNode ego, String sinnerKey, String egoType, String egoId,
                                    ValidationContext context) {
        JsonNode threadspinNode = ego.path("threadspin");
        if (threadspinNode.isMissingNode()) {
            return;
        }

        String threadspinPath = "equipment[" + sinnerKey + "].egos." + egoType + ".threadspin";
        if (!isInt(threadspinNode)) {
            context.reject(threadspinPath, p -> ValidationErrors.invalidFieldType(p, "number", threadspinNode));
            return;
        }
        int threadspin = threadspinNode.asInt();

        if (!validateThreadspinInRange(threadspinPath, threadspin, context)) {
            return;
        }

        validateThreadspinUnderEgoCeiling(threadspinPath, threadspin, egoId, context);
    }

    private boolean validateThreadspinInRange(String threadspinPath, int threadspin, ValidationContext context) {
        if (threadspin >= GameConstants.MIN_THREADSPIN && threadspin <= GameConstants.MAX_THREADSPIN) {
            return true;
        }

        context.reject(threadspinPath, p -> ValidationErrors.valueOutOfRange(
                "threadspin", threadspin, GameConstants.MIN_THREADSPIN, GameConstants.MAX_THREADSPIN));
        return false;
    }

    private void validateThreadspinUnderEgoCeiling(String threadspinPath, int threadspin, String egoId,
                                                   ValidationContext context) {
        Integer egoMax = gameDataRegistry.getEgoMaxThreadspin(egoId);
        if (egoMax == null) {
            context.reject("EGO maxThreadspin", p -> ValidationErrors.invalidIdReference(p, egoId));
            return;
        }

        if (threadspin > egoMax) {
            context.reject(threadspinPath, p -> ValidationErrors.valueOutOfRange(
                    "threadspin for EGO " + egoId, threadspin, GameConstants.MIN_THREADSPIN, egoMax));
        }
    }

    void validateGiftIds(JsonNode root, ValidationContext context) {
        validateGiftIdArray(root, "selectedGiftIds", context);
        validateGiftIdArray(root, "observationGiftIds", context);
        validateGiftIdArray(root, "comprehensiveGiftIds", context);
    }

    private void validateGiftIdArray(JsonNode root, String fieldName, ValidationContext context) {
        eachUniqueString(arrayField(root, fieldName), fieldName, context, (giftId, index) -> {
            if (!gameDataRegistry.hasEgoGift(giftId)) {
                context.reject(fieldName, p -> ValidationErrors.unknownId(ErrorCode.GIFT_UNKNOWN_ID, p, giftId));
            }
        });
    }

    void validateFloorIds(List<FloorSelection> floors, ValidationContext context) {
        for (int index = 0; index < floors.size(); index++) {
            FloorSelection floor = floors.get(index);
            String floorPath = "floorSelections[" + index + "]";
            if (floor.themePack() instanceof ThemePack.Chosen(String themePackId)) {
                validateThemePackIsKnown(floorPath, themePackId, context);
            }
            validateFloorGiftIds(floorPath, floor, context);
        }
    }

    private void validateThemePackIsKnown(String floorPath, String themePackId, ValidationContext context) {
        if (!gameDataRegistry.hasThemePack(themePackId)) {
            context.reject(floorPath + ".themePackId",
                    p -> ValidationErrors.unknownId(ErrorCode.THEME_PACK_UNKNOWN_ID, p, themePackId));
        }
    }

    private void validateFloorGiftIds(String floorPath, FloorSelection floor, ValidationContext context) {
        String giftsPath = floorPath + ".giftIds";
        Set<String> seen = new HashSet<>();
        for (int index = 0; index < floor.giftIds().size(); index++) {
            String giftId = floor.giftIds().get(index);
            if (!seen.add(giftId) || !validateGiftIsKnown(giftsPath, giftId, context)) {
                continue;
            }
            if (floor.themePack() instanceof ThemePack.Chosen(String themePackId)) {
                validateGiftIsAffordable(giftsPath + "[" + index + "]", giftId, themePackId, context);
            }
        }
    }

    private boolean validateGiftIsKnown(String giftsPath, String giftId, ValidationContext context) {
        if (gameDataRegistry.hasEgoGift(giftId)) {
            return true;
        }

        context.reject(giftsPath, p -> ValidationErrors.unknownId(ErrorCode.FLOOR_UNKNOWN_GIFT_ID, p, giftId));
        return false;
    }

    private void validateGiftIsAffordable(String giftPath, String giftId, String themePackId,
                                          ValidationContext context) {
        if (!gameDataRegistry.isGiftAffordableForThemePack(giftId, themePackId)) {
            context.reject(giftPath, p -> ValidationErrors.giftNotAffordable(giftId, themePackId));
        }
    }
}
