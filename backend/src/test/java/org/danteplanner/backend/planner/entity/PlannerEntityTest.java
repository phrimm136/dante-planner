package org.danteplanner.backend.planner.entity;

import java.time.Instant;

import org.junit.jupiter.api.Test;

import org.danteplanner.backend.support.TestDataFactory;
import org.danteplanner.backend.user.api.UserRole;

import static org.assertj.core.api.Assertions.assertThat;
import static org.danteplanner.backend.support.Rejections.assertRejected;

/**
 * Pure in-memory lifecycle tests for {@link Planner}: a moderator takedown
 * unpublishes, and a taken-down planner cannot be re-published.
 */
class PlannerEntityTest {

    @Test
    void takeDown_WhenInvoked_Unpublishes() {
        Planner planner = TestDataFactory.planner(TestDataFactory.unsavedUser(UserRole.NORMAL)).published(true).build();

        planner.takeDown(Instant.now());

        assertThat(planner.isPublished()).isFalse();
        assertThat(planner.getTakenDownAt()).isNotNull();
    }

    @Test
    void publish_WhenTakenDown_Throws() {
        Planner planner = TestDataFactory.planner(TestDataFactory.unsavedUser(UserRole.NORMAL)).published(true).build();
        planner.takeDown(Instant.now());

        assertThat(planner.isPublished()).isFalse();
        assertThat(planner.isTakenDown()).isTrue();

        assertRejected(() -> planner.publish(Instant.now()))
                .withCode("PLANNER_FORBIDDEN")
                .satisfies(rejection -> assertThat(rejection.getMessage()).isEqualTo("User is not authorized to modify planner with id: " + planner.getId()));
    }
}
