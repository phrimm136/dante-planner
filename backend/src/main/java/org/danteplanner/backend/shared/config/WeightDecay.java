package org.danteplanner.backend.shared.config;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

final class WeightDecay {

    static final int WEIGHT_NEW = 3;

    static final int WEIGHT_RECENT = 2;

    static final int WEIGHT_OLD = 1;

    private static final int TIER_NEW_DAYS = 30;
    private static final int TIER_RECENT_DAYS = 60;

    private WeightDecay() {
    }

    static int weightOf(LocalDate addedDate, LocalDate referenceDate) {
        if (addedDate == null) {
            return WEIGHT_OLD;
        }

        long daysSinceAdded = ChronoUnit.DAYS.between(addedDate, referenceDate);
        if (daysSinceAdded <= TIER_NEW_DAYS) {
            return WEIGHT_NEW;
        }
        if (daysSinceAdded <= TIER_RECENT_DAYS) {
            return WEIGHT_RECENT;
        }
        return WEIGHT_OLD;
    }
}
