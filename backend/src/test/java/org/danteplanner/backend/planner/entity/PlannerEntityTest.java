package org.danteplanner.backend.planner.entity;

import org.danteplanner.backend.planner.exception.PlannerForbiddenException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Pure in-memory lifecycle tests for {@link Planner}: a moderator takedown
 * unpublishes, and a taken-down planner cannot be re-published.
 */
class PlannerEntityTest {

    private Planner publishedPlanner() {
        return Planner.builder()
                .id(UUID.randomUUID())
                .title("Test Planner")
                .category("5F")
                .content("{}")
                .contentVersion(6)
                .plannerType(PlannerType.MIRROR_DUNGEON)
                .createdAt(Instant.now())
                .lastModifiedAt(Instant.now())
                .published(true)
                .build();
    }

    @Test
    @DisplayName("takeDown_WhenInvoked_Unpublishes")
    void takeDown_WhenInvoked_Unpublishes() {
        Planner planner = publishedPlanner();

        planner.takeDown();

        assertThat(planner.getPublished()).isFalse();
        assertThat(planner.getTakenDownAt()).isNotNull();
    }

    @Test
    @DisplayName("togglePublished_WhenTakenDown_Throws")
    void togglePublished_WhenTakenDown_Throws() {
        Planner planner = publishedPlanner();
        planner.takeDown();

        assertThat(planner.getPublished()).isFalse();
        assertThat(planner.isTakenDown()).isTrue();

        assertThatThrownBy(planner::togglePublished)
                .isInstanceOf(PlannerForbiddenException.class);
    }
}
