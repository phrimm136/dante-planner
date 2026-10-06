package org.danteplanner.backend.repository;
import org.danteplanner.backend.planner.repository.PlannerRepository;
import org.danteplanner.backend.integration.SharedMySqlContainerSupport;
import org.junit.jupiter.api.Tag;
import org.danteplanner.backend.planner.repository.PlannerStatsRepository;
import org.danteplanner.backend.user.repository.UserRepository;


import org.danteplanner.backend.config.TestConfig;
import org.danteplanner.backend.planner.entity.Planner;
import org.danteplanner.backend.planner.entity.PlannerStats;
import org.danteplanner.backend.planner.entity.PlannerStatus;
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

import static org.junit.jupiter.api.Assertions.*;

/**
 * Repository tests for PlannerStatsRepository atomic counter operations.
 *
 * <p>Tests atomic increment queries for upvotes
 * using H2 in-memory database in test profile.</p>
 */
@SpringBootTest
@ActiveProfiles("it")
@Tag("containerized")
@Import(TestConfig.class)
class PlannerRepositoryIT extends SharedMySqlContainerSupport {

    @Autowired
    private PlannerRepository plannerRepository;

    @Autowired
    private PlannerStatsRepository statsRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private TransactionTemplate transactionTemplate;

    private User testUser;
    private Planner testPlanner;

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
        statsRepository.save(PlannerStats.builder().plannerId(testPlanner.getId()).build());
    }

    private void seedUpvotes(int upvotes) {
        // Replace the setUp row: PlannerStats persists as-new, so a second save
        // of the same id inserts rather than updates
        statsRepository.deleteById(testPlanner.getId());
        statsRepository.save(PlannerStats.builder()
                .plannerId(testPlanner.getId())
                .upvotes(upvotes)
                .build());
    }

    // ==================== Upvote Tests ====================

    @Test
    void incrementUpvotes_WhenExistingPlanner_IncrementsCount() {
        // Act
        transactionTemplate.executeWithoutResult(status -> statsRepository.incrementUpvotes(testPlanner.getId()));

        // Assert
        PlannerStats updated = statsRepository.findById(testPlanner.getId()).orElseThrow();
        assertEquals(1, updated.getUpvotes());
    }

    // ==================== Atomic Notification Flag Tests ====================

    @Test
    void trySetRecommendedNotified_WhenFirstCall_ReturnsOne() {
        // Arrange - Set planner to exactly meet threshold (upvotes=10, threshold=10)
        seedUpvotes(10);

        // Act
        int rowsUpdated = trySetRecommendedNotified(testPlanner.getId());

        // Assert
        assertEquals(1, rowsUpdated);
        PlannerStats updated = statsRepository.findById(testPlanner.getId()).orElseThrow();
        assertNotNull(updated.getRecommendedNotifiedAt());
    }

    @Test
    void trySetRecommendedNotified_WhenSecondCall_ReturnsZero() {
        // Arrange - Set planner to meet threshold and set flag
        seedUpvotes(15);

        // First call sets the flag
        trySetRecommendedNotified(testPlanner.getId());

        // Act - Second call should return 0
        int rowsUpdated = trySetRecommendedNotified(testPlanner.getId());

        // Assert
        assertEquals(0, rowsUpdated);
    }

    @Test
    void trySetRecommendedNotified_WhenBelowThreshold_ReturnsZero() {
        // Arrange - Planner below threshold (upvotes=5, threshold=10)
        seedUpvotes(5);

        // Act
        int rowsUpdated = trySetRecommendedNotified(testPlanner.getId());

        // Assert
        assertEquals(0, rowsUpdated);
        PlannerStats updated = statsRepository.findById(testPlanner.getId()).orElseThrow();
        assertNull(updated.getRecommendedNotifiedAt());
    }

    @Test
    void trySetRecommendedNotified_WhenNonExistentPlanner_ReturnsZero() {
        // Arrange
        UUID nonExistentId = UUID.randomUUID();

        // Act
        int rowsUpdated = trySetRecommendedNotified(nonExistentId);

        // Assert
        assertEquals(0, rowsUpdated);
    }

    private int trySetRecommendedNotified(UUID plannerId) {
        return transactionTemplate.execute(status -> statsRepository.trySetRecommendedNotified(plannerId, 10));
    }
}
