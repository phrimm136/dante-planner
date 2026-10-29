package org.danteplanner.backend.planner.service;

import java.util.UUID;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

class PlannerVisibilityTest {

    private static final long OWNER_ID = 41L;

    @ParameterizedTest
    @CsvSource({
            "true,  false, true",
            "true,  true,  false",
            "false, false, false",
            "false, true,  false"
    })
    void visible_WhenCapturedCombination_AnswersAsTodaysQueries(
            boolean published, boolean authorDeleted, boolean expected) {
        PlannerVisibilityFacts facts = new PlannerVisibilityFacts(UUID.randomUUID(), OWNER_ID, published, authorDeleted);

        assertThat(PlannerVisibility.visible(facts)).isEqualTo(expected);
    }
}
