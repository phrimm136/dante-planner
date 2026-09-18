package org.danteplanner.backend.planner.validation;

import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.regex.Pattern;

@Component
public class SinnerIdValidator {

    // Identity: 1 + sinner (01-12) + identity index (2+ digits)
    private static final Pattern IDENTITY_PATTERN = Pattern.compile("^1(0[1-9]|1[0-2])\\d{2,}$");

    // EGO: 2 + sinner (01-12) + EGO index (2+ digits)
    private static final Pattern EGO_PATTERN = Pattern.compile("^2(0[1-9]|1[0-2])\\d{2,}$");

    /**
     * Equipment keys are 2-digit 1-indexed ("01"-"12"), matching ID sinner indices.
     */
    public boolean validateMatch(String equipmentKey, String entityId) {
        String normalizedKey = equipmentKey.length() == 1
                ? "0" + equipmentKey
                : equipmentKey;

        return extractSinnerIndex(entityId)
                .filter(normalizedKey::equals)
                .isPresent();
    }

    private Optional<String> extractSinnerIndex(String id) {
        if (id == null || id.length() < 4) {
            return Optional.empty();
        }

        if (IDENTITY_PATTERN.matcher(id).matches() || EGO_PATTERN.matcher(id).matches()) {
            return Optional.of(id.substring(1, 3));
        }

        return Optional.empty();
    }
}
