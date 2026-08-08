package org.danteplanner.backend.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.danteplanner.backend.config.TestConfig;
import org.danteplanner.backend.planner.entity.Planner;
import org.danteplanner.backend.planner.entity.PlannerKeywordFilter;
import org.danteplanner.backend.planner.repository.PlannerEntityFilterRepository;
import org.danteplanner.backend.planner.repository.PlannerKeywordFilterRepository;
import org.danteplanner.backend.planner.repository.PlannerRepository;
import org.danteplanner.backend.planner.validation.PlannerContentEntityExtractor;
import org.danteplanner.backend.planner.service.PlannerFilterService;
import org.danteplanner.backend.user.entity.User;
import org.danteplanner.backend.user.repository.UserRepository;
import org.danteplanner.backend.support.TestDataFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Filter rebuild over existing rows: rebuilding a visible planner's filter
 * indexes while rows from a prior build are present must replace them in one
 * transaction, not collide on the composite primary key.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@ActiveProfiles("it")
@Tag("containerized")
@Import(TestConfig.class)
class PlannerFilterRebuildIT {

    @DynamicPropertySource
    static void ownIndex(DynamicPropertyRegistry registry) {
        SharedMySqlContainerSupport.registerOwnDatabase(registry, "filter_rebuild");
    }





    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PlannerRepository plannerRepository;

    @Autowired
    private PlannerEntityFilterRepository entityFilterRepository;

    @Autowired
    private PlannerKeywordFilterRepository keywordFilterRepository;

    @Autowired
    private PlannerFilterService filterService;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private ObjectMapper objectMapper;

    private User owner;

    @BeforeEach
    void setUp() {
        // First statement of the only @BeforeEach: JUnit does not order sibling
        // @BeforeEach methods, so a separate wipe method could run after setup.
        entityFilterRepository.deleteAll();
        keywordFilterRepository.deleteAll();
        plannerRepository.deleteAll();
        cleanUp();
        owner = TestDataFactory.createTestUser(userRepository, "rebuild-owner@example.com");
    }

    @AfterEach
    void tearDown() {
        cleanUp();
    }

    private void cleanUp() {
    }

    @Test
    @DisplayName("a rebuild over existing filter rows replaces them without a duplicate-key failure")
    void rebuildFilters_WhenRowsAlreadyExist_ReplacesWithoutDuplicateKey() {
        Planner planner = TestDataFactory.planner(owner)
                .selectedKeywords(Set.of("Sinking", "Combustion"))
                .published(true)
                .save(plannerRepository);

        filterService.rebuildFilters(planner.getId());
        long entityRowsAfterFirstBuild = entityFilterRepository.count();
        assertThat(entityRowsAfterFirstBuild).isPositive();

        filterService.rebuildFilters(planner.getId());

        assertThat(entityFilterRepository.count()).isEqualTo(entityRowsAfterFirstBuild);
        assertThat(keywordFilterRepository.findAll())
                .extracting(PlannerKeywordFilter::getKeyword)
                .containsExactlyInAnyOrder("Sinking", "Combustion");
    }

    @Test
    @DisplayName("a rebuild whose read view predates a concurrently committed rebuild still replaces the rows")
    void rebuildFilters_WhenConcurrentRebuildCommittedAfterSnapshot_ReplacesWithoutDuplicateKey() {
        Planner planner = TestDataFactory.planner(owner)
                .selectedKeywords(Set.of("Sinking", "Combustion"))
                .published(true)
                .save(plannerRepository);

        TransactionTemplate outer = new TransactionTemplate(transactionManager);
        TransactionTemplate concurrent = new TransactionTemplate(transactionManager);
        concurrent.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);

        outer.executeWithoutResult(status -> {
            // Pin this transaction's InnoDB read view before the concurrent commit
            assertThat(entityFilterRepository.count()).isZero();

            concurrent.executeWithoutResult(inner -> filterService.rebuildFilters(planner.getId()));

            filterService.rebuildFilters(planner.getId());
        });

        assertThat(keywordFilterRepository.findAll())
                .extracting(PlannerKeywordFilter::getKeyword)
                .containsExactlyInAnyOrder("Sinking", "Combustion");
    }

    @Test
    @DisplayName("the procedure's extraction matches the Java oracle the drift reconciler audits with")
    void rebuildFilters_WhenFactoryContent_MatchesExtractorOracle() throws Exception {
        Planner planner = TestDataFactory.planner(owner)
                .published(true)
                .save(plannerRepository);

        filterService.rebuildFilters(planner.getId());

        Set<String> expected = PlannerContentEntityExtractor
                .extract(objectMapper.readTree(planner.getContentJson()))
                .stream()
                .map(ref -> ref.type().name() + ":" + ref.id())
                .collect(Collectors.toSet());
        Set<String> actual = entityFilterRepository.findAll().stream()
                .filter(f -> f.getPlannerId().equals(planner.getId()))
                .map(f -> f.getEntityType().name() + ":" + f.getEntityId())
                .collect(Collectors.toSet());

        assertThat(expected).isNotEmpty();
        assertThat(actual).isEqualTo(expected);
    }

    @Test
    @DisplayName("enhanced gift ids index under their base, collapsing onto an existing base row")
    void rebuildFilters_WhenEnhancedGiftIds_IndexesBaseIdOnly() throws Exception {
        String content = """
                {"equipment":{},
                 "selectedGiftIds":["9154","19154","29154"],
                 "observationGiftIds":["19001"],
                 "comprehensiveGiftIds":[],
                 "floorSelections":[{"giftIds":["29002"],"themePackId":null}]}
                """;
        Planner planner = TestDataFactory.planner(owner)
                .published(true)
                .content(content)
                .save(plannerRepository);

        filterService.rebuildFilters(planner.getId());

        Set<Integer> gifts = entityFilterRepository.findAll().stream()
                .filter(f -> f.getPlannerId().equals(planner.getId()))
                .filter(f -> f.getEntityType().name().equals("EGO_GIFT"))
                .map(f -> f.getEntityId())
                .collect(Collectors.toSet());

        // 9154/19154/29154 are one gift, so the three collapse to a single row.
        assertThat(gifts).containsExactlyInAnyOrder(9154, 9001, 9002);

        Set<String> oracle = PlannerContentEntityExtractor
                .extract(objectMapper.readTree(content))
                .stream()
                .filter(ref -> ref.type().name().equals("EGO_GIFT"))
                .map(ref -> String.valueOf(ref.id()))
                .collect(Collectors.toSet());
        assertThat(oracle).isEqualTo(gifts.stream().map(String::valueOf).collect(Collectors.toSet()));
    }
}
