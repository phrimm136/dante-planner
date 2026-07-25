package org.danteplanner.backend.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.danteplanner.backend.config.TestConfig;
import org.danteplanner.backend.planner.dto.UpsertPlannerRequest;
import org.danteplanner.backend.planner.entity.Planner;
import org.danteplanner.backend.planner.entity.PlannerCatalog;
import org.danteplanner.backend.planner.entity.PlannerEntityFilter;
import org.danteplanner.backend.planner.entity.PlannerKeywordFilter;
import org.danteplanner.backend.planner.entity.PlannerStats;
import org.danteplanner.backend.planner.entity.PlannerStatus;
import org.danteplanner.backend.planner.entity.PlannerType;
import org.danteplanner.backend.planner.repository.PlannerCatalogRepository;
import org.danteplanner.backend.planner.repository.PlannerContentRepository;
import org.danteplanner.backend.planner.repository.PlannerEntityFilterRepository;
import org.danteplanner.backend.planner.repository.PlannerKeywordFilterRepository;
import org.danteplanner.backend.planner.repository.PlannerPublicationRepository;
import org.danteplanner.backend.planner.repository.PlannerRepository;
import org.danteplanner.backend.planner.repository.PlannerStatsRepository;
import org.danteplanner.backend.planner.service.PlannerCatalogService;
import org.danteplanner.backend.planner.service.PlannerCommandService;
import org.danteplanner.backend.planner.service.PlannerContentEntityExtractor;
import org.danteplanner.backend.planner.service.PlannerFilterService;
import org.danteplanner.backend.shared.entity.ContentEntityType;
import org.danteplanner.backend.support.TestDataCleanup;
import org.danteplanner.backend.support.TestDataFactory;
import org.danteplanner.backend.user.entity.User;
import org.danteplanner.backend.user.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Owner write commands over their committed projections: an edit that changes the
 * searchable composition rebuilds both filter indexes while an edit that does not
 * leaves them alone, every visible edit republishes the catalog's scalar copies,
 * and a soft delete withdraws the planner from the owner load path, the catalog,
 * and both filter indexes at once.
 *
 * <p>The rebuild and the clear run in an {@code AFTER_COMMIT} listener, so the
 * command is driven without a surrounding test transaction; the listener has
 * already committed by the time the command returns.</p>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@ActiveProfiles("it")
@Tag("containerized")
@Import(TestConfig.class)
class PlannerCommandFlowIT extends SharedMySqlContainerSupport {

    @DynamicPropertySource
    static void registerMySqlProperties(DynamicPropertyRegistry registry) {
        registerSharedMysql(registry, "planner_command_flow_it");
    }

    /**
     * A theme-pack id no fixture content yields. Planted in the entity index, it survives
     * exactly while no rebuild runs, which is what makes the skipped rebuild observable as
     * state rather than as a call.
     */
    private static final int UNREACHABLE_ENTITY_ID = 424242;

    private static final String PLANTED_ROW = ContentEntityType.THEME_PACK + ":" + UNREACHABLE_ENTITY_ID;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PlannerRepository plannerRepository;

    @Autowired
    private PlannerContentRepository contentRepository;

    @Autowired
    private PlannerPublicationRepository publicationRepository;

    @Autowired
    private PlannerCatalogRepository catalogRepository;

    @Autowired
    private PlannerStatsRepository statsRepository;

    @Autowired
    private PlannerEntityFilterRepository entityFilterRepository;

    @Autowired
    private PlannerKeywordFilterRepository keywordFilterRepository;

    @Autowired
    private PlannerCommandService commandService;

    @Autowired
    private PlannerCatalogService catalogService;

    @Autowired
    private PlannerFilterService filterService;

    @Autowired
    private ObjectMapper objectMapper;

    private User owner;
    private UUID deviceId;

    @BeforeEach
    void setUp() {
        cleanUp();
        owner = TestDataFactory.createTestUser(userRepository, "command-flow-owner@example.com");
        deviceId = UUID.randomUUID();
    }

    @AfterEach
    void tearDown() {
        cleanUp();
    }

    private void cleanUp() {
        entityFilterRepository.deleteAll();
        keywordFilterRepository.deleteAll();
        catalogRepository.deleteAll();
        statsRepository.deleteAll();
        plannerRepository.deleteAll();
        TestDataCleanup.deleteUsersExceptSentinel(userRepository);
    }

    private Planner publishedPlanner(String title, Set<String> keywords) {
        Planner planner = TestDataFactory.planner(owner)
                .title(title)
                .selectedKeywords(keywords)
                .published(true)
                .save(plannerRepository);
        statsRepository.save(PlannerStats.builder().plannerId(planner.getId()).build());
        catalogService.add(planner);
        filterService.rebuildFilters(planner.getId());
        // MySQL re-serializes a JSON column, so "the content did not change" is only
        // expressible against the stored form, not the string handed to the insert.
        return plannerRepository.findAggregateForOwner(planner.getId(), owner.getId()).orElseThrow();
    }

    private void plantUnreachableEntityRow(UUID plannerId) {
        entityFilterRepository.save(
                new PlannerEntityFilter(ContentEntityType.THEME_PACK, UNREACHABLE_ENTITY_ID, plannerId));
    }

    private Set<String> entityIndex(UUID plannerId) {
        return entityFilterRepository.findAll().stream()
                .filter(row -> row.getPlannerId().equals(plannerId))
                .map(row -> row.getEntityType() + ":" + row.getEntityId())
                .collect(Collectors.toSet());
    }

    private Set<String> keywordIndex(UUID plannerId) {
        return keywordFilterRepository.findAll().stream()
                .filter(row -> row.getPlannerId().equals(plannerId))
                .map(PlannerKeywordFilter::getKeyword)
                .collect(Collectors.toSet());
    }

    private Set<String> extractorOracle(String contentJson) throws Exception {
        return PlannerContentEntityExtractor.extract(objectMapper.readTree(contentJson)).stream()
                .map(ref -> ref.type() + ":" + ref.id())
                .collect(Collectors.toSet());
    }

    private UpsertPlannerRequest edit(Planner planner, String title, Set<String> keywords) {
        return new UpsertPlannerRequest(
                planner.getId().toString(),
                planner.getCategory(),
                title,
                PlannerStatus.SAVED,
                planner.getContentJson(),
                planner.getContentVersion(),
                PlannerType.MIRROR_DUNGEON,
                planner.getSyncVersion(),
                keywords);
    }

    @Test
    @DisplayName("an edit that leaves the searchable composition alone syncs the catalog copies and never touches the filter index")
    void an_edit_without_a_composition_change_leaves_the_filter_index_untouched() {
        Planner planner = publishedPlanner("Before Edit", Set.of("Sinking", "Combustion"));
        UUID plannerId = planner.getId();
        plantUnreachableEntityRow(plannerId);
        Set<String> entityIndexBefore = entityIndex(plannerId);

        commandService.upsertPlanner(owner.getId(), deviceId, plannerId,
                edit(planner, "After Edit", null), false);

        PlannerCatalog catalogRow = catalogRepository.findById(plannerId).orElseThrow();
        assertThat(catalogRow.getTitle())
                .as("the catalog scalar copy carries the new title")
                .isEqualTo("After Edit");
        assertThat(catalogRow.getSelectedKeywords())
                .containsExactlyInAnyOrder("Sinking", "Combustion");

        assertThat(entityIndex(plannerId))
                .as("no rebuild ran: a rebuild clears every row for the planner, "
                        + "so the planted row would be gone")
                .contains(PLANTED_ROW)
                .isEqualTo(entityIndexBefore);
        assertThat(keywordIndex(plannerId))
                .containsExactlyInAnyOrder("Sinking", "Combustion");
    }

    @Test
    @DisplayName("an edit that changes the searchable composition rebuilds both filter indexes from the committed row")
    void an_edit_with_a_composition_change_rebuilds_both_filter_indexes() throws Exception {
        Planner planner = publishedPlanner("Keyword Edit", Set.of("Sinking"));
        UUID plannerId = planner.getId();
        plantUnreachableEntityRow(plannerId);

        commandService.upsertPlanner(owner.getId(), deviceId, plannerId,
                edit(planner, "Keyword Edit", Set.of("Combustion", "Burst")), false);

        assertThat(keywordIndex(plannerId))
                .as("the keyword index is re-extracted from the committed keyword set")
                .containsExactlyInAnyOrder("Combustion", "Burst");
        assertThat(entityIndex(plannerId))
                .as("the entity index is re-extracted from the committed content, "
                        + "dropping the planted row the content does not yield")
                .doesNotContain(PLANTED_ROW)
                .isEqualTo(extractorOracle(planner.getContentJson()));
        assertThat(catalogRepository.findById(plannerId).orElseThrow().getSelectedKeywords())
                .as("the catalog scalar copy carries the normalized keyword set")
                .containsExactlyInAnyOrder("Combustion", "Burst");
    }

    @Test
    @DisplayName("a soft delete persists the deletion stamp and withdraws the planner from the owner path, the catalog, and both filter indexes")
    void a_soft_delete_withdraws_the_planner_from_every_projection() {
        Planner planner = publishedPlanner("Doomed", Set.of("Sinking"));
        UUID plannerId = planner.getId();
        assertThat(entityIndex(plannerId)).isNotEmpty();
        assertThat(keywordIndex(plannerId)).isNotEmpty();

        commandService.deletePlanner(owner.getId(), deviceId, plannerId);

        assertThat(contentRepository.findById(plannerId).orElseThrow().getDeletedAt())
                .as("the deletion stamp is on the committed row, not only on the in-memory aggregate")
                .isNotNull();
        assertThat(publicationRepository.findById(plannerId).orElseThrow().getPublished())
                .as("the auto-unpublish that precedes the delete is committed too")
                .isFalse();
        assertThat(plannerRepository.findAggregateForOwner(plannerId, owner.getId()))
                .as("the owner load path no longer returns it")
                .isEmpty();
        assertThat(plannerRepository.countActiveByUserId(owner.getId()))
                .as("it stops counting against the per-user limit")
                .isZero();
        assertThat(catalogRepository.existsById(plannerId))
                .as("the browse projection drops it")
                .isFalse();
        assertThat(entityIndex(plannerId)).isEmpty();
        assertThat(keywordIndex(plannerId)).isEmpty();
    }
}
