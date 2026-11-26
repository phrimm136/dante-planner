package org.danteplanner.backend.repository;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;

import jakarta.persistence.EntityManager;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import org.danteplanner.backend.config.TestConfig;
import org.danteplanner.backend.integration.SharedMySqlContainerSupport;
import org.danteplanner.backend.planner.repository.StoredPlanner;
import org.danteplanner.backend.planner.entity.PlannerEntityFilter;
import org.danteplanner.backend.planner.entity.PlannerEntityFilterId;
import org.danteplanner.backend.planner.entity.PlannerKeywordFilter;
import org.danteplanner.backend.planner.entity.PlannerKeywordFilterId;
import org.danteplanner.backend.planner.entity.PlannerSubscriptionId;
import org.danteplanner.backend.planner.entity.PlannerView;
import org.danteplanner.backend.planner.entity.PlannerViewId;
import org.danteplanner.backend.planner.entity.PlannerVote;
import org.danteplanner.backend.planner.entity.PlannerVoteId;
import org.danteplanner.backend.planner.entity.VoteType;
import org.danteplanner.backend.planner.repository.PlannerEntityFilterRepository;
import org.danteplanner.backend.planner.repository.PlannerKeywordFilterRepository;
import org.danteplanner.backend.planner.repository.PlannerSubscriptionRepository;
import org.danteplanner.backend.planner.repository.PlannerViewRepository;
import org.danteplanner.backend.planner.repository.PlannerVoteRepository;
import org.danteplanner.backend.shared.entity.ContentEntityType;
import org.danteplanner.backend.support.PlannerSeeds;
import org.danteplanner.backend.support.TestDataFactory;
import org.danteplanner.backend.user.entity.User;
import org.danteplanner.backend.user.repository.UserRepository;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("it")
@Tag("containerized")
@Import(TestConfig.class)
class CompositeIdIT extends SharedMySqlContainerSupport {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PlannerSeeds plannerSeeds;

    @Autowired
    private PlannerVoteRepository voteRepository;

    @Autowired
    private PlannerSubscriptionRepository subscriptionRepository;

    @Autowired
    private PlannerViewRepository viewRepository;

    @Autowired
    private PlannerEntityFilterRepository entityFilterRepository;

    @Autowired
    private PlannerKeywordFilterRepository keywordFilterRepository;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private TransactionTemplate transactionTemplate;

    private Long userId;
    private UUID plannerId;

    @BeforeEach
    void setUp() {
        User user = TestDataFactory.createTestUser(userRepository, "composite-id@example.com");
        StoredPlanner planner = TestDataFactory.planner(user).published(true).save(plannerSeeds);
        userId = user.getId();
        plannerId = planner.id();
    }

    @Test
    void vote_WhenAddressedByItsCompositeId_LoadsAndDeletes() {
        assertLoadsAndDeletes(voteRepository, new PlannerVote(userId, plannerId, VoteType.UP),
                new PlannerVoteId(userId, plannerId));
    }

    @Test
    void subscription_WhenAddressedByItsCompositeId_LoadsAndDeletes() {
        transactionTemplate.executeWithoutResult(status ->
                subscriptionRepository.insertIfAbsent(userId, plannerId, Instant.now()));

        assertLoadsAndDeletes(subscriptionRepository, new PlannerSubscriptionId(userId, plannerId));
    }

    @Test
    void view_WhenAddressedByItsCompositeId_LoadsAndDeletes() {
        LocalDate day = LocalDate.of(2026, 1, 1);
        assertLoadsAndDeletes(viewRepository, new PlannerView(plannerId, "a".repeat(64), day),
                new PlannerViewId(plannerId, "a".repeat(64), day));
    }

    @Test
    void entityFilter_WhenAddressedByItsCompositeId_LoadsAndDeletes() {
        assertLoadsAndDeletes(entityFilterRepository,
                new PlannerEntityFilter(ContentEntityType.EGO_GIFT, 9001, plannerId),
                new PlannerEntityFilterId(ContentEntityType.EGO_GIFT, 9001, plannerId));
    }

    @Test
    void keywordFilter_WhenAddressedByItsCompositeId_LoadsAndDeletes() {
        assertLoadsAndDeletes(keywordFilterRepository, new PlannerKeywordFilter("Burst", plannerId),
                new PlannerKeywordFilterId("Burst", plannerId));
    }

    private <T, I> void assertLoadsAndDeletes(JpaRepository<T, I> repository, T row, I id) {
        transactionTemplate.executeWithoutResult(status -> entityManager.persist(row));

        assertLoadsAndDeletes(repository, id);
    }

    private <T, I> void assertLoadsAndDeletes(JpaRepository<T, I> repository, I id) {
        assertThat(repository.findById(id)).isPresent();
        transactionTemplate.executeWithoutResult(status ->
                entityManager.remove(repository.findById(id).orElseThrow()));
        assertThat(repository.existsById(id)).isFalse();
    }
}
