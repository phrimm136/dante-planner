package org.danteplanner.backend.integration;

import org.danteplanner.backend.config.TestConfig;
import static org.hamcrest.Matchers.hasItems;
import org.danteplanner.backend.moderation.dto.HidePlannerRequest;
import org.danteplanner.backend.moderation.repository.ModerationActionRepository;
import org.danteplanner.backend.moderation.service.PlannerModerationService;
import org.danteplanner.backend.planner.entity.Planner;
import org.danteplanner.backend.planner.entity.PlannerCatalog;
import org.danteplanner.backend.planner.entity.PlannerStats;
import org.danteplanner.backend.planner.entity.VoteType;
import org.danteplanner.backend.planner.exception.PlannerForbiddenException;
import org.danteplanner.backend.planner.repository.PlannerCatalogRepository;
import org.danteplanner.backend.planner.repository.PlannerRepository;
import org.danteplanner.backend.planner.repository.PlannerStatsRepository;
import org.danteplanner.backend.planner.repository.PlannerVoteRepository;
import org.danteplanner.backend.planner.service.PlannerCatalogService;
import org.danteplanner.backend.planner.service.PlannerCommandService;
import org.danteplanner.backend.planner.service.PlannerEngagementService;
import org.danteplanner.backend.planner.service.PlannerPublishingService;
import org.danteplanner.backend.user.entity.User;
import org.danteplanner.backend.user.repository.UserRepository;
import org.danteplanner.backend.support.TestDataFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import javax.sql.DataSource;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Catalog projection lifecycle: membership equals visibility, ordering is
 * recency riding the catalog index, the recommended flag follows vote and
 * moderation transitions, and a takedown pins the aggregate unpublishable.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@ActiveProfiles("it")
@Tag("containerized")
@Import(TestConfig.class)
class PlannerCatalogLifecycleIT extends SharedMySqlContainerSupport {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PlannerRepository plannerRepository;

    @Autowired
    private PlannerCatalogRepository catalogRepository;

    @Autowired
    private PlannerStatsRepository statsRepository;

    @Autowired
    private PlannerVoteRepository voteRepository;

    @Autowired
    private ModerationActionRepository moderationActionRepository;

    @Autowired
    private PlannerCommandService commandService;

    @Autowired
    private PlannerPublishingService publishingService;

    @Autowired
    private PlannerEngagementService engagementService;

    @Autowired
    private PlannerCatalogService catalogService;

    @Autowired
    private PlannerModerationService plannerModerationService;

    @Autowired
    private DataSource dataSource;

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

    private Planner draft() {
        Planner planner = TestDataFactory.createTestPlanner(plannerRepository, owner, false);
        statsRepository.save(PlannerStats.builder().plannerId(planner.getId()).build());
        return planner;
    }

    private boolean hasCatalogRow(UUID plannerId) {
        return catalogRepository.existsById(plannerId);
    }

    @Test
    @DisplayName("catalog-membership-on-transition: row exists after publish, absent after unpublish, delete, and takedown")
    void catalogMembership_WhenVisibilityTransitions_RowPresenceMatches() {
        // publish -> row
        Planner planner = draft();
        publishingService.setPublished(owner.getId(), planner.getId(), true);
        assertThat(hasCatalogRow(planner.getId())).as("published planner has a catalog row").isTrue();

        // unpublish -> gone
        publishingService.setPublished(owner.getId(), planner.getId(), false);
        assertThat(hasCatalogRow(planner.getId())).as("unpublished planner has no catalog row").isFalse();

        // republish then owner delete -> gone
        publishingService.setPublished(owner.getId(), planner.getId(), true);
        assertThat(hasCatalogRow(planner.getId())).isTrue();
        commandService.deletePlanner(owner.getId(), null, planner.getId());
        assertThat(hasCatalogRow(planner.getId())).as("deleted planner has no catalog row").isFalse();

        // fresh published planner, moderator takedown -> gone
        Planner takenDown = draft();
        publishingService.setPublished(owner.getId(), takenDown.getId(), true);
        assertThat(hasCatalogRow(takenDown.getId())).isTrue();
        plannerModerationService.deletePlanner(admin.getId(), takenDown.getId(), "violation");
        assertThat(hasCatalogRow(takenDown.getId())).as("taken-down planner has no catalog row").isFalse();
    }

    @Test
    @DisplayName("list-recency-sort: newest first_published_at first, served without a filesort")
    void listRecencySort_WhenBrowsing_NewestFirstFilesortFree() throws Exception {
        Instant now = Instant.now();
        for (int i = 0; i < 3; i++) {
            Planner planner = TestDataFactory.planner(owner)
                    .title("Recency " + i)
                    .published(true)
                    .firstPublishedAt(now.minus(3 - i, ChronoUnit.HOURS))
                    .save(plannerRepository);
            statsRepository.save(PlannerStats.builder().plannerId(planner.getId()).build());
            catalogService.add(planner);
        }

        mockMvc.perform(get("/api/planner/md/published"))
                .andExpect(status().isOk())
                // Relative order among this test's rows: the catalog also holds its neighbours',
                // so absolute positions belong to whoever published most recently.
                .andExpect(jsonPath("$.content[*].title", hasItems("Recency 2", "Recency 1", "Recency 0")))
                .andExpect(result -> {
                    List<String> titles = com.jayway.jsonpath.JsonPath.read(
                            result.getResponse().getContentAsString(), "$.content[*].title");
                    assertThat(titles.indexOf("Recency 2")).isLessThan(titles.indexOf("Recency 1"));
                    assertThat(titles.indexOf("Recency 1")).isLessThan(titles.indexOf("Recency 0"));
                });

        // The browse shape must ride idx_catalog_recent instead of sorting rows
        String plan = new JdbcTemplate(dataSource).queryForList(
                        "EXPLAIN SELECT planner_id FROM planner_catalog "
                                + "ORDER BY first_published_at DESC LIMIT 20")
                .toString();
        assertThat(plan)
                .as("recency browse rides idx_catalog_recent without a filesort: %s", plan)
                .doesNotContain("Using filesort");
    }

    @Test
    @DisplayName("recommended-flag-and-detail: vote crossing sets catalog.recommended; hide clears it; unhide restores it")
    void recommendedFlag_WhenVoteAndModerationTransitions_TracksDerivation() {
        Planner planner = draft();
        publishingService.setPublished(owner.getId(), planner.getId(), true);
        assertThat(catalogRepository.findById(planner.getId())
                .map(PlannerCatalog::getRecommended).orElseThrow()).isFalse();

        // votes up to one below the threshold: still not recommended
        for (int i = 0; i < recommendedThreshold - 1; i++) {
            User voter = TestDataFactory.createTestUser(userRepository, "voter" + i + "@example.com");
            engagementService.castVote(voter.getId(), planner.getId(), VoteType.UP);
        }
        assertThat(catalogRepository.findById(planner.getId())
                .map(PlannerCatalog::getRecommended).orElseThrow())
                .as("below threshold stays unrecommended").isFalse();

        // the crossing vote flips the derived flag
        User crosser = TestDataFactory.createTestUser(userRepository, "crosser@example.com");
        engagementService.castVote(crosser.getId(), planner.getId(), VoteType.UP);
        assertThat(catalogRepository.findById(planner.getId())
                .map(PlannerCatalog::getRecommended).orElseThrow())
                .as("threshold crossing sets recommended").isTrue();

        // moderation hide clears it; unhide recomputes it from stats
        plannerModerationService.hideFromRecommended(planner.getId(), admin.getId(), new HidePlannerRequest("off-list"));
        assertThat(catalogRepository.findById(planner.getId())
                .map(PlannerCatalog::getRecommended).orElseThrow())
                .as("hidden planner is not recommended").isFalse();

        plannerModerationService.unhideFromRecommended(planner.getId(), admin.getId());
        assertThat(catalogRepository.findById(planner.getId())
                .map(PlannerCatalog::getRecommended).orElseThrow())
                .as("unhide restores the stats-derived flag").isTrue();
    }

    @Test
    @DisplayName("takedown-blocks-republish: a moderator-taken-down planner rejects the owner's publish and gains no catalog row")
    void takedownBlocksRepublish_WhenOwnerPublishes_RejectedWithoutCatalogRow() {
        Planner planner = draft();
        publishingService.setPublished(owner.getId(), planner.getId(), true);
        plannerModerationService.deletePlanner(admin.getId(), planner.getId(), "violation");
        assertThat(hasCatalogRow(planner.getId())).isFalse();

        assertThatThrownBy(() -> publishingService.setPublished(owner.getId(), planner.getId(), true))
                .as("the aggregate root rejects republishing a taken-down planner")
                .isInstanceOf(PlannerForbiddenException.class);
        assertThat(hasCatalogRow(planner.getId()))
                .as("no catalog row appears for the rejected publish").isFalse();
    }
}
