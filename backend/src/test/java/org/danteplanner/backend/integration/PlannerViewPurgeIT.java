package org.danteplanner.backend.integration;

import org.danteplanner.backend.config.TestConfig;
import org.danteplanner.backend.planner.repository.StoredPlanner;
import org.danteplanner.backend.support.PlannerSeeds;
import org.danteplanner.backend.support.TestDataFactory;
import org.danteplanner.backend.user.entity.User;
import org.danteplanner.backend.user.repository.UserRepository;
import org.danteplanner.backend.user.service.UserAccountLifecycleService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * View rows are removed by the account purge itself, not by the database.
 *
 * <p>{@code planner_views} carries no foreign key to the planner core, so nothing in the schema
 * removes a planner's view rows when the core row goes; the sweep in the purge path is the only
 * thing standing between a deleted account and rows naming a planner that no longer exists. Each
 * test asserts the constraint is absent first, or a surviving cascade would satisfy the assertion
 * and the sweep could be deleted without a failure.</p>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@ActiveProfiles("it")
@Tag("containerized")
@Import(TestConfig.class)
class PlannerViewPurgeIT extends SharedMySqlContainerSupport {

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PlannerSeeds plannerSeeds;

    @Autowired
    private UserAccountLifecycleService lifecycleService;

    @Autowired
    private DataSource dataSource;

    private JdbcTemplate jdbc;
    private User owner;
    private User other;
    private final List<UUID> viewedPlanners = new ArrayList<>();

    @BeforeEach
    void setUp() {
        jdbc = new JdbcTemplate(dataSource);
        owner = TestDataFactory.createTestUser(userRepository, "view-purge-owner@example.com");
        other = TestDataFactory.createTestUser(userRepository, "view-purge-other@example.com");
    }

    /**
     * View rows outlive the planner they name now that the constraint is gone, so a test that
     * leaves them behind leaks rows into the database its neighbours share.
     */
    @AfterEach
    void tearDown() {
        for (UUID plannerId : viewedPlanners) {
            jdbc.update("DELETE FROM planner_views WHERE planner_id = UUID_TO_BIN(?)",
                    plannerId.toString());
        }
        viewedPlanners.clear();
    }

    private void assertNoForeignKeyBacksTheSweep() {
        Integer constraints = jdbc.queryForObject(
                "SELECT COUNT(*) FROM information_schema.table_constraints "
                        + "WHERE constraint_schema = DATABASE() AND table_name = 'planner_views' "
                        + "AND constraint_name = 'fk_view_planner'",
                Integer.class);
        assertThat(constraints)
                .as("planner_views owns no foreign key, so no cascade can stand in for the sweep")
                .isZero();
    }

    private StoredPlanner plannerWithViews(User plannerOwner, String title, int days) {
        StoredPlanner planner = TestDataFactory.planner(plannerOwner)
                .title(title)
                .published(true)
                .save(plannerSeeds);
        viewedPlanners.add(planner.id());
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        for (int day = 0; day < days; day++) {
            jdbc.update("INSERT INTO planner_views (planner_id, viewer_hash, view_date, created_at) "
                            + "VALUES (UUID_TO_BIN(?), SHA2(?, 256), ?, NOW())",
                    planner.id().toString(), "viewer-" + day, today.minusDays(day));
        }
        return planner;
    }

    private int viewRowsFor(UUID plannerId) {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM planner_views WHERE planner_id = UUID_TO_BIN(?)",
                Integer.class, plannerId.toString());
        return count != null ? count : -1;
    }

    private int coreRowsFor(UUID plannerId) {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM planner WHERE id = UUID_TO_BIN(?)",
                Integer.class, plannerId.toString());
        return count != null ? count : -1;
    }

    /**
     * Make the account purge-eligible, then purge it. {@code performHardDelete} re-reads
     * eligibility under a lock, so a live account is skipped rather than deleted.
     */
    private void purge(Long userId) {
        transactionTemplate.executeWithoutResult(status ->
                userRepository.softDelete(userId, Instant.now(), Instant.now().minusSeconds(60)));
        assertThat(lifecycleService.performHardDelete(userId, Instant.now())).isTrue();
    }

    @Test
    void plannerViews_WhenOwnerAccountHardDeleted_AreDeletedWithoutForeignKeySupport() {
        assertNoForeignKeyBacksTheSweep();
        StoredPlanner viewed = plannerWithViews(owner, "Swept Views", 3);
        StoredPlanner alsoViewed = plannerWithViews(owner, "Swept Views Too", 2);
        assertThat(viewRowsFor(viewed.id())).isEqualTo(3);
        assertThat(viewRowsFor(alsoViewed.id())).isEqualTo(2);

        purge(owner.getId());

        assertThat(userRepository.findById(owner.getId())).as("the account row is gone").isEmpty();
        for (StoredPlanner planner : List.of(viewed, alsoViewed)) {
            assertThat(coreRowsFor(planner.id()))
                    .as("the planner core row for %s is gone", planner.id())
                    .isZero();
            assertThat(viewRowsFor(planner.id()))
                    .as("no view row survives planner %s", planner.id())
                    .isZero();
        }
    }

    @Test
    void plannerViews_WhenAnotherAccountHardDeleted_SurviveOnTheUntouchedPlanner() {
        assertNoForeignKeyBacksTheSweep();
        StoredPlanner theirs = plannerWithViews(other, "Kept Views", 2);
        plannerWithViews(owner, "Swept Views Elsewhere", 1);

        purge(owner.getId());

        assertThat(coreRowsFor(theirs.id()))
                .as("the third party's planner is untouched")
                .isEqualTo(1);
        assertThat(viewRowsFor(theirs.id()))
                .as("the set-based delete named the purged account's planners and no others")
                .isEqualTo(2);
    }
}
