package org.danteplanner.backend.planner.validation;

import java.util.Set;

/**
 * Sinner-key constants shared by equipment and skill-state validation.
 *
 * <p>Equipment and skillEAState are both keyed by 1-indexed sinner number
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
}
