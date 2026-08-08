package org.danteplanner.backend.planner.entity;

import org.danteplanner.backend.planner.exception.PlannerForbiddenException;
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
        Planner planner = Planner.builder()
                .id(UUID.randomUUID())
                .plannerType(PlannerType.MIRROR_DUNGEON)
                .createdAt(Instant.now())
                .build();
        planner.attach(
                PlannerContent.builder()
                        .title("Test Planner")
                        .category("5F")
                        .content("{}")
                        .gameContentVersion(6)
                        .build(),
                PlannerPublication.builder().build(),
                PlannerModeration.builder().build());
        planner.publish();
        return planner;
    }

    @Test
    void takeDown_WhenInvoked_Unpublishes() {
        Planner planner = publishedPlanner();

        planner.takeDown();

        assertThat(planner.isPublished()).isFalse();
        assertThat(planner.getTakenDownAt()).isNotNull();
    }

    @Test
    void publish_WhenTakenDown_Throws() {
        Planner planner = publishedPlanner();
        planner.takeDown();

        assertThat(planner.isPublished()).isFalse();
        assertThat(planner.isTakenDown()).isTrue();

        assertThatThrownBy(planner::publish)
                .isInstanceOf(PlannerForbiddenException.class);
    }
}
