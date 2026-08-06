package org.danteplanner.backend.planner.validation;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;

import java.util.Iterator;
import java.util.Set;

import static org.danteplanner.backend.planner.validation.JsonTraversal.arrayField;
import static org.danteplanner.backend.planner.validation.JsonTraversal.eachNumber;
import static org.danteplanner.backend.planner.validation.SinnerKeys.forEachSinnerEntry;

/**
 * Validates equipment structure: sinner-index keys, presence of all 12
 * sinners, each sinner's identity, the required ZAYIN EGO, EGO types, and
 * the deployment order array.
 */
@Component
class EquipmentValidator {

    private static final int MIN_DEPLOYMENT_SINNER = 0;
    private static final int MAX_DEPLOYMENT_SINNER = 11;

    private static final String REQUIRED_EGO_TYPE = "ZAYIN";

    private static final Set<String> VALID_EGO_TYPES = Set.of(
            "ZAYIN", "TETH", "HE", "WAW", "ALEPH"
    );

    void validateEquipmentSinnerIndices(JsonNode root, ValidationContext context) {
        forEachSinnerEntry(root.path("equipment"), "equipment", context, (sinnerKey, sinnerEquipment) -> {
            validateSinnerHasIdentity(sinnerKey, sinnerEquipment, context);
            validateSinnerHasZayinEgo(sinnerKey, sinnerEquipment, context);
        });
    }

    private void validateSinnerHasIdentity(String sinnerKey, JsonNode sinnerEquipment, ValidationContext context) {
        JsonNode identity = sinnerEquipment.get("identity");
        if (identity == null || !identity.isObject()) {
            context.reject("equipment[" + sinnerKey + "].identity",
                    p -> ValidationErrors.invalidFieldType(p, "object"));
            return;
        }

        JsonNode idNode = identity.get("id");
        if (idNode == null || !idNode.isTextual() || idNode.asText().isBlank()) {
            context.reject("equipment[" + sinnerKey + "].identity.id",
                    p -> ValidationErrors.invalidFieldType(p, "non-empty string"));
        }
    }

    private void validateSinnerHasZayinEgo(String sinnerKey, JsonNode sinnerEquipment, ValidationContext context) {
        JsonNode egos = sinnerEquipment.get("egos");
        if (egos == null || !egos.isObject()) {
            context.reject("equipment[" + sinnerKey + "].egos",
                    p -> ValidationErrors.invalidFieldType(p, "object"));
            return;
        }

        validateEgoTypes(sinnerKey, egos, context);

        JsonNode zayinEgo = egos.get(REQUIRED_EGO_TYPE);
        if (zayinEgo == null || !zayinEgo.isObject()) {
            context.reject("equipment[" + sinnerKey + "].egos." + REQUIRED_EGO_TYPE,
                    p -> ValidationErrors.missingRequiredField(Set.of(p)));
            return;
        }

        JsonNode idNode = zayinEgo.get("id");
        if (idNode == null || !idNode.isTextual() || idNode.asText().isBlank()) {
            context.reject("equipment[" + sinnerKey + "].egos." + REQUIRED_EGO_TYPE + ".id",
                    p -> ValidationErrors.invalidFieldType(p, "non-empty string"));
        }
    }

    private void validateEgoTypes(String sinnerKey, JsonNode egos, ValidationContext context) {
        Iterator<String> egoKeys = egos.fieldNames();

        while (egoKeys.hasNext()) {
            String egoType = egoKeys.next();

            if (!VALID_EGO_TYPES.contains(egoType)) {
                context.reject("equipment[" + sinnerKey + "].egos." + egoType,
                        p -> ValidationErrors.invalidFieldType(p, "valid EGO type (ZAYIN/TETH/HE/WAW/ALEPH)"));
            }
        }
    }

    void validateDeploymentOrder(JsonNode root, ValidationContext context) {
        eachNumber(arrayField(root, "deploymentOrder"), "deploymentOrder", context, (sinnerIndex, position) -> {
            if (sinnerIndex < MIN_DEPLOYMENT_SINNER || sinnerIndex > MAX_DEPLOYMENT_SINNER) {
                context.reject("deploymentOrder[" + position + "]", p -> ValidationErrors.valueOutOfRange(
                        p, sinnerIndex, MIN_DEPLOYMENT_SINNER, MAX_DEPLOYMENT_SINNER));
            }
        });
    }
}
