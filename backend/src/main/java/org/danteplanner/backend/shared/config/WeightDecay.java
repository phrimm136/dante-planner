package org.danteplanner.backend.shared.config;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/**
 * Time-decay weighting for the random pools that mint usernames.
 *
 * <p>A recently added entry is drawn more often, so new vocabulary reaches users instead of being
 * swamped by the accumulated pool.</p>
 */
final class WeightDecay {

    /** Weight of an entry added within {@link #TIER_NEW_DAYS} days. */
    static final int WEIGHT_NEW = 3;

    /** Weight of an entry added within {@link #TIER_RECENT_DAYS} days. */
    static final int WEIGHT_RECENT = 2;

    /** Weight of an older entry, and of one the pool does not carry. */
    static final int WEIGHT_OLD = 1;

    private static final int TIER_NEW_DAYS = 30;
    private static final int TIER_RECENT_DAYS = 60;

    private WeightDecay() {
    }

    /**
     * The selection weight of an entry.
     *
     * @param addedDate     when the entry joined the pool, or null when the pool has no such entry
     * @param referenceDate the date to measure age against
     * @return {@link #WEIGHT_NEW}, {@link #WEIGHT_RECENT}, or {@link #WEIGHT_OLD}
     */
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
