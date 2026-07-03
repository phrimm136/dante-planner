package org.danteplanner.backend.planner.validation;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

import static org.danteplanner.backend.planner.validation.SinnerKeys.ALL_SINNER_KEYS;
import static org.danteplanner.backend.planner.validation.SinnerKeys.MAX_EQUIPMENT_SINNER;
import static org.danteplanner.backend.planner.validation.SinnerKeys.MIN_EQUIPMENT_SINNER;

/**
 * Validates the optional skillEAState map: sinner-index keys, presence of all
 * 12 sinners, valid skill-slot keys, and each sinner's slot total.
 */
@Component
@Slf4j
class SkillStateValidator {

    private static final Set<String> VALID_SKILL_SLOTS = Set.of("0", "1", "2");

    private static final int SKILL_EA_TOTAL = 6;

    void validateSkillEAState(JsonNode root, ValidationContext context) {
        JsonNode skillEAState = root.get("skillEAState");
        if (skillEAState == null) {
            return;
        }

        if (!skillEAState.isObject()) {
            log.warn("Validation failed: skillEAState is not an object");
            context.addError(ValidationErrors.invalidFieldType("skillEAState", "object", skillEAState));
            return;
        }

        Set<String> presentKeys = new HashSet<>();
        Iterator<String> keys = skillEAState.fieldNames();
        while (keys.hasNext()) {
            String key = keys.next();
            try {
                int index = Integer.parseInt(key);
                if (index < MIN_EQUIPMENT_SINNER || index > MAX_EQUIPMENT_SINNER) {
                    log.warn("Validation failed: skillEAState key '{}' out of range", key);
                    context.addError(ValidationErrors.valueOutOfRange("skillEAState key", index,
                            MIN_EQUIPMENT_SINNER, MAX_EQUIPMENT_SINNER));
                    continue;
                }
                presentKeys.add(String.format("%02d", index));
            } catch (NumberFormatException e) {
                log.warn("Validation failed: skillEAState key '{}' not an integer", key);
                context.addError(ValidationErrors.invalidFieldType("skillEAState key '" + key + "'", "integer"));
                continue;
            }
        }

        Set<String> missingSinners = new HashSet<>(ALL_SINNER_KEYS);
        missingSinners.removeAll(presentKeys);
        if (!missingSinners.isEmpty()) {
            log.warn("Validation failed: missing sinners in skillEAState - {}", missingSinners);
            context.addError(ValidationErrors.missingRequiredField(missingSinners));
            return;
        }

        for (String sinnerKey : presentKeys) {
            JsonNode sinnerSkills = skillEAState.get(sinnerKey);
            if (sinnerSkills == null) {
                sinnerSkills = skillEAState.get(String.valueOf(Integer.parseInt(sinnerKey)));
            }
            if (sinnerSkills == null || !sinnerSkills.isObject()) {
                log.warn("Validation failed: skillEAState[{}] is not an object", sinnerKey);
                context.addError(ValidationErrors.invalidFieldType("skillEAState[" + sinnerKey + "]", "object"));
                continue;
            }

            Set<String> seenSlots = new HashSet<>();
            int total = 0;
            Iterator<String> slotKeys = sinnerSkills.fieldNames();
            while (slotKeys.hasNext()) {
                String slotKey = slotKeys.next();

                if (!VALID_SKILL_SLOTS.contains(slotKey)) {
                    log.warn("Validation failed: skillEAState[{}] has invalid slot key '{}'", sinnerKey, slotKey);
                    context.addError(ValidationErrors.invalidFieldType("skillEAState[" + sinnerKey + "] slot '" + slotKey + "'", "0, 1, or 2"));
                    continue;
                }

                if (!seenSlots.add(slotKey)) {
                    log.warn("Validation failed: skillEAState[{}] has duplicate slot key '{}'", sinnerKey, slotKey);
                    context.addError(ValidationErrors.duplicateValue("skillEAState[" + sinnerKey + "]", slotKey));
                    continue;
                }

                JsonNode slotValue = sinnerSkills.get(slotKey);
                if (!slotValue.isNumber()) {
                    log.warn("Validation failed: skillEAState[{}][{}] is not a number", sinnerKey, slotKey);
                    context.addError(ValidationErrors.invalidFieldType("skillEAState[" + sinnerKey + "][" + slotKey + "]", "number", slotValue));
                    continue;
                }
                total += slotValue.asInt();
            }

            if (total != SKILL_EA_TOTAL) {
                log.warn("Validation failed: skillEAState[{}] total {} != {}", sinnerKey, total, SKILL_EA_TOTAL);
                context.addError(ValidationErrors.valueOutOfRange("skillEAState[" + sinnerKey + "] total", total,
                        SKILL_EA_TOTAL, SKILL_EA_TOTAL));
            }
        }
    }
}
