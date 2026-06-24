package org.danteplanner.backend.validation;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

import static org.danteplanner.backend.validation.SinnerKeys.ALL_SINNER_KEYS;
import static org.danteplanner.backend.validation.SinnerKeys.MAX_EQUIPMENT_SINNER;
import static org.danteplanner.backend.validation.SinnerKeys.MIN_EQUIPMENT_SINNER;

/**
 * Validates equipment structure: sinner-index keys, presence of all 12
 * sinners, each sinner's identity, the required ZAYIN EGO, EGO types, and
 * the deployment order array.
 */
@Component
@Slf4j
class EquipmentValidator {

    private static final int MIN_DEPLOYMENT_SINNER = 0;
    private static final int MAX_DEPLOYMENT_SINNER = 11;

    private static final String REQUIRED_EGO_TYPE = "ZAYIN";

    private static final Set<String> VALID_EGO_TYPES = Set.of(
            "ZAYIN", "TETH", "HE", "WAW", "ALEPH"
    );

    void validateEquipmentSinnerIndices(JsonNode root, ValidationContext context) {
        JsonNode equipment = root.get("equipment");
        if (equipment == null || !equipment.isObject()) {
            return;
        }

        Set<String> presentKeys = new HashSet<>();
        Iterator<String> keys = equipment.fieldNames();
        while (keys.hasNext()) {
            String key = keys.next();
            try {
                int index = Integer.parseInt(key);
                if (index < MIN_EQUIPMENT_SINNER || index > MAX_EQUIPMENT_SINNER) {
                    log.warn("Validation failed: equipment index '{}' out of range", index);
                    context.addError(ValidationErrors.valueOutOfRange("equipment key", index, MIN_EQUIPMENT_SINNER, MAX_EQUIPMENT_SINNER));
                    continue;
                }
                presentKeys.add(String.format("%02d", index));
            } catch (NumberFormatException e) {
                log.warn("Validation failed: equipment key '{}' not an integer", key);
                context.addError(ValidationErrors.invalidFieldType("equipment key '" + key + "'", "integer"));
                continue;
            }
        }

        Set<String> missingSinners = new HashSet<>(ALL_SINNER_KEYS);
        missingSinners.removeAll(presentKeys);
        if (!missingSinners.isEmpty()) {
            log.warn("Validation failed: missing sinners in equipment - {}", missingSinners);
            context.addError(ValidationErrors.missingRequiredField(missingSinners));
            return;
        }

        for (String sinnerKey : presentKeys) {
            JsonNode sinnerEquipment = equipment.get(sinnerKey);
            if (sinnerEquipment == null) {
                sinnerEquipment = equipment.get(String.valueOf(Integer.parseInt(sinnerKey)));
            }
            if (sinnerEquipment == null || !sinnerEquipment.isObject()) {
                log.warn("Validation failed: equipment[{}] is not an object", sinnerKey);
                context.addError(ValidationErrors.invalidFieldType("equipment[" + sinnerKey + "]", "object"));
                continue;
            }

            validateSinnerHasIdentity(sinnerKey, sinnerEquipment, context);
            validateSinnerHasZayinEgo(sinnerKey, sinnerEquipment, context);
        }
    }

    private void validateSinnerHasIdentity(String sinnerKey, JsonNode sinnerEquipment, ValidationContext context) {
        JsonNode identity = sinnerEquipment.get("identity");
        if (identity == null || !identity.isObject()) {
            log.warn("Validation failed: equipment[{}] missing identity", sinnerKey);
            context.addError(ValidationErrors.invalidFieldType("equipment[" + sinnerKey + "].identity", "object"));
            return;
        }

        JsonNode idNode = identity.get("id");
        if (idNode == null || !idNode.isTextual() || idNode.asText().isBlank()) {
            log.warn("Validation failed: equipment[{}].identity missing id", sinnerKey);
            context.addError(ValidationErrors.invalidFieldType("equipment[" + sinnerKey + "].identity.id", "non-empty string"));
        }
    }

    private void validateSinnerHasZayinEgo(String sinnerKey, JsonNode sinnerEquipment, ValidationContext context) {
        JsonNode egos = sinnerEquipment.get("egos");
        if (egos == null || !egos.isObject()) {
            log.warn("Validation failed: equipment[{}] missing egos", sinnerKey);
            context.addError(ValidationErrors.invalidFieldType("equipment[" + sinnerKey + "].egos", "object"));
            return;
        }

        validateEgoTypes(sinnerKey, egos, context);

        JsonNode zayinEgo = egos.get(REQUIRED_EGO_TYPE);
        if (zayinEgo == null || !zayinEgo.isObject()) {
            log.warn("Validation failed: equipment[{}] missing {} EGO", sinnerKey, REQUIRED_EGO_TYPE);
            context.addError(ValidationErrors.missingRequiredField(Set.of("equipment[" + sinnerKey + "].egos.ZAYIN")));
            return;
        }

        JsonNode idNode = zayinEgo.get("id");
        if (idNode == null || !idNode.isTextual() || idNode.asText().isBlank()) {
            log.warn("Validation failed: equipment[{}].egos.{} missing id", sinnerKey, REQUIRED_EGO_TYPE);
            context.addError(ValidationErrors.invalidFieldType("equipment[" + sinnerKey + "].egos.ZAYIN.id", "non-empty string"));
        }
    }

    private void validateEgoTypes(String sinnerKey, JsonNode egos, ValidationContext context) {
        Set<String> seenTypes = new HashSet<>();
        Iterator<String> egoKeys = egos.fieldNames();

        while (egoKeys.hasNext()) {
            String egoType = egoKeys.next();

            if (!VALID_EGO_TYPES.contains(egoType)) {
                log.warn("Validation failed: equipment[{}].egos has invalid type '{}'", sinnerKey, egoType);
                context.addError(ValidationErrors.invalidFieldType("equipment[" + sinnerKey + "].egos." + egoType,
                        "valid EGO type (ZAYIN/TETH/HE/WAW/ALEPH)"));
                continue;
            }

            if (!seenTypes.add(egoType)) {
                log.warn("Validation failed: equipment[{}].egos has duplicate type '{}'", sinnerKey, egoType);
                context.addError(ValidationErrors.duplicateValue("equipment[" + sinnerKey + "].egos", egoType));
            }
        }

        if (seenTypes.size() > VALID_EGO_TYPES.size()) {
            log.warn("Validation failed: equipment[{}].egos has more than {} EGO types", sinnerKey, VALID_EGO_TYPES.size());
            context.addError(ValidationErrors.valueOutOfRange("equipment[" + sinnerKey + "].egos count",
                    seenTypes.size(), 1, VALID_EGO_TYPES.size()));
        }
    }

    void validateDeploymentOrder(JsonNode root, ValidationContext context) {
        JsonNode order = root.get("deploymentOrder");
        if (order == null || !order.isArray()) {
            return;
        }

        for (int i = 0; i < order.size(); i++) {
            JsonNode node = order.get(i);
            if (!node.isNumber()) {
                log.warn("Validation failed: deploymentOrder[{}] not a number", i);
                context.addError(ValidationErrors.invalidFieldType("deploymentOrder[" + i + "]", "number", node));
                continue;
            }

            int index = node.asInt();
            if (index < MIN_DEPLOYMENT_SINNER || index > MAX_DEPLOYMENT_SINNER) {
                log.warn("Validation failed: deploymentOrder[{}]={} out of range", i, index);
                context.addError(ValidationErrors.valueOutOfRange("deploymentOrder[" + i + "]", index,
                        MIN_DEPLOYMENT_SINNER, MAX_DEPLOYMENT_SINNER));
            }
        }
    }
}
