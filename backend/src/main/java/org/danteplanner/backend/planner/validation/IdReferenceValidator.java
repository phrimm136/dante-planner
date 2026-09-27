package org.danteplanner.backend.planner.validation;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import org.danteplanner.backend.planner.entity.MDCategory;
import org.danteplanner.backend.shared.util.GameConstants;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.function.IntFunction;

import static org.danteplanner.backend.planner.validation.JsonTraversal.arrayField;
import static org.danteplanner.backend.planner.validation.JsonTraversal.eachObject;
import static org.danteplanner.backend.planner.validation.JsonTraversal.eachObjectProperty;
import static org.danteplanner.backend.planner.validation.JsonTraversal.eachUniqueString;
import static org.danteplanner.backend.planner.validation.JsonTraversal.isInt;

@Component
@RequiredArgsConstructor
class IdReferenceValidator {

    private record DifficultyRule(int min, int max) {}

    private record FloorRules(int floorCount, IntFunction<DifficultyRule> difficultyAt) {}

    private static final DifficultyRule NORMAL_OR_HARD = new DifficultyRule(0, 1);
    private static final DifficultyRule HARD = new DifficultyRule(1, 1);
    private static final DifficultyRule EXTREME = new DifficultyRule(3, 3);

    private static final Map<MDCategory, FloorRules> FLOOR_RULES;

    static {
        Map<MDCategory, FloorRules> byCategory = new EnumMap<>(MDCategory.class);
        byCategory.put(MDCategory.F5, new FloorRules(5, floor -> NORMAL_OR_HARD));
        byCategory.put(MDCategory.F10, new FloorRules(10, floor -> HARD));
        byCategory.put(MDCategory.F15, new FloorRules(15, floor -> floor < 10 ? HARD : EXTREME));

        List<MDCategory> uncovered = Arrays.stream(MDCategory.values())
                .filter(category -> !byCategory.containsKey(category))
                .toList();
        if (!uncovered.isEmpty()) {
            throw new ExceptionInInitializerError("No floor rules for MD category(s): " + uncovered);
        }
        FLOOR_RULES = Map.copyOf(byCategory);
    }

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

    void validateFloorSelectionIds(JsonNode root, String category, ValidationContext context) {
        JsonNode floorSelections = arrayField(root, "floorSelections");
        FloorRules rules = FLOOR_RULES.get(MDCategory.fromValue(category));

        eachObject(floorSelections, rules.floorCount(), (floor, index) -> {
            String floorPath = "floorSelections[" + index + "]";
            JsonNode themePackNode = floor.path("themePackId");
            boolean themePackChosen = themePackNode.isTextual();

            if (!validateThemePackPresence(floorPath, themePackNode, context)) {
                return;
            }

            validateDifficultyRange(floorPath, floor, rules.difficultyAt().apply(index), context);
            validateThemePackSequence(floorPath, floorSelections, index, themePackChosen, context);
            validateFloorGiftIds(floorPath, floor, themePackChosen ? themePackNode.asText() : null, context);
        });
    }

    private boolean validateThemePackPresence(String floorPath, JsonNode themePackNode, ValidationContext context) {
        String themePackId = themePackNode.isTextual() ? themePackNode.asText() : "";

        if (themePackId.isEmpty()) {
            if (!context.policy().requiresPublishableContent()) {
                return true;
            }
            context.reject(floorPath, ValidationErrors::floorMissingThemePack);
            return false;
        }

        if (gameDataRegistry.hasThemePack(themePackId)) {
            return true;
        }

        context.reject(floorPath + ".themePackId",
                p -> ValidationErrors.unknownId(ErrorCode.THEME_PACK_UNKNOWN_ID, p, themePackId));
        return false;
    }

    private void validateDifficultyRange(String floorPath, JsonNode floor, DifficultyRule expected,
                                         ValidationContext context) {
        if (!context.policy().requiresPublishableContent()) {
            return;
        }

        JsonNode difficultyNode = floor.path("difficulty");
        int difficulty = isInt(difficultyNode) ? difficultyNode.asInt() : -1;

        if (difficulty < expected.min() || difficulty > expected.max()) {
            context.reject(floorPath + ".difficulty",
                    p -> ValidationErrors.valueOutOfRange(p, difficulty, expected.min(), expected.max()));
        }
    }

    private void validateThemePackSequence(String floorPath, JsonNode floorSelections, int index,
                                           boolean themePackChosen, ValidationContext context) {
        if (!themePackChosen || index == 0) {
            return;
        }

        int previousIndex = index - 1;
        JsonNode previousFloor = floorSelections.get(previousIndex);
        if (!previousFloor.isObject()) {
            return;
        }

        JsonNode previousThemePackNode = previousFloor.path("themePackId");
        boolean previousChosen = previousThemePackNode.isTextual()
                && !previousThemePackNode.asText().isEmpty();
        if (!previousChosen) {
            context.reject(floorPath, p -> ValidationErrors.invalidSequence(
                    p + " requires themePackId in floorSelections[" + previousIndex + "]"));
        }
    }

    private void validateFloorGiftIds(String floorPath, JsonNode floor, String themePackId,
                                      ValidationContext context) {
        String giftsPath = floorPath + ".giftIds";

        eachUniqueString(arrayField(floor, "giftIds"), giftsPath, context, (giftId, index) -> {
            if (!validateGiftIsKnown(giftsPath, giftId, context)) {
                return;
            }

            validateGiftIsAffordable(giftsPath, index, giftId, themePackId, context);
        });
    }

    private boolean validateGiftIsKnown(String giftsPath, String giftId, ValidationContext context) {
        if (gameDataRegistry.hasEgoGift(giftId)) {
            return true;
        }

        context.reject(giftsPath, p -> ValidationErrors.unknownId(ErrorCode.FLOOR_UNKNOWN_GIFT_ID, p, giftId));
        return false;
    }

    private void validateGiftIsAffordable(String giftsPath, int index, String giftId, String themePackId,
                                          ValidationContext context) {
        if (themePackId == null) {
            return;
        }

        if (!gameDataRegistry.isGiftAffordableForThemePack(giftId, themePackId)) {
            context.reject(giftsPath + "[" + index + "]",
                    p -> ValidationErrors.giftNotAffordable(giftId, themePackId));
        }
    }
}
