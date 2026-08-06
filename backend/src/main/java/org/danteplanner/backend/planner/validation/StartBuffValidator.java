package org.danteplanner.backend.planner.validation;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.Set;

import static org.danteplanner.backend.planner.validation.JsonTraversal.arrayField;
import static org.danteplanner.backend.planner.validation.JsonTraversal.eachNumber;
import static org.danteplanner.backend.planner.validation.JsonTraversal.eachUniqueString;

/**
 * Validates start-buff and start-gift selections against game data.
 *
 * <p>Start buffs: max 10, each ID must exist, base IDs (00-09) unique.
 * Start gifts: keyword-gated, each gift must belong to the keyword's pool.
 */
@Component
@RequiredArgsConstructor
class StartBuffValidator {

    private static final int MAX_START_BUFFS = 10;
    private static final int MIN_BUFF_BASE_ID = 0;
    private static final int MAX_BUFF_BASE_ID = 9;

    private final GameDataRegistry gameDataRegistry;

    void validateStartBuffIds(JsonNode root, ValidationContext context) {
        JsonNode buffIds = arrayField(root, "selectedBuffIds");

        if (buffIds.size() > MAX_START_BUFFS) {
            context.reject("selectedBuffIds count",
                    p -> ValidationErrors.valueOutOfRange(p, buffIds.size(), 0, MAX_START_BUFFS));
            return;
        }

        Set<Integer> seenBaseIds = new HashSet<>();

        eachNumber(buffIds, "selectedBuffIds", context, (buffId, index) -> {
            if (!validateBuffIsKnown(buffId, context)) {
                return;
            }

            validateBuffBaseId(buffId, index, seenBaseIds, context);
        });
    }

    /**
     * @return false when the buff is not in game data, so the base id it would carry is a guess
     */
    private boolean validateBuffIsKnown(int buffId, ValidationContext context) {
        if (gameDataRegistry.hasStartBuff(String.valueOf(buffId))) {
            return true;
        }

        context.reject("selectedBuffIds", p -> ValidationErrors.invalidIdReference(p, String.valueOf(buffId)));
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

    void validateStartGiftIds(JsonNode root, ValidationContext context) {
        JsonNode keywordNode = root.path("selectedGiftKeyword");
        JsonNode giftIds = arrayField(root, "selectedGiftIds");

        if (!keywordNode.isTextual()) {
            validateGiftsWaitForTheirKeyword(giftIds, context);
            return;
        }

        String keyword = keywordNode.asText();

        if (!validateKeywordIsKnown(keyword, context)) {
            return;
        }

        validateGiftsAreInKeywordPool(keyword, giftIds, context);
    }

    private void validateGiftsWaitForTheirKeyword(JsonNode giftIds, ValidationContext context) {
        if (giftIds.isEmpty()) {
            return;
        }

        context.reject("selectedGiftIds",
                p -> ValidationErrors.invalidSequence(p + " requires selectedGiftKeyword"));
    }

    /**
     * @return false when the keyword is not in game data, so it names no pool to check gifts against
     */
    private boolean validateKeywordIsKnown(String keyword, ValidationContext context) {
        if (gameDataRegistry.hasStartGiftKeyword(keyword)) {
            return true;
        }

        context.reject("selectedGiftKeyword", p -> ValidationErrors.invalidIdReference(p, keyword));
        return false;
    }

    private void validateGiftsAreInKeywordPool(String keyword, JsonNode giftIds, ValidationContext context) {
        Set<String> pool = gameDataRegistry.getStartGiftPool(keyword);

        eachUniqueString(giftIds, "selectedGiftIds", context, (giftId, index) -> {
            if (!pool.contains(giftId)) {
                context.reject("selectedGiftIds (not in keyword '" + keyword + "' pool)",
                        p -> ValidationErrors.invalidIdReference(p, giftId));
            }
        });
    }
}
