package org.danteplanner.backend.integration;

import net.javacrumbs.shedlock.core.LockProvider;
import org.danteplanner.backend.config.TestConfig;
import org.danteplanner.backend.planner.entity.Planner;
import org.danteplanner.backend.planner.entity.PlannerStats;
import org.danteplanner.backend.planner.exception.PlannerNotFoundException;
import org.danteplanner.backend.planner.repository.PlannerRepository;
import org.danteplanner.backend.planner.repository.PlannerStatsRepository;
import org.danteplanner.backend.planner.service.PlannerAccessGuard;
import org.danteplanner.backend.planner.service.PlannerCatalogService;
import org.danteplanner.backend.planner.service.PlannerDriftReconciler;
import org.danteplanner.backend.planner.service.PlannerDriftReconciler.DriftRecord;
import org.danteplanner.backend.user.entity.User;
import org.danteplanner.backend.user.repository.UserRepository;
import org.danteplanner.backend.user.service.UserAccountLifecycleService;
import org.danteplanner.backend.support.TestDataFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import javax.sql.DataSource;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * A deactivated owner's published planners are invisible to public reads and to the drift
 * audit's catalog membership for the grace window, and visible again on reactivation.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@ActiveProfiles("it")
@Tag("containerized")
@Import({TestConfig.class, DeactivatedOwnerVisibilityIT.GrantedLockHarness.class})
class DeactivatedOwnerVisibilityIT extends SharedMySqlContainerSupport {

    @TestConfiguration
    static class GrantedLockHarness {

        @Bean
        @Primary
        LockProvider grantedLockProvider() {
            return configuration -> Optional.of(() -> { });
        }
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PlannerRepository plannerRepository;

    @Autowired
    private PlannerStatsRepository statsRepository;

    @Autowired
    private PlannerCatalogService catalogService;

    @Autowired
    private PlannerAccessGuard accessGuard;

    @Autowired
    private UserAccountLifecycleService lifecycleService;

    @Autowired
    private PlannerDriftReconciler reconciler;

    @Autowired
    private DataSource dataSource;

    private User owner;
    private Planner planner;

    @BeforeEach
    void setUp() {
        owner = TestDataFactory.createTestUser(userRepository, "deactivated-owner@example.com");
        planner = TestDataFactory.createTestPlanner(plannerRepository, owner, true);
        statsRepository.save(PlannerStats.builder().plannerId(planner.getId()).build());
        catalogService.add(planner);
    }

    private Integer catalogRows(UUID plannerId) {
        return new JdbcTemplate(dataSource).queryForObject(
                "SELECT COUNT(*) FROM planner_catalog WHERE planner_id = UUID_TO_BIN(?)",
                Integer.class, plannerId.toString());
    }

    private List<DriftRecord> membershipRecordsFor(List<DriftRecord> records, UUID plannerId) {
        return records.stream()
                .filter(r -> r.plannerId().equals(plannerId) && r.kind().equals("catalog_membership"))
                .toList();
    }

    @Test
    void publishedDetail_WhenOwnerActive_Returns200() throws Exception {
        mockMvc.perform(get("/api/planner/md/published/{id}", planner.getId()))
                .andExpect(status().isOk());
    }

    @Test
    void publishedDetail_WhenOwnerDeactivated_Returns404PlannerNotFound() throws Exception {
        lifecycleService.deleteAccount(owner.getId());

        mockMvc.perform(get("/api/planner/md/published/{id}", planner.getId()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PLANNER_NOT_FOUND"));
    }

    @Test
    void publishedDetail_WhenOwnerReactivated_Returns200() throws Exception {
        lifecycleService.deleteAccount(owner.getId());
        lifecycleService.reactivateAccount(owner.getId());

        mockMvc.perform(get("/api/planner/md/published/{id}", planner.getId()))
                .andExpect(status().isOk());
    }

    @Test
    void checkPublished_WhenOwnerDeactivated_ThrowsPlannerNotFound() {
        lifecycleService.deleteAccount(owner.getId());

        assertThatThrownBy(() -> accessGuard.checkPublished(planner.getId()))
                .isInstanceOf(PlannerNotFoundException.class);
    }

    @Test
    void reconcile_WhenOwnerDeactivated_ReportsNoMissingCatalogRow() {
        lifecycleService.deleteAccount(owner.getId());

        List<DriftRecord> records = reconciler.reconcile();

        assertThat(membershipRecordsFor(records, planner.getId()))
                .as("a withdrawn row of a deactivated owner is not membership drift")
                .isEmpty();
        assertThat(catalogRows(planner.getId())).isZero();
    }

    @Test
    void reconcile_WhenDeactivatedOwnerKeepsCatalogRow_ReportsRowPresent() {
        lifecycleService.deleteAccount(owner.getId());
        catalogService.add(planner);

        List<DriftRecord> records = reconciler.reconcile();

        assertThat(membershipRecordsFor(records, planner.getId()))
                .extracting(DriftRecord::expected, DriftRecord::actual)
                .containsExactly(tuple("row absent", "row present"));
    }
}
