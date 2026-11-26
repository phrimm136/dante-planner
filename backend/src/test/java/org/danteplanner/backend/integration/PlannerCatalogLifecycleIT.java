package org.danteplanner.backend.integration;

import org.danteplanner.backend.config.TestConfig;
import org.danteplanner.backend.moderation.dto.HidePlannerRequest;
import org.danteplanner.backend.moderation.service.PlannerModerationService;
import org.danteplanner.backend.planner.repository.StoredPlanner;
import org.danteplanner.backend.planner.entity.PlannerCatalog;
import org.danteplanner.backend.planner.entity.PlannerStats;
import org.danteplanner.backend.planner.entity.VoteType;
import org.danteplanner.backend.planner.repository.PlannerCatalogRepository;
import org.danteplanner.backend.planner.repository.PlannerStatsRepository;
import org.danteplanner.backend.planner.service.PlannerCommandService;
import org.danteplanner.backend.planner.service.PlannerEngagementService;
import org.danteplanner.backend.planner.service.PlannerPublishingService;
import org.danteplanner.backend.user.entity.User;
import org.danteplanner.backend.user.repository.UserRepository;
import org.danteplanner.backend.support.PlannerSeeds;
import org.danteplanner.backend.support.TestDataFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.danteplanner.backend.support.Rejections.assertRejected;

/**
 * Catalog projection lifecycle: membership equals visibility, the recommended
 * flag follows vote and moderation transitions, and a takedown pins the
 * aggregate unpublishable.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@ActiveProfiles("it")
@Tag("containerized")
@Import(TestConfig.class)
class PlannerCatalogLifecycleIT extends SharedMySqlContainerSupport {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PlannerSeeds plannerSeeds;

    @Autowired
    private PlannerCatalogRepository catalogRepository;

    @Autowired
    private PlannerStatsRepository statsRepository;

    @Autowired
    private PlannerCommandService commandService;

    @Autowired
    private PlannerPublishingService publishingService;

    @Autowired
    private PlannerEngagementService engagementService;

    @Autowired
    private PlannerModerationService plannerModerationService;

    @Value("${planner.recommended-threshold}")
    private int recommendedThreshold;

    private User owner;
    private User admin;

    @BeforeEach
    void setUp() {
        cleanUp();
        owner = TestDataFactory.createTestUser(userRepository, "catalog-owner@example.com");
        admin = TestDataFactory.createAdmin(userRepository, "catalog-admin@example.com");
    }

    @AfterEach
    void tearDown() {
        cleanUp();
    }

    private void cleanUp() {
    }

    private StoredPlanner draft() {
        StoredPlanner planner = TestDataFactory.createTestPlanner(plannerSeeds, owner, false);
        statsRepository.save(PlannerStats.builder().plannerId(planner.id()).build());
        return planner;
    }

    private boolean hasCatalogRow(UUID plannerId) {
        return catalogRepository.existsById(plannerId);
    }

    // catalog-membership-on-transition
    @Test
    void catalogMembership_WhenVisibilityTransitions_RowPresenceMatches() {
        // publish -> row
        StoredPlanner planner = draft();
        publishingService.publish(owner.getId(), planner.id());
        assertThat(hasCatalogRow(planner.id())).as("published planner has a catalog row").isTrue();

        // unpublish -> gone
        publishingService.unpublish(owner.getId(), planner.id());
        assertThat(hasCatalogRow(planner.id())).as("unpublished planner has no catalog row").isFalse();

        // republish then owner delete -> gone
        publishingService.publish(owner.getId(), planner.id());
        assertThat(hasCatalogRow(planner.id())).isTrue();
        commandService.deletePlanner(owner.getId(), planner.id());
        assertThat(hasCatalogRow(planner.id())).as("deleted planner has no catalog row").isFalse();

        // fresh published planner, moderator takedown -> gone
        StoredPlanner takenDown = draft();
        publishingService.publish(owner.getId(), takenDown.id());
        assertThat(hasCatalogRow(takenDown.id())).isTrue();
        plannerModerationService.deletePlanner(admin.getId(), takenDown.id(), "violation");
        assertThat(hasCatalogRow(takenDown.id())).as("taken-down planner has no catalog row").isFalse();
    }

    // recommended-flag-and-detail
    @Test
    void recommendedFlag_WhenVoteAndModerationTransitions_TracksDerivation() {
        StoredPlanner planner = draft();
        publishingService.publish(owner.getId(), planner.id());
        assertThat(catalogRepository.findById(planner.id())
                .map(PlannerCatalog::isRecommended).orElseThrow()).isFalse();

        // votes up to one below the threshold: still not recommended
        for (int i = 0; i < recommendedThreshold - 1; i++) {
            User voter = TestDataFactory.createTestUser(userRepository, "voter" + i + "@example.com");
            engagementService.castVote(voter.getId(), planner.id(), VoteType.UP);
        }
        assertThat(catalogRepository.findById(planner.id())
                .map(PlannerCatalog::isRecommended).orElseThrow())
                .as("below threshold stays unrecommended").isFalse();

        // the crossing vote flips the derived flag
        User crosser = TestDataFactory.createTestUser(userRepository, "crosser@example.com");
        engagementService.castVote(crosser.getId(), planner.id(), VoteType.UP);
        assertThat(catalogRepository.findById(planner.id())
                .map(PlannerCatalog::isRecommended).orElseThrow())
                .as("threshold crossing sets recommended").isTrue();

        // moderation hide clears it; unhide recomputes it from stats
        plannerModerationService.hideFromRecommended(planner.id(), admin.getId(), new HidePlannerRequest("off-list"));
        assertThat(catalogRepository.findById(planner.id())
                .map(PlannerCatalog::isRecommended).orElseThrow())
                .as("hidden planner is not recommended").isFalse();

        plannerModerationService.unhideFromRecommended(planner.id(), admin.getId());
        assertThat(catalogRepository.findById(planner.id())
                .map(PlannerCatalog::isRecommended).orElseThrow())
                .as("unhide restores the stats-derived flag").isTrue();
    }

    // takedown-blocks-republish
    @Test
    void takedownBlocksRepublish_WhenOwnerPublishes_RejectedWithoutCatalogRow() {
        StoredPlanner planner = draft();
        publishingService.publish(owner.getId(), planner.id());
        plannerModerationService.deletePlanner(admin.getId(), planner.id(), "violation");
        assertThat(hasCatalogRow(planner.id())).isFalse();

        assertRejected(() -> publishingService.publish(owner.getId(), planner.id()))
                .as("the aggregate root rejects republishing a taken-down planner")
                .withCode("PLANNER_FORBIDDEN");
        assertThat(hasCatalogRow(planner.id()))
                .as("no catalog row appears for the rejected publish").isFalse();
    }
}
