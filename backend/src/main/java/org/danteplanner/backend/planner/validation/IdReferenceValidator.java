package org.danteplanner.backend.planner.validation;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import org.danteplanner.backend.shared.util.GameConstants;
import org.springframework.stereotype.Component;

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

        context.reject("identity", p -> ValidationErrors.unknownId(ErrorCode.UNKNOWN_IDENTITY, p, identityId));
        return false;
    }

    private boolean validateIdentityBelongsToSinner(String sinnerKey, String identityId,
                                                    ValidationContext context) {
        if (sinnerIdValidator.validateMatch(sinnerKey, identityId)) {
            return true;
        }

        context.reject("identity for sinner " + sinnerKey,
                p -> ValidationErrors.unknownId(ErrorCode.MISMATCHED_IDENTITY, p, identityId));
        return false;
    }

    private void validateIdentityLevelAndUptie(String sinnerKey, JsonNode identity, ValidationContext context) {
        String identityPath = "equipment[" + sinnerKey + "].identity";
        requireInRange(identity, identityPath, "level", ErrorCode.OUT_OF_RANGE_IDENTITY_LEVEL,
                GameConstants.MIN_LEVEL, GameConstants.MAX_LEVEL, context);
        requireInRange(identity, identityPath, "uptie", ErrorCode.OUT_OF_RANGE_IDENTITY_UPTIE,
                GameConstants.MIN_UPTIE, GameConstants.MAX_UPTIE, context);
    }

    private void requireInRange(JsonNode owner, String ownerPath, String field, ErrorCode code, int min, int max,
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
            context.reject(ownerPath + "." + field, p -> ValidationErrors.valueOutOfRange(code, field, value, min, max));
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

        context.reject("EGO", p -> ValidationErrors.unknownId(ErrorCode.UNKNOWN_EGO, p, egoId));
        return false;
    }

    private boolean validateEgoBelongsToSinner(String sinnerKey, String egoId, ValidationContext context) {
        if (sinnerIdValidator.validateMatch(sinnerKey, egoId)) {
            return true;
        }

        context.reject("EGO for sinner " + sinnerKey, p -> ValidationErrors.unknownId(ErrorCode.MISMATCHED_EGO, p, egoId));
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

        context.reject(threadspinPath, p -> ValidationErrors.valueOutOfRange(ErrorCode.OUT_OF_RANGE_EGO_THREADSPIN,
                "threadspin", threadspin, GameConstants.MIN_THREADSPIN, GameConstants.MAX_THREADSPIN));
        return false;
    }

    private void validateThreadspinUnderEgoCeiling(String threadspinPath, int threadspin, String egoId,
                                                   ValidationContext context) {
        Integer egoMax = gameDataRegistry.getEgoMaxThreadspin(egoId);
        if (egoMax == null) {
            context.reject("EGO maxThreadspin",
                    p -> ValidationErrors.unknownId(ErrorCode.MISSING_EGO_THREADSPIN_CEILING, p, egoId));
            return;
        }

        if (threadspin > egoMax) {
            context.reject(threadspinPath, p -> ValidationErrors.valueOutOfRange(ErrorCode.OUT_OF_RANGE_EGO_THREADSPIN,
                    "threadspin for EGO " + egoId, threadspin, GameConstants.MIN_THREADSPIN, egoMax));
        }
    }

    void validateGiftIds(JsonNode root, ValidationContext context) {
        validateGiftIdArray(root, "selectedGiftIds", ErrorCode.DUPLICATE_STARTGIFT, context);
        validateGiftIdArray(root, "observationGiftIds", ErrorCode.DUPLICATE_EGOGIFT, context);
        validateGiftIdArray(root, "comprehensiveGiftIds", ErrorCode.DUPLICATE_EGOGIFT, context);
    }

    private void validateGiftIdArray(JsonNode root, String fieldName, ErrorCode duplicateCode,
                                     ValidationContext context) {
        eachUniqueString(arrayField(root, fieldName), fieldName, duplicateCode, context, (giftId, index) -> {
            if (!gameDataRegistry.hasEgoGift(giftId)) {
                context.reject(fieldName, p -> ValidationErrors.unknownId(ErrorCode.UNKNOWN_EGOGIFT, p, giftId));
            }
        });
    }
}
