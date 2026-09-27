package org.danteplanner.backend.planner.validation;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import org.danteplanner.backend.planner.exception.PlannerValidationException;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.Set;

import static org.danteplanner.backend.planner.validation.JsonTraversal.arrayField;
import static org.danteplanner.backend.planner.validation.JsonTraversal.eachNumber;
import static org.danteplanner.backend.planner.validation.JsonTraversal.eachUniqueString;

@Component
@RequiredArgsConstructor
class StartBuffValidator {

    private static final int MAX_START_BUFFS = 10;
    private static final int MIN_BUFF_BASE_ID = 0;
    private static final int MAX_BUFF_BASE_ID = 9;

    private final GameDataRegistry gameDataRegistry;

    boolean validateSeasonExists(int version, ValidationContext context) {
        if (gameDataRegistry.hasSeason(version)) {
            return true;
        }

        context.reject("content version", p -> new PlannerValidationException(
                ErrorCode.UNKNOWN_CONTENT_VERSION.getCode(),
                "No game data for content version " + version));
        return false;
    }

    void validateStartBuffIds(JsonNode root, int version, ValidationContext context) {
        JsonNode buffIds = arrayField(root, "selectedBuffIds");

        if (buffIds.size() > MAX_START_BUFFS) {
            context.reject("selectedBuffIds count",
                    p -> ValidationErrors.valueOutOfRange(p, buffIds.size(), 0, MAX_START_BUFFS));
            return;
        }

        Set<Integer> seenBaseIds = new HashSet<>();

        eachNumber(buffIds, "selectedBuffIds", context, (buffId, index) -> {
            if (!validateBuffIsKnown(version, buffId, context)) {
                return;
            }

            validateBuffBaseId(buffId, index, seenBaseIds, context);
        });
    }

    private boolean validateBuffIsKnown(int version, int buffId, ValidationContext context) {
        if (gameDataRegistry.hasStartBuff(version, String.valueOf(buffId))) {
            return true;
        }

        context.reject("selectedBuffIds",
                p -> ValidationErrors.unknownId(ErrorCode.START_BUFF_UNKNOWN_ID, p, String.valueOf(buffId)));
        return false;
    }

    private void validateBuffBaseId(int buffId, int index, Set<Integer> seenBaseIds, ValidationContext context) {
        int baseId = buffId % 100;

        if (baseId < MIN_BUFF_BASE_ID || baseId > MAX_BUFF_BASE_ID) {
            context.reject("selectedBuffIds[" + index + "] base ID",
                    p -> ValidationErrors.valueOutOfRange(p, baseId, MIN_BUFF_BASE_ID, MAX_BUFF_BASE_ID));
            return;
        }

        if (!seenBaseIds.add(baseId)) {
            context.reject("selectedBuffIds base IDs",
                    p -> ValidationErrors.duplicateValue(p, String.valueOf(baseId)));
        }
    }

    void validateStartGiftIds(JsonNode root, int version, ValidationContext context) {
        JsonNode keywordNode = root.path("selectedGiftKeyword");
        JsonNode giftIds = arrayField(root, "selectedGiftIds");

        if (!keywordNode.isTextual()) {
            validateGiftsWaitForTheirKeyword(giftIds, context);
            return;
        }

        String keyword = keywordNode.asText();

        if (!validateKeywordIsKnown(version, keyword, context)) {
            return;
        }

        validateGiftsAreInKeywordPool(version, keyword, giftIds, context);
    }

    private void validateGiftsWaitForTheirKeyword(JsonNode giftIds, ValidationContext context) {
        if (giftIds.isEmpty()) {
            return;
        }

        context.reject("selectedGiftIds",
                p -> ValidationErrors.invalidSequence(p + " requires selectedGiftKeyword"));
    }

    private boolean validateKeywordIsKnown(int version, String keyword, ValidationContext context) {
        if (gameDataRegistry.hasStartGiftKeyword(version, keyword)) {
            return true;
        }

        context.reject("selectedGiftKeyword", p -> ValidationErrors.invalidIdReference(p, keyword));
        return false;
    }

    private void validateGiftsAreInKeywordPool(int version, String keyword, JsonNode giftIds,
                                               ValidationContext context) {
        Set<String> pool = gameDataRegistry.getStartGiftPool(version, keyword);

        eachUniqueString(giftIds, "selectedGiftIds", context, (giftId, index) -> {
            if (!pool.contains(giftId)) {
                context.reject("selectedGiftIds (not in keyword '" + keyword + "' pool)",
                        p -> ValidationErrors.invalidIdReference(p, giftId));
            }
        });
    }
}
