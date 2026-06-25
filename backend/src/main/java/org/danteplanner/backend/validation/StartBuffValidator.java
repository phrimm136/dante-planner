package org.danteplanner.backend.validation;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
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
@Slf4j
class StartBuffValidator {

    private static final int MAX_START_BUFFS = 10;
    private static final int MIN_BUFF_BASE_ID = 0;
    private static final int MAX_BUFF_BASE_ID = 9;

    private final GameDataRegistry gameDataRegistry;

    StartBuffValidator(GameDataRegistry gameDataRegistry) {
        this.gameDataRegistry = gameDataRegistry;
    }

    void validateStartBuffIds(JsonNode root, ValidationContext context) {
        JsonNode buffIds = root.get("selectedBuffIds");
        if (buffIds == null || !buffIds.isArray()) {
            return;
        }

        if (buffIds.size() > MAX_START_BUFFS) {
            log.warn("Validation failed: selectedBuffIds has {} items, max is {}",
                    buffIds.size(), MAX_START_BUFFS);
            context.addError(ValidationErrors.valueOutOfRange("selectedBuffIds count", buffIds.size(), 0, MAX_START_BUFFS));
            return;
        }

        Set<Integer> seenBaseIds = new HashSet<>();

        for (int i = 0; i < buffIds.size(); i++) {
            JsonNode node = buffIds.get(i);
            if (!node.isNumber()) {
                log.warn("Validation failed: selectedBuffIds[{}] is not a number", i);
                context.addError(ValidationErrors.invalidFieldType("selectedBuffIds[" + i + "]", "number", node));
                continue;
            }

            int buffId = node.asInt();

            if (!gameDataRegistry.hasStartBuff(String.valueOf(buffId))) {
                log.warn("Validation failed: selectedBuffIds[{}] buff ID '{}' not found in game data", i, buffId);
                context.addError(ValidationErrors.invalidIdReference("selectedBuffIds", String.valueOf(buffId)));
                continue;
            }

            int baseId = buffId % 100;
            if (baseId < MIN_BUFF_BASE_ID || baseId > MAX_BUFF_BASE_ID) {
                log.warn("Validation failed: selectedBuffIds[{}] buff ID '{}' has invalid base ID '{}'",
                        i, buffId, baseId);
                context.addError(ValidationErrors.valueOutOfRange("selectedBuffIds[" + i + "] base ID", baseId,
                        MIN_BUFF_BASE_ID, MAX_BUFF_BASE_ID));
                continue;
            }

            if (!seenBaseIds.add(baseId)) {
                log.warn("Validation failed: selectedBuffIds has duplicate base ID '{}' (buff ID '{}')",
                        baseId, buffId);
                context.addError(ValidationErrors.duplicateValue("selectedBuffIds base IDs", String.valueOf(baseId)));
            }
        }
    }

    void validateStartGiftIds(JsonNode root, ValidationContext context) {
        JsonNode keywordNode = root.get("selectedGiftKeyword");
        JsonNode giftIdsNode = root.get("selectedGiftIds");

        boolean hasKeyword = keywordNode != null && !keywordNode.isNull() && keywordNode.isTextual();
        if (!hasKeyword) {
            if (giftIdsNode != null && giftIdsNode.isArray() && giftIdsNode.size() > 0) {
                log.warn("Validation failed: selectedGiftIds has items but selectedGiftKeyword is null");
                context.addError(ValidationErrors.invalidSequence("selectedGiftIds requires selectedGiftKeyword"));
            }
            return;
        }

        String keyword = keywordNode.asText();

        if (!gameDataRegistry.hasStartGiftKeyword(keyword)) {
            log.warn("Validation failed: selectedGiftKeyword '{}' is not a valid keyword", keyword);
            context.addError(ValidationErrors.invalidIdReference("selectedGiftKeyword", keyword));
            return;
        }

        Set<String> pool = gameDataRegistry.getStartGiftPool(keyword);

        if (giftIdsNode != null && giftIdsNode.isArray()) {
            Set<String> seenGiftIds = new HashSet<>();

            for (int i = 0; i < giftIdsNode.size(); i++) {
                JsonNode node = giftIdsNode.get(i);
                if (!node.isTextual()) {
                    log.warn("Validation failed: selectedGiftIds[{}] is not a string", i);
                    context.addError(ValidationErrors.invalidFieldType("selectedGiftIds[" + i + "]", "string", node));
                    continue;
                }

                String giftId = node.asText();

                if (!seenGiftIds.add(giftId)) {
                    log.warn("Validation failed: selectedGiftIds has duplicate gift ID '{}'", giftId);
                    context.addError(ValidationErrors.duplicateValue("selectedGiftIds", giftId));
                    continue;
                }

                if (!pool.contains(giftId)) {
                    log.warn("Validation failed: selectedGiftIds[{}] gift '{}' not in keyword '{}' pool",
                            i, giftId, keyword);
                    context.addError(ValidationErrors.invalidIdReference("selectedGiftIds (not in keyword '" + keyword + "' pool)", giftId));
                }
            }
        }
    }
}
