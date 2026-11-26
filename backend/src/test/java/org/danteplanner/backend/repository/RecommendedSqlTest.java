package org.danteplanner.backend.repository;

import java.time.Instant;

import org.danteplanner.backend.planner.entity.Listing;
import org.danteplanner.backend.planner.repository.RecommendedSql;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The in-memory mirror of the recommended predicate, asserted on the three readings that separate
 * it from the SQL fragment it must agree with.
 */
class RecommendedSqlTest {

    private static final int THRESHOLD = 10;

    @Test
    void isRecommended_WhenAtThresholdAndNotHidden_IsRecommendable() {
        assertThat(RecommendedSql.isRecommended(THRESHOLD, new Listing.Listed(), THRESHOLD)).isTrue();
    }

    @Test
    void isRecommended_WhenHidden_IsNotRecommendable() {
        assertThat(RecommendedSql.isRecommended(
                THRESHOLD, new Listing.Hidden(1L, "spam", Instant.EPOCH), THRESHOLD)).isFalse();
    }

    @Test
    void isRecommended_WhenBelowThreshold_IsNotRecommendable() {
        assertThat(RecommendedSql.isRecommended(THRESHOLD - 1, new Listing.Listed(), THRESHOLD)).isFalse();
    }
}
