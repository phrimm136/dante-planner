package org.danteplanner.backend.validation;

import org.springframework.stereotype.Component;

import java.util.regex.Pattern;

/**
 * Validates sinner-ID consistency in equipment data.
 * Single responsibility: Extract and validate sinner indices from entity IDs.
 */
@Component
public class SinnerIdValidator {

    // Identity: 1 + sinner (01-12) + identity index (2+ digits)
    private static final Pattern IDENTITY_PATTERN = Pattern.compile("^1(0[1-9]|1[0-2])\\d{2,}$");

    // EGO: 2 + sinner (01-12) + EGO index (2+ digits)
    private static final Pattern EGO_PATTERN = Pattern.compile("^2(0[1-9]|1[0-2])\\d{2,}$");

    /**
     * Extract sinner index from an entity ID.
     *
     * @param id Entity ID (identity: "10101" or EGO: "20101")
     * @return Sinner index as 2-digit string (e.g., "01"), or null if invalid
     */
    public String extractSinnerIndex(String id) {
        if (id == null || id.length() < 4) {
            return null;
        }

        if (IDENTITY_PATTERN.matcher(id).matches() || EGO_PATTERN.matcher(id).matches()) {
            return id.substring(1, 3);
        }

        return null;
    }

    /**
     * Validate that equipment key matches the sinner encoded in entity ID.
     *
     * <p>Equipment keys are 2-digit 1-indexed ("01"-"12"), matching ID sinner indices.
     *
     * @param equipmentKey Equipment key (e.g., "01", "02", "12")
     * @param entityId     Identity or EGO ID (e.g., "10101", "20101")
     * @return true if sinner indices match
     */
    public boolean validateMatch(String equipmentKey, String entityId) {
        String sinnerFromId = extractSinnerIndex(entityId);
        if (sinnerFromId == null) {
            return false;
        }

        // Equipment key should be 2-digit format matching ID's sinner index
        String normalizedKey = equipmentKey.length() == 1
                ? "0" + equipmentKey
                : equipmentKey;

        return normalizedKey.equals(sinnerFromId);
    }
}
