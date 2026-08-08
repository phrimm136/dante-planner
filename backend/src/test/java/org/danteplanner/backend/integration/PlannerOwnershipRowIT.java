package org.danteplanner.backend.integration;

import java.util.Optional;

import org.danteplanner.backend.config.TestConfig;
import org.danteplanner.backend.planner.entity.Planner;
import org.danteplanner.backend.planner.repository.PlannerOwnershipRow;
import org.danteplanner.backend.planner.repository.PlannerRepository;
import org.danteplanner.backend.support.TestDataFactory;
import org.danteplanner.backend.user.entity.User;
import org.danteplanner.backend.user.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies the {@link PlannerOwnershipRow} projection maps against the real schema: the ownership
 * SELECT resolves a planner's owner and soft-delete state through the aliased getters, so the upsert
 * create branch can distinguish owner-soft-deleted from other-user-active in one query.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("it")
@Tag("containerized")
@Import(TestConfig.class)
class PlannerOwnershipRowIT extends SharedMySqlContainerSupport {


    @Autowired
    private PlannerRepository plannerRepository;

    @Autowired
    private UserRepository userRepository;

    @Test
    @DisplayName("ownership SELECT resolves an active planner's owner and null soft-delete state")
    void createExistenceTwoSelects_WhenActivePlanner_ProjectsOwnerAndNullDeletedAt() {
        User owner = TestDataFactory.createTestUser(userRepository, "ownership-" + System.nanoTime() + "@example.com");
        Planner planner = TestDataFactory.createTestPlanner(plannerRepository, owner, false);

        Optional<PlannerOwnershipRow> ownership =
                plannerRepository.findOwnershipById(planner.getId());

        assertThat(ownership).isPresent();
        assertThat(ownership.get().getUserId()).isEqualTo(owner.getId());
        assertThat(ownership.get().getDeletedAt()).isNull();
    }
}
