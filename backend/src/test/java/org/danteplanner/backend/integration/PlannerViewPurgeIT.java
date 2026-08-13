package org.danteplanner.backend.integration;

import org.danteplanner.backend.config.TestConfig;
import org.danteplanner.backend.planner.entity.Planner;
import org.danteplanner.backend.planner.repository.PlannerRepository;
import org.danteplanner.backend.support.TestDataFactory;
import org.danteplanner.backend.user.entity.User;
import org.danteplanner.backend.user.repository.UserRepository;
import org.danteplanner.backend.user.service.UserAccountLifecycleService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

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
    private UserRepository userRepository;

    @Autowired
    private PlannerRepository plannerRepository;

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

    private Planner plannerWithViews(User plannerOwner, String title, int days) {
        Planner planner = TestDataFactory.planner(plannerOwner)
                .title(title)
                .published(true)
                .save(plannerRepository);
        viewedPlanners.add(planner.getId());
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        for (int day = 0; day < days; day++) {
            jdbc.update("INSERT INTO planner_views (planner_id, viewer_hash, view_date, created_at) "
                            + "VALUES (UUID_TO_BIN(?), SHA2(?, 256), ?, NOW())",
                    planner.getId().toString(), "viewer-" + day, today.minusDays(day));
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
        User managed = userRepository.findById(userId).orElseThrow();
        managed.softDelete(Instant.now().minusSeconds(60));
        userRepository.save(managed);
        assertThat(lifecycleService.performHardDelete(userId, Instant.now())).isTrue();
    }

    @Test
    @DisplayName("view-rows-are-swept-app-side: a hard-deleted account's planner leaves no view rows behind, with no foreign key to carry them off")
    void plannerViews_WhenOwnerAccountHardDeleted_AreDeletedWithoutForeignKeySupport() {
        assertNoForeignKeyBacksTheSweep();
        Planner viewed = plannerWithViews(owner, "Swept Views", 3);
        Planner alsoViewed = plannerWithViews(owner, "Swept Views Too", 2);
        assertThat(viewRowsFor(viewed.getId())).isEqualTo(3);
        assertThat(viewRowsFor(alsoViewed.getId())).isEqualTo(2);

        purge(owner.getId());

        assertThat(userRepository.findById(owner.getId())).as("the account row is gone").isEmpty();
        for (Planner planner : List.of(viewed, alsoViewed)) {
            assertThat(coreRowsFor(planner.getId()))
                    .as("the planner core row for %s is gone", planner.getId())
                    .isZero();
            assertThat(viewRowsFor(planner.getId()))
                    .as("no view row survives planner %s", planner.getId())
                    .isZero();
        }
    }

    @Test
    @DisplayName("the-sweep-is-scoped-to-the-account: another owner's planner keeps its view rows through the purge")
    void plannerViews_WhenAnotherAccountHardDeleted_SurviveOnTheUntouchedPlanner() {
        assertNoForeignKeyBacksTheSweep();
        Planner theirs = plannerWithViews(other, "Kept Views", 2);
        plannerWithViews(owner, "Swept Views Elsewhere", 1);

        purge(owner.getId());

        assertThat(coreRowsFor(theirs.getId()))
                .as("the third party's planner is untouched")
                .isEqualTo(1);
        assertThat(viewRowsFor(theirs.getId()))
                .as("the set-based delete named the purged account's planners and no others")
                .isEqualTo(2);
    }
}
