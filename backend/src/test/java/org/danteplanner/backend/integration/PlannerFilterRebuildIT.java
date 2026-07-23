package org.danteplanner.backend.integration;

import org.danteplanner.backend.config.TestConfig;
import org.danteplanner.backend.planner.entity.Planner;
import org.danteplanner.backend.planner.entity.PlannerKeywordFilter;
import org.danteplanner.backend.planner.repository.PlannerEntityFilterRepository;
import org.danteplanner.backend.planner.repository.PlannerKeywordFilterRepository;
import org.danteplanner.backend.planner.repository.PlannerRepository;
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
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Set;

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
class PlannerFilterRebuildIT extends SharedMySqlContainerSupport {

    @DynamicPropertySource
    static void registerMySqlProperties(DynamicPropertyRegistry registry) {
        registerSharedMysql(registry, "planner_filter_rebuild_it");
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

    private User owner;

    @BeforeEach
    void setUp() {
        cleanUp();
        owner = TestDataFactory.createTestUser(userRepository, "rebuild-owner@example.com");
    }

    @AfterEach
    void tearDown() {
        cleanUp();
    }

    private void cleanUp() {
        entityFilterRepository.deleteAll();
        keywordFilterRepository.deleteAll();
        plannerRepository.deleteAll();
        userRepository.findAll().stream()
                .filter(u -> u.getId() != 0L)
                .forEach(userRepository::delete);
    }

    @Test
    @DisplayName("a rebuild over existing filter rows replaces them without a duplicate-key failure")
    void rebuildFilters_WhenRowsAlreadyExist_ReplacesWithoutDuplicateKey() {
        Planner planner = TestDataFactory.planner(owner)
                .selectedKeywords(Set.of("Sinking", "Combustion"))
                .published(true)
                .save(plannerRepository);

        filterService.rebuildFilters(planner.getId(), planner.getContentJson(), planner.getSelectedKeywords());
        long entityRowsAfterFirstBuild = entityFilterRepository.count();
        assertThat(entityRowsAfterFirstBuild).isPositive();

        filterService.rebuildFilters(planner.getId(), planner.getContentJson(), planner.getSelectedKeywords());

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

            concurrent.executeWithoutResult(inner -> filterService.rebuildFilters(
                    planner.getId(), planner.getContentJson(), planner.getSelectedKeywords()));

            filterService.rebuildFilters(
                    planner.getId(), planner.getContentJson(), planner.getSelectedKeywords());
        });

        assertThat(keywordFilterRepository.findAll())
                .extracting(PlannerKeywordFilter::getKeyword)
                .containsExactlyInAnyOrder("Sinking", "Combustion");
    }
}
