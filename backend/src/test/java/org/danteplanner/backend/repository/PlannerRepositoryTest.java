package org.danteplanner.backend.repository;

import jakarta.persistence.EntityManager;

import org.danteplanner.backend.config.TestConfig;
import org.danteplanner.backend.entity.Planner;
import org.danteplanner.backend.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Repository tests for PlannerRepository atomic vote operations.
 *
 * <p>Tests atomic increment/decrement queries for upvotes
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
    private UserRepository userRepository;

    @Autowired
    private EntityManager entityManager;

    private User testUser;
    private Planner testPlanner;

    @BeforeEach
    void setUp() {
        // Clean up
        plannerRepository.deleteAll();
        userRepository.deleteAll();

        // Create test user
        testUser = User.builder()
                .email("test@example.com")
                .provider("google")
                .providerId("google-123")
                .usernameKeyword("W_CORP")
                .usernameSuffix("test1")
                .build();
        testUser = userRepository.save(testUser);

        // Create test planner with initial vote counts at 0
        testPlanner = Planner.builder()
                .id(UUID.randomUUID())
                .user(testUser)
                .title("Test Planner")
                .category("5F")
                .status("draft")
                .content("{\"data\":\"test\"}")
                .published(true)
                .upvotes(0)
                .schemaVersion(1)
                .contentVersion(6)
                .plannerType(org.danteplanner.backend.entity.PlannerType.MIRROR_DUNGEON)
                .savedAt(Instant.now())
                .build();
        testPlanner = plannerRepository.save(testPlanner);
        plannerRepository.flush();
    }

    // ==================== Upvote Tests ====================

    @Test
    @DisplayName("incrementUpvotes - existing planner returns 1 and increments count")
    void incrementUpvotes_ExistingPlanner_ReturnsOne() {
        // Act
        int rowsUpdated = plannerRepository.incrementUpvotes(testPlanner.getId());
        entityManager.clear(); // Clear persistence context to force re-read

        // Assert
        assertEquals(1, rowsUpdated);
        Planner updated = plannerRepository.findById(testPlanner.getId()).orElseThrow();
        assertEquals(1, updated.getUpvotes());
    }

    @Test
    @DisplayName("incrementUpvotes - non-existent planner returns 0")
    void incrementUpvotes_NonExistentPlanner_ReturnsZero() {
        // Arrange
        UUID nonExistentId = UUID.randomUUID();

        // Act
        int rowsUpdated = plannerRepository.incrementUpvotes(nonExistentId);

        // Assert
        assertEquals(0, rowsUpdated);
    }

    @Test
    @DisplayName("decrementUpvotes - positive count decrements and returns 1")
    void decrementUpvotes_PositiveCount_Decrements() {
        // Arrange - Set upvotes to 5
        testPlanner.setUpvotes(5);
        plannerRepository.save(testPlanner);
        plannerRepository.flush();
        entityManager.clear();

        // Act
        int rowsUpdated = plannerRepository.decrementUpvotes(testPlanner.getId());
        entityManager.clear();

        // Assert
        assertEquals(1, rowsUpdated);
        Planner updated = plannerRepository.findById(testPlanner.getId()).orElseThrow();
        assertEquals(4, updated.getUpvotes());
    }

    @Test
    @DisplayName("decrementUpvotes - zero count stays at zero and returns 0")
    void decrementUpvotes_ZeroCount_StaysAtZero() {
        // Arrange - upvotes is already 0 from setUp
        plannerRepository.flush();
        entityManager.clear();

        // Act
        int rowsUpdated = plannerRepository.decrementUpvotes(testPlanner.getId());
        entityManager.clear();

        // Assert
        assertEquals(0, rowsUpdated);
        Planner updated = plannerRepository.findById(testPlanner.getId()).orElseThrow();
        assertEquals(0, updated.getUpvotes());
    }

    @Test
    @DisplayName("decrementUpvotes - non-existent planner returns 0")
    void decrementUpvotes_NonExistentPlanner_ReturnsZero() {
        // Arrange
        UUID nonExistentId = UUID.randomUUID();

        // Act
        int rowsUpdated = plannerRepository.decrementUpvotes(nonExistentId);

        // Assert
        assertEquals(0, rowsUpdated);
    }

    // ==================== Atomic Notification Flag Tests ====================

    @Test
    @DisplayName("trySetRecommendedNotified - first call on threshold planner returns 1 and sets flag")
    void trySetRecommendedNotified_FirstCall_ReturnsOne() {
        // Arrange - Set planner to exactly meet threshold (upvotes=10, threshold=10)
        testPlanner.setUpvotes(10);
        plannerRepository.save(testPlanner);
        plannerRepository.flush();
        entityManager.clear();

        // Act
        int rowsUpdated = plannerRepository.trySetRecommendedNotified(testPlanner.getId(), 10);
        entityManager.clear();

        // Assert
        assertEquals(1, rowsUpdated);
        Planner updated = plannerRepository.findById(testPlanner.getId()).orElseThrow();
        assertNotNull(updated.getRecommendedNotifiedAt());
    }

    @Test
    @DisplayName("trySetRecommendedNotified - second call returns 0 (atomic flag already set)")
    void trySetRecommendedNotified_SecondCall_ReturnsZero() {
        // Arrange - Set planner to meet threshold and set flag
        testPlanner.setUpvotes(15);
        plannerRepository.save(testPlanner);
        plannerRepository.flush();

        // First call sets the flag
        plannerRepository.trySetRecommendedNotified(testPlanner.getId(), 10);
        entityManager.clear();

        // Act - Second call should return 0
        int rowsUpdated = plannerRepository.trySetRecommendedNotified(testPlanner.getId(), 10);

        // Assert
        assertEquals(0, rowsUpdated);
    }

    @Test
    @DisplayName("trySetRecommendedNotified - returns 0 when threshold not met")
    void trySetRecommendedNotified_BelowThreshold_ReturnsZero() {
        // Arrange - Planner below threshold (upvotes=5, threshold=10)
        testPlanner.setUpvotes(5);
        plannerRepository.save(testPlanner);
        plannerRepository.flush();
        entityManager.clear();

        // Act
        int rowsUpdated = plannerRepository.trySetRecommendedNotified(testPlanner.getId(), 10);

        // Assert
        assertEquals(0, rowsUpdated);
        Planner updated = plannerRepository.findById(testPlanner.getId()).orElseThrow();
        assertNull(updated.getRecommendedNotifiedAt());
    }

    @Test
    @DisplayName("trySetRecommendedNotified - returns 0 for non-existent planner")
    void trySetRecommendedNotified_NonExistentPlanner_ReturnsZero() {
        // Arrange
        UUID nonExistentId = UUID.randomUUID();

        // Act
        int rowsUpdated = plannerRepository.trySetRecommendedNotified(nonExistentId, 10);

        // Assert
        assertEquals(0, rowsUpdated);
    }

}
