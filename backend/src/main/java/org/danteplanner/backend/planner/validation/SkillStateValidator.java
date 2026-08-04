package org.danteplanner.backend.planner.validation;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Set;

import static org.danteplanner.backend.planner.validation.SinnerKeys.forEachSinnerEntry;

/**
 * Validates the optional skillEAState map: sinner-index keys, presence of all
 * 12 sinners, valid skill-slot keys, and each sinner's slot total.
 */
@Component
class SkillStateValidator {

    private static final Set<String> VALID_SKILL_SLOTS = Set.of("0", "1", "2");

    private static final int SKILL_EA_TOTAL = 6;

    void validateSkillEAState(JsonNode root, ValidationContext context) {
        JsonNode skillEAState = root.get("skillEAState");
        if (skillEAState == null) {
            return;
        }

        if (!skillEAState.isObject()) {
            context.reject("skillEAState", p -> ValidationErrors.invalidFieldType(p, "object", skillEAState));
            return;
        }

        forEachSinnerEntry(skillEAState, "skillEAState", context, (sinnerKey, sinnerSkills) ->
                validateSlotTotal(sinnerKey, sinnerSkills, context));
    }

    private void validateSlotTotal(String sinnerKey, JsonNode sinnerSkills, ValidationContext context) {
        String sinnerPath = "skillEAState[" + sinnerKey + "]";
        int total = 0;

        for (Map.Entry<String, JsonNode> slot : sinnerSkills.properties()) {
            String slotKey = slot.getKey();
            JsonNode slotValue = slot.getValue();

            if (!VALID_SKILL_SLOTS.contains(slotKey)) {
                context.reject(sinnerPath + " slot '" + slotKey + "'",
                        p -> ValidationErrors.invalidFieldType(p, "0, 1, or 2"));
                continue;
            }

            if (!slotValue.isNumber()) {
                context.reject(sinnerPath + "[" + slotKey + "]",
                        p -> ValidationErrors.invalidFieldType(p, "number", slotValue));
                continue;
            }

            total += slotValue.asInt();
        }

        if (total != SKILL_EA_TOTAL) {
            int slotTotal = total;
            context.reject(sinnerPath + " total",
                    p -> ValidationErrors.valueOutOfRange(p, slotTotal, SKILL_EA_TOTAL, SKILL_EA_TOTAL));
        }
    }
}
