package org.danteplanner.backend.planner.validation;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;

/**
 * Equipment and skillEAState are both keyed by 1-indexed sinner number
 * (1-12) and both require all 12 sinners present.
 */
final class SinnerKeys {

    static final int MIN_EQUIPMENT_SINNER = 1;
    static final int MAX_EQUIPMENT_SINNER = 12;

    static final Set<String> ALL_SINNER_KEYS = Set.of(
            "01", "02", "03", "04", "05", "06", "07", "08", "09", "10", "11", "12"
    );

    private SinnerKeys() {
    }

    static void forEachSinnerEntry(JsonNode container, String field, ValidationContext context,
                                   BiConsumer<String, JsonNode> body) {
        if (!container.isObject()) {
            return;
        }

        Map<String, JsonNode> bySinnerKey = new HashMap<>();

        for (Map.Entry<String, JsonNode> entry : container.properties()) {
            String key = entry.getKey();
            int index;
            try {
                index = Integer.parseInt(key);
            } catch (NumberFormatException e) {
                context.reject(field + " key '" + key + "'", p -> ValidationErrors.invalidFieldType(p, "integer"));
                continue;
            }

            if (index < MIN_EQUIPMENT_SINNER || index > MAX_EQUIPMENT_SINNER) {
                context.reject(field + " key", p -> ValidationErrors.valueOutOfRange(
                        p, index, MIN_EQUIPMENT_SINNER, MAX_EQUIPMENT_SINNER));
                continue;
            }

            bySinnerKey.put(String.format("%02d", index), entry.getValue());
        }

        Set<String> missingSinners = new HashSet<>(ALL_SINNER_KEYS);
        missingSinners.removeAll(bySinnerKey.keySet());
        if (!missingSinners.isEmpty()) {
            context.reject(field, p -> ValidationErrors.missingRequiredField(missingSinners));
            return;
        }

        for (Map.Entry<String, JsonNode> entry : bySinnerKey.entrySet()) {
            String sinnerKey = entry.getKey();
            JsonNode value = entry.getValue();
            if (!value.isObject()) {
                context.reject(field + "[" + sinnerKey + "]", p -> ValidationErrors.invalidFieldType(p, "object"));
                continue;
            }
            body.accept(sinnerKey, value);
        }
    }
}
