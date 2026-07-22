package org.danteplanner.backend.repository;
import org.danteplanner.backend.planner.repository.PlannerRepository;
import org.danteplanner.backend.planner.repository.PlannerStatsRepository;
import org.danteplanner.backend.user.repository.UserRepository;


import jakarta.persistence.EntityManager;

import org.danteplanner.backend.config.TestConfig;
import org.danteplanner.backend.planner.entity.Planner;
import org.danteplanner.backend.planner.entity.PlannerStats;
import org.danteplanner.backend.planner.entity.PlannerStatus;
import org.danteplanner.backend.user.entity.User;
import org.danteplanner.backend.support.TestDataFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Repository tests for PlannerStatsRepository atomic counter operations.
 *
 * <p>Tests atomic increment queries for upvotes
 * using H2 in-memory database in test profile.</p>
 */
@SpringBootTest
@ActiveProfiles("test")
@Import(TestConfig.class)
@Transactional
class PlannerRepositoryTest {

    @Autowired
    private PlannerRepository plannerRepository;

    @Autowired
    private PlannerStatsRepository statsRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private EntityManager entityManager;

    private User testUser;
    private Planner testPlanner;

    @BeforeEach
    void setUp() {
        // Clean up
        statsRepository.deleteAll();
        plannerRepository.deleteAll();
        userRepository.deleteAll();

        // Create test user
        testUser = TestDataFactory.createTestUser(userRepository, "test@example.com");

        // Create test planner with initial vote counts at 0
        testPlanner = TestDataFactory.planner(testUser)
                .status(PlannerStatus.DRAFT)
                .published(true)
                .save(plannerRepository);
        statsRepository.save(PlannerStats.builder().plannerId(testPlanner.getId()).build());
        statsRepository.flush();
    }

    private void seedUpvotes(int upvotes) {
        statsRepository.save(PlannerStats.builder()
                .plannerId(testPlanner.getId())
                .upvotes(upvotes)
                .build());
        statsRepository.flush();
        entityManager.clear();
    }

    // ==================== Upvote Tests ====================

    @Test
    @DisplayName("incrementUpvotes - existing planner increments count")
    void incrementUpvotes_ExistingPlanner_IncrementsCount() {
        // Act
        statsRepository.incrementUpvotes(testPlanner.getId());
        entityManager.clear(); // Clear persistence context to force re-read

        // Assert
        PlannerStats updated = statsRepository.findById(testPlanner.getId()).orElseThrow();
        assertEquals(1, updated.getUpvotes());
    }

    // ==================== Atomic Notification Flag Tests ====================

    @Test
    @DisplayName("trySetRecommendedNotified - first call on threshold planner returns 1 and sets flag")
    void trySetRecommendedNotified_FirstCall_ReturnsOne() {
        // Arrange - Set planner to exactly meet threshold (upvotes=10, threshold=10)
        seedUpvotes(10);

        // Act
        int rowsUpdated = statsRepository.trySetRecommendedNotified(testPlanner.getId(), 10);
        entityManager.clear();

        // Assert
        assertEquals(1, rowsUpdated);
        PlannerStats updated = statsRepository.findById(testPlanner.getId()).orElseThrow();
        assertNotNull(updated.getRecommendedNotifiedAt());
    }

    @Test
    @DisplayName("trySetRecommendedNotified - second call returns 0 (atomic flag already set)")
    void trySetRecommendedNotified_SecondCall_ReturnsZero() {
        // Arrange - Set planner to meet threshold and set flag
        seedUpvotes(15);

        // First call sets the flag
        statsRepository.trySetRecommendedNotified(testPlanner.getId(), 10);
        entityManager.clear();

        // Act - Second call should return 0
        int rowsUpdated = statsRepository.trySetRecommendedNotified(testPlanner.getId(), 10);

        // Assert
        assertEquals(0, rowsUpdated);
    }

    @Test
    @DisplayName("trySetRecommendedNotified - returns 0 when threshold not met")
    void trySetRecommendedNotified_BelowThreshold_ReturnsZero() {
        // Arrange - Planner below threshold (upvotes=5, threshold=10)
        seedUpvotes(5);

        // Act
        int rowsUpdated = statsRepository.trySetRecommendedNotified(testPlanner.getId(), 10);

        // Assert
        assertEquals(0, rowsUpdated);
        PlannerStats updated = statsRepository.findById(testPlanner.getId()).orElseThrow();
        assertNull(updated.getRecommendedNotifiedAt());
    }

    @Test
    @DisplayName("trySetRecommendedNotified - returns 0 for non-existent planner")
    void trySetRecommendedNotified_NonExistentPlanner_ReturnsZero() {
        // Arrange
        UUID nonExistentId = UUID.randomUUID();

        // Act
        int rowsUpdated = statsRepository.trySetRecommendedNotified(nonExistentId, 10);

        // Assert
        assertEquals(0, rowsUpdated);
    }

}
