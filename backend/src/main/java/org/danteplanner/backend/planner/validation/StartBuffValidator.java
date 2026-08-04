package org.danteplanner.backend.planner.validation;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.Set;

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
        JsonNode buffIds = root.get("selectedBuffIds");
        if (buffIds == null || !buffIds.isArray()) {
            return;
        }

        if (buffIds.size() > MAX_START_BUFFS) {
            context.reject("selectedBuffIds count",
                    p -> ValidationErrors.valueOutOfRange(p, buffIds.size(), 0, MAX_START_BUFFS));
            return;
        }

        Set<Integer> seenBaseIds = new HashSet<>();

        for (int i = 0; i < buffIds.size(); i++) {
            JsonNode node = buffIds.get(i);
            if (!node.isNumber()) {
                context.reject("selectedBuffIds[" + i + "]",
                        p -> ValidationErrors.invalidFieldType(p, "number", node));
                continue;
            }

            int buffId = node.asInt();

            if (!gameDataRegistry.hasStartBuff(String.valueOf(buffId))) {
                context.reject("selectedBuffIds",
                        p -> ValidationErrors.invalidIdReference(p, String.valueOf(buffId)));
                continue;
            }

            int baseId = buffId % 100;
            if (baseId < MIN_BUFF_BASE_ID || baseId > MAX_BUFF_BASE_ID) {
                context.reject("selectedBuffIds[" + i + "] base ID",
                        p -> ValidationErrors.valueOutOfRange(p, baseId, MIN_BUFF_BASE_ID, MAX_BUFF_BASE_ID));
                continue;
            }

            if (!seenBaseIds.add(baseId)) {
                context.reject("selectedBuffIds base IDs",
                        p -> ValidationErrors.duplicateValue(p, String.valueOf(baseId)));
            }
        }
    }

    void validateStartGiftIds(JsonNode root, ValidationContext context) {
        JsonNode keywordNode = root.get("selectedGiftKeyword");
        JsonNode giftIdsNode = root.get("selectedGiftIds");
        JsonNode giftIds = (giftIdsNode != null && giftIdsNode.isArray()) ? giftIdsNode : null;

        if (keywordNode == null || !keywordNode.isTextual()) {
            if (giftIds != null && !giftIds.isEmpty()) {
                context.reject("selectedGiftIds",
                        p -> ValidationErrors.invalidSequence(p + " requires selectedGiftKeyword"));
            }
            return;
        }

        String keyword = keywordNode.asText();

        if (!gameDataRegistry.hasStartGiftKeyword(keyword)) {
            context.reject("selectedGiftKeyword", p -> ValidationErrors.invalidIdReference(p, keyword));
            return;
        }

        Set<String> pool = gameDataRegistry.getStartGiftPool(keyword);
        if (giftIds == null) {
            return;
        }

        Set<String> seenGiftIds = new HashSet<>();

        for (int i = 0; i < giftIds.size(); i++) {
            JsonNode node = giftIds.get(i);
            if (!node.isTextual()) {
                context.reject("selectedGiftIds[" + i + "]",
                        p -> ValidationErrors.invalidFieldType(p, "string", node));
                continue;
            }

            String giftId = node.asText();

            if (!seenGiftIds.add(giftId)) {
                context.reject("selectedGiftIds", p -> ValidationErrors.duplicateValue(p, giftId));
                continue;
            }

            if (!pool.contains(giftId)) {
                context.reject("selectedGiftIds (not in keyword '" + keyword + "' pool)",
                        p -> ValidationErrors.invalidIdReference(p, giftId));
            }
        }
    }
}
