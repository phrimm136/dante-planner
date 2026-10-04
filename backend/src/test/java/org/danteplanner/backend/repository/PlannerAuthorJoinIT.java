package org.danteplanner.backend.repository;

import jakarta.persistence.EntityManager;
import org.danteplanner.backend.config.TestConfig;
import org.danteplanner.backend.integration.SharedMySqlContainerSupport;
import org.danteplanner.backend.planner.api.PlannerThread;
import org.danteplanner.backend.planner.entity.Planner;
import org.danteplanner.backend.planner.repository.PlannerRepository;
import org.danteplanner.backend.planner.repository.PlannerSummaryRow;
import org.danteplanner.backend.planner.repository.PublishedPlannerRow;
import org.danteplanner.backend.support.TestDataFactory;
import org.danteplanner.backend.support.ThreadStatementCounter;
import org.danteplanner.backend.user.entity.User;
import org.danteplanner.backend.user.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("it")
@Tag("containerized")
@Import(TestConfig.class)
class PlannerAuthorJoinIT extends SharedMySqlContainerSupport {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PlannerRepository plannerRepository;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private record Counted<T>(T value, long statements) {
    }

    @Test
    @DisplayName("the published read carries the author's epithet and suffix in one statement")
    void findPublishedAggregate_WhenPublished_ReadsAuthorInOneStatement() {
        User author = newUser();
        Planner planner = TestDataFactory.createTestPlanner(plannerRepository, author, true);

        Counted<Optional<PublishedPlannerRow>> read =
                counted(() -> plannerRepository.findPublishedAggregate(planner.getId()));

        assertThat(read.statements()).isEqualTo(1);
        PublishedPlannerRow row = read.value().orElseThrow();
        assertThat(row.planner().getId()).isEqualTo(planner.getId());
        assertThat(row.author().getUsernameEpithet()).isEqualTo(author.getUsernameEpithet());
        assertThat(row.author().getUsernameSuffix()).isEqualTo(author.getUsernameSuffix());
    }

    @Test
    @DisplayName("a deactivated author's planner is not read as published")
    void findPublishedAggregate_WhenAuthorDeactivated_ReturnsEmpty() {
        User author = newUser();
        Planner planner = TestDataFactory.createTestPlanner(plannerRepository, author, true);
        deactivate(author);

        assertThat(counted(() -> plannerRepository.findPublishedAggregate(planner.getId())).value()).isEmpty();
    }

    @Test
    @DisplayName("the published check joins the author in one statement and excludes a deactivated author")
    void existsPublishedById_WhenAuthorDeactivated_TurnsFalseInOneStatement() {
        User author = newUser();
        Planner planner = TestDataFactory.createTestPlanner(plannerRepository, author, true);

        Counted<Boolean> active = counted(() -> plannerRepository.existsPublishedById(planner.getId()));
        deactivate(author);
        Counted<Boolean> deactivated = counted(() -> plannerRepository.existsPublishedById(planner.getId()));

        assertThat(active.value()).isTrue();
        assertThat(active.statements()).isEqualTo(1);
        assertThat(deactivated.value()).isFalse();
        assertThat(deactivated.statements()).isEqualTo(1);
    }

    @Test
    @DisplayName("the thread read reports owner, publication and owner deactivation in one statement")
    void findThread_WhenOwnerDeactivated_ReportsItInOneStatement() {
        User owner = newUser();
        Planner planner = TestDataFactory.createTestPlanner(plannerRepository, owner, true);

        Counted<Optional<PlannerThread>> active = counted(() -> plannerRepository.findThread(planner.getId()));
        deactivate(owner);
        Counted<Optional<PlannerThread>> deactivated = counted(() -> plannerRepository.findThread(planner.getId()));

        assertThat(active.statements()).isEqualTo(1);
        assertThat(active.value()).contains(new PlannerThread(planner.getId(), owner.getId(), true, false));
        assertThat(deactivated.statements()).isEqualTo(1);
        assertThat(deactivated.value()).contains(new PlannerThread(planner.getId(), owner.getId(), true, true));
    }

    @Test
    @DisplayName("a planner saved with an owner id stores it in user_id and the owner queries find it")
    void insert_WhenSavedWithUserId_StoresItAndOwnerQueriesFindIt() {
        User owner = newUser();
        Planner planner = TestDataFactory.createTestPlanner(plannerRepository, owner, true);

        assertThat(jdbcTemplate.queryForObject(
                "SELECT user_id FROM planner WHERE id = UUID_TO_BIN(?)", Long.class, planner.getId().toString()))
                .isEqualTo(owner.getId());
        assertThat(plannerRepository.findIdsByUserId(owner.getId())).containsExactly(planner.getId());
        assertThat(plannerRepository.countActiveByUserId(owner.getId())).isEqualTo(1);
        assertThat(plannerRepository.findOwnerSummaries(owner.getId(), PageRequest.of(0, 20))
                .map(PlannerSummaryRow::getId).getContent())
                .containsExactly(planner.getId());
        assertThat(plannerRepository.findOwnerSummariesIncludingDeleted(owner.getId(), PageRequest.of(0, 20))
                .map(PlannerSummaryRow::getId).getContent())
                .containsExactly(planner.getId());
        assertThat(plannerRepository.findOwnershipById(planner.getId()).orElseThrow().getUserId())
                .isEqualTo(owner.getId());
        assertThat(plannerRepository.findAggregateForOwner(planner.getId(), owner.getId()).map(Planner::getId))
                .contains(planner.getId());
        assertThat(plannerRepository.findAggregatesForOwner(List.of(planner.getId()), owner.getId()))
                .extracting(Planner::getId)
                .containsExactly(planner.getId());
        assertThat(plannerRepository.findPublishedIdsOwnedBy(owner.getId())).containsExactly(planner.getId());
    }

    private User newUser() {
        return TestDataFactory.createTestUser(userRepository, "author-join-" + UUID.randomUUID() + "@example.com");
    }

    private void deactivate(User user) {
        jdbcTemplate.update("UPDATE users SET deleted_at = NOW() WHERE id = ?", user.getId());
    }

    private <T> Counted<T> counted(Supplier<T> read) {
        return transactionTemplate.execute(status -> {
            entityManager.clear();
            ThreadStatementCounter.start();
            T value;
            long statements;
            try {
                value = read.get();
            } finally {
                statements = ThreadStatementCounter.stop();
            }
            return new Counted<>(value, statements);
        });
    }
}
