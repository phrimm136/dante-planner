package org.danteplanner.backend.repository;

import org.danteplanner.backend.planner.api.Visibility;
import jakarta.persistence.EntityManager;
import tools.jackson.databind.ObjectMapper;
import org.danteplanner.backend.config.TestConfig;
import org.danteplanner.backend.integration.SharedMySqlContainerSupport;
import org.danteplanner.backend.planner.api.PlannerThread;
import org.danteplanner.backend.planner.repository.StoredPlanner;
import org.danteplanner.backend.planner.repository.PlannerRepository;
import org.danteplanner.backend.planner.repository.PlannerSummaryRow;
import org.danteplanner.backend.planner.repository.PublishedPlannerRow;
import org.danteplanner.backend.support.TestDataFactory;
import org.danteplanner.backend.support.ThreadStatementCounter;
import org.danteplanner.backend.user.entity.User;
import org.danteplanner.backend.user.repository.UserRepository;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
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
    void findPublishedAggregate_WhenPublished_ReadsAuthorInOneStatement() {
        User author = newUser();
        StoredPlanner planner = TestDataFactory.createTestPlanner(plannerRepository, author, true);

        Counted<Optional<PublishedPlannerRow>> read =
                counted(() -> plannerRepository.findPublishedAggregate(planner.id()));

        assertThat(read.statements()).isEqualTo(1);
        PublishedPlannerRow row = read.value().orElseThrow();
        assertThat(row.planner().id()).isEqualTo(planner.id());
        assertThat(row.author().getUsernameEpithet()).isEqualTo(author.getUsernameEpithet());
        assertThat(row.author().getUsernameSuffix()).isEqualTo(author.getUsernameSuffix());
    }

    @Test
    void findPublishedAggregate_WhenAuthorDeactivated_ReturnsEmpty() {
        User author = newUser();
        StoredPlanner planner = TestDataFactory.createTestPlanner(plannerRepository, author, true);
        deactivate(author);

        assertThat(counted(() -> plannerRepository.findPublishedAggregate(planner.id())).value()).isEmpty();
    }

    @Test
    void existsPublishedById_WhenAuthorDeactivated_TurnsFalseInOneStatement() {
        User author = newUser();
        StoredPlanner planner = TestDataFactory.createTestPlanner(plannerRepository, author, true);

        Counted<Boolean> active = counted(() -> plannerRepository.existsPublishedById(planner.id()));
        deactivate(author);
        Counted<Boolean> deactivated = counted(() -> plannerRepository.existsPublishedById(planner.id()));

        assertThat(active.value()).isTrue();
        assertThat(active.statements()).isEqualTo(1);
        assertThat(deactivated.value()).isFalse();
        assertThat(deactivated.statements()).isEqualTo(1);
    }

    @Test
    void findThread_WhenOwnerDeactivated_ReportsItInOneStatement() {
        User owner = newUser();
        StoredPlanner planner = TestDataFactory.createTestPlanner(plannerRepository, owner, true);

        Counted<Optional<PlannerThread>> active = counted(() -> plannerRepository.findThread(planner.id()));
        deactivate(owner);
        Counted<Optional<PlannerThread>> deactivated = counted(() -> plannerRepository.findThread(planner.id()));

        assertThat(active.statements()).isEqualTo(1);
        assertThat(active.value()).contains(new PlannerThread(planner.id(), owner.getId(), Visibility.SHOWN));
        assertThat(deactivated.statements()).isEqualTo(1);
        assertThat(deactivated.value()).contains(new PlannerThread(planner.id(), owner.getId(), Visibility.HIDDEN));
    }

    @ParameterizedTest
    @CsvSource({"true, false, SHOWN", "true, true, HIDDEN", "false, false, HIDDEN", "false, true, HIDDEN"})
    void visibility_WhenReadForEachPublicationAndAuthorState_IsShownOnlyForAPublishedLiveAuthor(
            boolean published, boolean authorDeactivated, Visibility expected) {
        User owner = newUser();
        StoredPlanner planner = TestDataFactory.createTestPlanner(plannerRepository, owner, published);
        if (authorDeactivated) {
            deactivate(owner);
        }

        assertThat(plannerRepository.findThread(planner.id()).orElseThrow().visibility()).isEqualTo(expected);
        assertThat(plannerRepository.findAggregateWithAuthor(planner.id()).orElseThrow().visibility())
                .isEqualTo(expected);
    }

    @Test
    void findAggregate_WhenRead_CarriesEveryStoredColumnAsATypedValue() {
        User owner = newUser();
        StoredPlanner planner = TestDataFactory.planner(owner).published(true)
                .selectedKeywords(java.util.Set.of("Burst")).deviceId(UUID.randomUUID())
                .save(plannerRepository);

        StoredPlanner read = plannerRepository.findAggregate(planner.id()).orElseThrow();

        assertThat(read).usingRecursiveComparison().ignoringCollectionOrder().ignoringFields("content.json").isEqualTo(planner);
        assertThat(new ObjectMapper().readTree(read.content().json()))
                .as("MySQL re-serializes a JSON column, so the document is compared, not its text")
                .isEqualTo(new ObjectMapper().readTree(planner.content().json()));
    }

    @Test
    void insert_WhenSavedWithUserId_StoresItAndOwnerQueriesFindIt() {
        User owner = newUser();
        StoredPlanner planner = TestDataFactory.createTestPlanner(plannerRepository, owner, true);

        assertThat(jdbcTemplate.queryForObject(
                "SELECT user_id FROM planner WHERE id = UUID_TO_BIN(?)", Long.class, planner.id().toString()))
                .isEqualTo(owner.getId());
        assertThat(plannerRepository.findIdsByUserId(owner.getId())).containsExactly(planner.id());
        assertThat(plannerRepository.countActiveByUserId(owner.getId())).isEqualTo(1);
        assertThat(plannerRepository.findOwnerSummaries(owner.getId(), PageRequest.of(0, 20))
                .map(PlannerSummaryRow::getId).getContent())
                .containsExactly(planner.id());
        assertThat(plannerRepository.findOwnerSummariesIncludingDeleted(owner.getId(), PageRequest.of(0, 20))
                .map(PlannerSummaryRow::getId).getContent())
                .containsExactly(planner.id());
        assertThat(plannerRepository.findOwnershipById(planner.id()).orElseThrow().getUserId())
                .isEqualTo(owner.getId());
        assertThat(plannerRepository.findAggregateForOwner(planner.id(), owner.getId()).map(StoredPlanner::id))
                .contains(planner.id());
        assertThat(plannerRepository.findAggregatesForOwner(List.of(planner.id()), owner.getId()))
                .extracting(StoredPlanner::id)
                .containsExactly(planner.id());
        assertThat(plannerRepository.findPublishedIdsOwnedBy(owner.getId())).containsExactly(planner.id());
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
