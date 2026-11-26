package org.danteplanner.backend.repository;
import org.danteplanner.backend.planner.repository.PlannerRepository;
import org.danteplanner.backend.integration.SharedMySqlContainerSupport;
import org.junit.jupiter.api.Tag;
import org.danteplanner.backend.planner.repository.PlannerStatsRepository;
import org.danteplanner.backend.planner.repository.PlannerVoteRepository;
import org.danteplanner.backend.user.repository.UserRepository;


import org.danteplanner.backend.config.TestConfig;
import org.danteplanner.backend.planner.repository.StoredPlanner;
import org.danteplanner.backend.planner.entity.PlannerStats;
import org.danteplanner.backend.planner.entity.PlannerStatus;
import org.danteplanner.backend.planner.entity.PlannerVote;
import org.danteplanner.backend.planner.entity.VoteType;
import org.danteplanner.backend.user.entity.User;
import org.danteplanner.backend.support.TestDataFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("it")
@Tag("containerized")
@Import(TestConfig.class)
class PlannerStatsRepositoryIT extends SharedMySqlContainerSupport {

    @Autowired
    private PlannerRepository plannerRepository;

    @Autowired
    private PlannerStatsRepository statsRepository;

    @Autowired
    private PlannerVoteRepository voteRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private TransactionTemplate transactionTemplate;

    private User testUser;
    private StoredPlanner testPlanner;

    @BeforeEach
    void setUp() {
        // Clean up

        // Create test user
        testUser = TestDataFactory.createTestUser(userRepository, "test@example.com");

        // Create test planner with initial vote counts at 0
        testPlanner = TestDataFactory.planner(testUser)
                .status(PlannerStatus.DRAFT)
                .published(true)
                .save(plannerRepository);
        statsRepository.save(PlannerStats.builder().plannerId(testPlanner.id()).build());
    }

    private void seedUpvotes(int upvotes) {
        // Replace the setUp row: PlannerStats persists as-new, so a second save
        // of the same id inserts rather than updates
        statsRepository.deleteById(testPlanner.id());
        statsRepository.save(PlannerStats.builder()
                .plannerId(testPlanner.id())
                .upvotes(upvotes)
                .build());
    }

    // ==================== Upvote Tests ====================

    @Test
    void incrementUpvotes_WhenExistingPlanner_IncrementsCount() {
        // Act
        transactionTemplate.executeWithoutResult(status -> statsRepository.incrementUpvotes(testPlanner.id()));

        // Assert
        PlannerStats updated = statsRepository.findById(testPlanner.id()).orElseThrow();
        assertThat(updated.getUpvotes()).isEqualTo(1);
    }

    @Test
    void upvotesOf_WhenTheRowWasLoadedBeforeTheCounterMoved_ReadsTheMovedCounter() {
        transactionTemplate.executeWithoutResult(status -> {
            assertThat(statsRepository.findById(testPlanner.id()).orElseThrow().getUpvotes()).isZero();

            statsRepository.incrementUpvotes(testPlanner.id());

            assertThat(statsRepository.upvotesOf(testPlanner.id())).isEqualTo(1);
        });
    }

    @Test
    void upvotesOf_WhenNoRowExists_ReadsZeroAndTheFirstIncrementInsertsIt() {
        UUID plannerId = testPlanner.id();
        statsRepository.deleteById(plannerId);

        transactionTemplate.executeWithoutResult(status -> {
            assertThat(statsRepository.upvotesOf(plannerId)).isZero();

            statsRepository.incrementUpvotes(plannerId);

            assertThat(statsRepository.upvotesOf(plannerId)).isEqualTo(1);
        });
    }

    @Test
    void incrementUpvotes_WhenAVoteInsertIsPending_KeepsTheVoteAndCountsIt() {
        transactionTemplate.executeWithoutResult(status -> {
            voteRepository.insert(new PlannerVote(testUser.getId(), testPlanner.id(), VoteType.UP));
            statsRepository.incrementUpvotes(testPlanner.id());
        });

        assertThat(voteRepository.findByUserIdAndPlannerId(testUser.getId(), testPlanner.id())).isPresent();
        assertThat(statsRepository.upvotesOf(testPlanner.id())).isEqualTo(1);
    }

    @Test
    void decrementCommentCount_WhenAtZero_StaysAtZero() {
        transactionTemplate.executeWithoutResult(status ->
                statsRepository.decrementCommentCount(testPlanner.id()));

        assertThat(statsRepository.findById(testPlanner.id()).orElseThrow().getCommentCount()).isZero();
    }

    // ==================== Atomic Notification Flag Tests ====================

    @Test
    void trySetRecommendedNotified_WhenFirstCall_ReturnsOne() {
        // Arrange - Set planner to exactly meet threshold (upvotes=10, threshold=10)
        seedUpvotes(10);

        // Act
        int rowsUpdated = trySetRecommendedNotified(testPlanner.id());

        // Assert
        assertThat(rowsUpdated).isEqualTo(1);
        PlannerStats updated = statsRepository.findById(testPlanner.id()).orElseThrow();
        assertThat(updated.getRecommendedNotifiedAt()).isNotNull();
    }

    @Test
    void trySetRecommendedNotified_WhenSecondCall_ReturnsZero() {
        // Arrange - Set planner to meet threshold and set flag
        seedUpvotes(15);

        // First call sets the flag
        trySetRecommendedNotified(testPlanner.id());

        // Act - Second call should return 0
        int rowsUpdated = trySetRecommendedNotified(testPlanner.id());

        // Assert
        assertThat(rowsUpdated).isEqualTo(0);
    }

    @Test
    void trySetRecommendedNotified_WhenBelowThreshold_ReturnsZero() {
        // Arrange - Planner below threshold (upvotes=5, threshold=10)
        seedUpvotes(5);

        // Act
        int rowsUpdated = trySetRecommendedNotified(testPlanner.id());

        // Assert
        assertThat(rowsUpdated).isEqualTo(0);
        PlannerStats updated = statsRepository.findById(testPlanner.id()).orElseThrow();
        assertThat(updated.getRecommendedNotifiedAt()).isNull();
    }

    @Test
    void trySetRecommendedNotified_WhenNonExistentPlanner_ReturnsZero() {
        // Arrange
        UUID nonExistentId = UUID.randomUUID();

        // Act
        int rowsUpdated = trySetRecommendedNotified(nonExistentId);

        // Assert
        assertThat(rowsUpdated).isEqualTo(0);
    }

    private int trySetRecommendedNotified(UUID plannerId) {
        return transactionTemplate.execute(status -> statsRepository.trySetRecommendedNotified(plannerId, 10));
    }
}
