package org.danteplanner.backend.moderation;

import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import org.danteplanner.backend.config.TestConfig;
import org.danteplanner.backend.integration.SharedMySqlContainerSupport;
import org.danteplanner.backend.moderation.api.ModeratedPlanner;
import org.danteplanner.backend.moderation.service.PlannerModerationService;
import org.danteplanner.backend.planner.repository.StoredPlanner;
import org.danteplanner.backend.planner.repository.PlannerRepository;
import org.danteplanner.backend.planner.service.PlannerCommandService;
import org.danteplanner.backend.support.PlannerSeeds;
import org.danteplanner.backend.support.TestDataFactory;
import org.danteplanner.backend.user.entity.User;
import org.danteplanner.backend.user.repository.UserRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.danteplanner.backend.support.Rejections.assertRejected;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@ActiveProfiles("it")
@Tag("containerized")
@Import(TestConfig.class)
class PlannerModerationServiceIT extends SharedMySqlContainerSupport {

    @Autowired
    private PlannerModerationService moderationService;

    @Autowired
    private PlannerCommandService plannerCommandService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PlannerRepository plannerRepository;

    @Autowired
    private PlannerSeeds plannerSeeds;

    private User owner;
    private User moderator;
    private User admin;

    @BeforeEach
    void setUp() {
        owner = TestDataFactory.createTestUser(userRepository, "moderated-owner@example.com");
        moderator = TestDataFactory.createModerator(userRepository, "unpublishing-mod@example.com");
        admin = TestDataFactory.createAdmin(userRepository, "unpublishing-admin@example.com");
    }

    private StoredPlanner persisted(UUID plannerId) {
        return plannerRepository.findAggregate(plannerId).orElseThrow();
    }

    @Test
    void unpublishPlanner_WhenModeratorUnpublishes_Succeeds() {
        StoredPlanner planner = TestDataFactory.createTestPlanner(plannerSeeds, owner, true);

        ModeratedPlanner result = moderationService.unpublishPlanner(moderator.getId(), planner.id());

        assertThat(result.published()).isFalse();
        StoredPlanner persisted = persisted(planner.id());
        assertThat(persisted.id()).isEqualTo(planner.id());
        assertThat(persisted.publication().isPublished()).isFalse();
    }

    @Test
    void unpublishPlanner_WhenAdminUnpublishes_Succeeds() {
        StoredPlanner planner = TestDataFactory.createTestPlanner(plannerSeeds, owner, true);

        ModeratedPlanner result = moderationService.unpublishPlanner(admin.getId(), planner.id());

        assertThat(result.published()).isFalse();
        assertThat(persisted(planner.id()).publication().isPublished()).isFalse();
    }

    @Test
    void unpublishPlanner_WhenAlreadyUnpublished_Succeeds() {
        StoredPlanner planner = TestDataFactory.createTestPlanner(plannerSeeds, owner, false);

        ModeratedPlanner result = moderationService.unpublishPlanner(moderator.getId(), planner.id());

        assertThat(result.published()).isFalse();
        assertThat(persisted(planner.id()).publication().isPublished()).isFalse();
    }

    @Test
    void unpublishPlanner_WhenNonExistentPlanner_ThrowsPlannerNotFoundException() {
        UUID nonExistentId = UUID.randomUUID();

        assertRejected(() -> moderationService.unpublishPlanner(moderator.getId(), nonExistentId))
                .withCode("PLANNER_NOT_FOUND");
    }

    @Test
    void unpublishPlanner_WhenDeletedPlanner_ThrowsPlannerNotFoundException() {
        StoredPlanner planner = TestDataFactory.createTestPlanner(plannerSeeds, owner, true);
        plannerCommandService.deletePlanner(owner.getId(), planner.id());

        assertRejected(() -> moderationService.unpublishPlanner(moderator.getId(), planner.id()))
                .withCode("PLANNER_NOT_FOUND");
    }
}
