package org.danteplanner.backend.validation;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import org.danteplanner.backend.util.GameConstants;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Validates that equipment, EGO, gift and floor-selection IDs exist in game
 * data and are consistent with their sinner keys.
 *
 * <p>Mixes two failure modes, preserved from the original validator: hard
 * out-of-range values for level/uptie/threadspin (global ceilings) throw and
 * abort, while ID-lookup, sinner-match and per-EGO ceiling failures accumulate.
 */
@Component
@Slf4j
class IdReferenceValidator {

    private final GameDataRegistry gameDataRegistry;
    private final SinnerIdValidator sinnerIdValidator;

    IdReferenceValidator(GameDataRegistry gameDataRegistry, SinnerIdValidator sinnerIdValidator) {
        this.gameDataRegistry = gameDataRegistry;
        this.sinnerIdValidator = sinnerIdValidator;
    }

    void validateEquipmentIds(JsonNode root, ValidationContext context) {
        JsonNode equipment = root.get("equipment");
        if (equipment == null || !equipment.isObject()) {
            return;
        }

        for (Map.Entry<String, JsonNode> entry : equipment.properties()) {
            String sinnerKey = entry.getKey();
            JsonNode sinnerEquipment = entry.getValue();

            validateIdentityId(sinnerKey, sinnerEquipment, context);
            validateEgoIds(sinnerKey, sinnerEquipment, context);
        }
    }

    private void validateIdentityId(String sinnerKey, JsonNode sinnerEquipment, ValidationContext context) {
        JsonNode identity = sinnerEquipment.get("identity");
        if (identity == null || !identity.isObject()) {
            return;
        }

        JsonNode idNode = identity.get("id");
        if (idNode == null || !idNode.isTextual()) {
            return;
        }

        String identityId = idNode.asText();

        if (!gameDataRegistry.hasIdentity(identityId)) {
            log.warn("Validation failed: identity ID '{}' not found in game data", identityId);
            context.addError(ValidationErrors.invalidIdReference("identity", identityId));
            return;
        }

        if (!sinnerIdValidator.validateMatch(sinnerKey, identityId)) {
            log.warn("Validation failed: identity ID '{}' does not match sinner key '{}'", identityId, sinnerKey);
            context.addError(ValidationErrors.invalidIdReference("identity for sinner " + sinnerKey, identityId));
            return;
        }

        JsonNode levelNode = identity.get("level");
        if (levelNode != null && levelNode.isNumber()) {
            int level = levelNode.asInt();
            if (level < GameConstants.MIN_LEVEL || level > GameConstants.MAX_LEVEL) {
                log.warn("Validation failed: equipment[{}].identity.level {} out of range [{}-{}]",
                        sinnerKey, level, GameConstants.MIN_LEVEL, GameConstants.MAX_LEVEL);
                throw ValidationErrors.valueOutOfRange("level", level, GameConstants.MIN_LEVEL, GameConstants.MAX_LEVEL);
            }
        }

        JsonNode uptieNode = identity.get("uptie");
        if (uptieNode != null && uptieNode.isNumber()) {
            int uptie = uptieNode.asInt();
            if (uptie < GameConstants.MIN_UPTIE || uptie > GameConstants.MAX_UPTIE) {
                log.warn("Validation failed: equipment[{}].identity.uptie {} out of range [{}-{}]",
                        sinnerKey, uptie, GameConstants.MIN_UPTIE, GameConstants.MAX_UPTIE);
                throw ValidationErrors.valueOutOfRange("uptie", uptie, GameConstants.MIN_UPTIE, GameConstants.MAX_UPTIE);
            }
        }
    }

    private void validateEgoIds(String sinnerKey, JsonNode sinnerEquipment, ValidationContext context) {
        JsonNode egos = sinnerEquipment.get("egos");
        if (egos == null || !egos.isObject()) {
            return;
        }

        for (Map.Entry<String, JsonNode> egoEntry : egos.properties()) {
            String egoType = egoEntry.getKey();
            JsonNode ego = egoEntry.getValue();
            if (ego == null || !ego.isObject()) {
                continue;
            }

            JsonNode idNode = ego.get("id");
            if (idNode == null || !idNode.isTextual()) {
                continue;
            }

            String egoId = idNode.asText();

            if (!gameDataRegistry.hasEgo(egoId)) {
                log.warn("Validation failed: EGO ID '{}' not found in game data", egoId);
                context.addError(ValidationErrors.invalidIdReference("EGO", egoId));
                continue;
            }

            if (!sinnerIdValidator.validateMatch(sinnerKey, egoId)) {
                log.warn("Validation failed: EGO ID '{}' does not match sinner key '{}'", egoId, sinnerKey);
                context.addError(ValidationErrors.invalidIdReference("EGO for sinner " + sinnerKey, egoId));
                continue;
            }

            JsonNode threadspinNode = ego.get("threadspin");
            if (threadspinNode != null && threadspinNode.isNumber()) {
                int threadspin = threadspinNode.asInt();
                if (threadspin < GameConstants.MIN_THREADSPIN || threadspin > GameConstants.MAX_THREADSPIN) {
                    log.warn("Validation failed: equipment[{}].egos.{}.threadspin {} out of range [{}-{}]",
                            sinnerKey, egoType, threadspin, GameConstants.MIN_THREADSPIN, GameConstants.MAX_THREADSPIN);
                    throw ValidationErrors.valueOutOfRange("threadspin", threadspin, GameConstants.MIN_THREADSPIN, GameConstants.MAX_THREADSPIN);
                }

                Integer egoMax = gameDataRegistry.getEgoMaxThreadspin(egoId);
                if (egoMax == null) {
                    log.warn("Validation failed: EGO ID '{}' has no maxThreadspin in registry", egoId);
                    context.addError(ValidationErrors.invalidIdReference("EGO maxThreadspin", egoId));
                    continue;
                }
                if (threadspin > egoMax) {
                    log.warn("Validation failed: equipment[{}].egos.{}.threadspin {} exceeds EGO {} max {}",
                            sinnerKey, egoType, threadspin, egoId, egoMax);
                    context.addError(ValidationErrors.valueOutOfRange("threadspin for EGO " + egoId, threadspin, GameConstants.MIN_THREADSPIN, egoMax));
                    continue;
                }
            }
        }
    }

    void validateGiftIds(JsonNode root, ValidationContext context) {
        validateGiftIdArray(root, "selectedGiftIds", context);
        validateGiftIdArray(root, "observationGiftIds", context);
        validateGiftIdArray(root, "comprehensiveGiftIds", context);
    }

    private void validateGiftIdArray(JsonNode root, String fieldName, ValidationContext context) {
        JsonNode array = root.get(fieldName);
        if (array == null || !array.isArray()) {
            return;
        }

        Set<String> seenGiftIds = new HashSet<>();

        for (int i = 0; i < array.size(); i++) {
            JsonNode node = array.get(i);

            if (!node.isTextual()) {
                log.warn("Validation failed: {}[{}] is not a string", fieldName, i);
                context.addError(ValidationErrors.invalidFieldType(fieldName + "[" + i + "]", "string", node));
                continue;
            }

            String giftId = node.asText();

            if (!seenGiftIds.add(giftId)) {
                log.warn("Validation failed: {}[{}] has duplicate gift ID '{}'", fieldName, i, giftId);
                context.addError(ValidationErrors.duplicateValue(fieldName, giftId));
                continue;
            }

            if (!gameDataRegistry.hasEgoGift(giftId)) {
                log.warn("Validation failed: {}[{}] gift ID '{}' not found in game data", fieldName, i, giftId);
                context.addError(ValidationErrors.invalidIdReference(fieldName, giftId));
            }
        }
    }

    void validateFloorSelectionIds(JsonNode root, String category, ValidationContext context) {
        JsonNode floorSelections = root.get("floorSelections");
        if (floorSelections == null || !floorSelections.isArray()) {
            return;
        }

        int floorCount = switch (category) {
            case "5F" -> 5;
            case "10F" -> 10;
            case "15F" -> 15;
            default -> 15;
        };

        for (int i = 0; i < floorSelections.size(); i++) {
            JsonNode floor = floorSelections.get(i);
            if (!floor.isObject()) {
                continue;
            }

            if (i >= floorCount) {
                continue;
            }

            JsonNode themePackNode = floor.get("themePackId");
            boolean hasThemePack = themePackNode != null && !themePackNode.isNull() && themePackNode.isTextual();

            if (context.isStrictMode()) {
                if (!hasThemePack) {
                    log.warn("Validation failed: floorSelections[{}] must have a themePackId for publish", i);
                    context.addError(ValidationErrors.missingRequiredField(Set.of("floorSelections[" + i + "].themePackId")));
                    continue;
                }

                String themePackId = themePackNode.asText();
                if (themePackId.isEmpty() || !gameDataRegistry.hasThemePack(themePackId)) {
                    log.warn("Validation failed: floorSelections[{}] themePackId '{}' not found", i, themePackId);
                    context.addError(ValidationErrors.invalidIdReference("floorSelections[" + i + "].themePackId", themePackId));
                    continue;
                }

                JsonNode difficultyNode = floor.get("difficulty");
                int difficulty = (difficultyNode != null && difficultyNode.isNumber()) ? difficultyNode.asInt() : -1;

                switch (category) {
                    case "5F" -> {
                        if (difficulty != 0 && difficulty != 1) {
                            log.warn("Validation failed: floorSelections[{}] must be Normal or Hard for 5F category", i);
                            context.addError(ValidationErrors.valueOutOfRange("floorSelections[" + i + "].difficulty", difficulty, 0, 1));
                        }
                    }
                    case "10F" -> {
                        if (difficulty != 1) {
                            log.warn("Validation failed: floorSelections[{}] must be Hard for 10F category", i);
                            context.addError(ValidationErrors.valueOutOfRange("floorSelections[" + i + "].difficulty", difficulty, 1, 1));
                        }
                    }
                    case "15F" -> {
                        if (i < 10 && difficulty != 1) {
                            log.warn("Validation failed: floorSelections[{}] must be Hard for 15F category", i);
                            context.addError(ValidationErrors.valueOutOfRange("floorSelections[" + i + "].difficulty", difficulty, 1, 1));
                        }
                        if (i >= 10 && difficulty != 3) {
                            log.warn("Validation failed: floorSelections[{}] must be Extreme for 15F category", i);
                            context.addError(ValidationErrors.valueOutOfRange("floorSelections[" + i + "].difficulty", difficulty, 3, 3));
                        }
                    }
                }
            } else if (hasThemePack) {
                String themePackId = themePackNode.asText();
                if (!themePackId.isEmpty() && !gameDataRegistry.hasThemePack(themePackId)) {
                    log.warn("Validation failed: floorSelections[{}] themePackId '{}' not found", i, themePackId);
                    context.addError(ValidationErrors.invalidIdReference("floorSelections[" + i + "].themePackId", themePackId));
                    continue;
                }
            }

            if (hasThemePack && i > 0) {
                JsonNode previousFloor = floorSelections.get(i - 1);
                if (previousFloor.isObject()) {
                    JsonNode previousThemePackNode = previousFloor.get("themePackId");
                    boolean previousHasThemePack = previousThemePackNode != null && !previousThemePackNode.isNull()
                            && previousThemePackNode.isTextual() && !previousThemePackNode.asText().isEmpty();
                    if (!previousHasThemePack) {
                        log.warn("Validation failed: floorSelections[{}] cannot have themePackId because floor {} is missing one", i, i - 1);
                        context.addError(ValidationErrors.invalidSequence("floorSelections[" + i + "] requires themePackId in floorSelections[" + (i - 1) + "]"));
                    }
                }
            }

            JsonNode giftIds = floor.get("giftIds");
            if (giftIds != null && giftIds.isArray()) {
                Set<String> seenFloorGiftIds = new HashSet<>();

                for (int j = 0; j < giftIds.size(); j++) {
                    JsonNode giftNode = giftIds.get(j);

                    if (!giftNode.isTextual()) {
                        log.warn("Validation failed: floorSelections[{}].giftIds[{}] is not a string", i, j);
                        context.addError(ValidationErrors.invalidFieldType("floorSelections[" + i + "].giftIds[" + j + "]", "string", giftNode));
                        continue;
                    }

                    String giftId = giftNode.asText();

                    if (!seenFloorGiftIds.add(giftId)) {
                        log.warn("Validation failed: floorSelections[{}].giftIds has duplicate '{}'", i, giftId);
                        context.addError(ValidationErrors.duplicateValue("floorSelections[" + i + "].giftIds", giftId));
                        continue;
                    }

                    if (!gameDataRegistry.hasEgoGift(giftId)) {
                        log.warn("Validation failed: floorSelections[{}].giftIds[{}] '{}' not found", i, j, giftId);
                        context.addError(ValidationErrors.invalidIdReference("floorSelections[" + i + "].giftIds", giftId));
                        continue;
                    }

                    String themePackId = themePackNode.asText();
                    if (!gameDataRegistry.isGiftAffordableForThemePack(giftId, themePackId)) {
                        log.warn("Validation failed: floorSelections[{}].giftIds[{}] '{}' not affordable for theme pack '{}'", i, j, giftId, themePackId);
                        context.addError(ValidationErrors.giftNotAffordable(giftId, themePackId));
                    }
                }
            }
        }
    }
}
