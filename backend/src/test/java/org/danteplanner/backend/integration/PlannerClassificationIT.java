package org.danteplanner.backend.integration;

import java.util.Optional;

import org.danteplanner.backend.config.TestConfig;
import org.danteplanner.backend.planner.entity.Planner;
import org.danteplanner.backend.planner.repository.PlannerClassification;
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
 * Verifies the {@link PlannerClassification} projection maps against the real schema: the classifying
 * SELECT resolves a planner's owner and soft-delete state through the aliased getters, so the upsert
 * create branch can distinguish owner-soft-deleted from other-user-active in one query.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("it")
@Tag("containerized")
@Import(TestConfig.class)
class PlannerClassificationIT extends SharedMySqlContainerSupport {


    @Autowired
    private PlannerRepository plannerRepository;

    @Autowired
    private UserRepository userRepository;

    @Test
    @DisplayName("classifying SELECT resolves an active planner's owner and null soft-delete state")
    void createExistenceTwoSelects_WhenActivePlanner_ProjectsOwnerAndNullDeletedAt() {
        User owner = TestDataFactory.createTestUser(userRepository, "classify-" + System.nanoTime() + "@example.com");
        Planner planner = TestDataFactory.createTestPlanner(plannerRepository, owner, false);

        Optional<PlannerClassification> classification =
                plannerRepository.findClassificationById(planner.getId());

        assertThat(classification).isPresent();
        assertThat(classification.get().getUserId()).isEqualTo(owner.getId());
        assertThat(classification.get().getDeletedAt()).isNull();
    }
}
