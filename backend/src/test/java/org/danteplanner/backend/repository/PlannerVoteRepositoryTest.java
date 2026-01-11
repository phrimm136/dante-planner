package org.danteplanner.backend.repository;

import org.danteplanner.backend.config.TestConfig;
import org.danteplanner.backend.entity.Planner;
import org.danteplanner.backend.entity.PlannerVote;
import org.danteplanner.backend.entity.User;
import org.danteplanner.backend.entity.VoteType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Repository tests for PlannerVoteRepository.
 *
 * <p>Tests data access layer operations for planner votes
 * using H2 in-memory database in test profile.</p>
 *
 * <p>NOTE: Soft-delete tests removed in Phase 2 - votes are now immutable.
 * Old tests for toggle behavior, soft-delete, reactivation, and vote type changes
 * are obsolete and have been deleted.</p>
 */
@SpringBootTest
@ActiveProfiles("test")
@Import(TestConfig.class)
@Transactional
class PlannerVoteRepositoryTest {

    @Autowired
    private PlannerVoteRepository plannerVoteRepository;

    @Autowired
    private PlannerRepository plannerRepository;

    @Autowired
    private UserRepository userRepository;

    private User testUser;
    private Planner testPlanner;

    @BeforeEach
    void setUp() {
        // Clean up
        plannerVoteRepository.deleteAll();
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

        // Create test planner
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
    }

    @Test
    @DisplayName("Should save vote and retrieve by user ID and planner ID")
    void testSaveAndFindVote() {
        // Arrange
        PlannerVote vote = new PlannerVote(testUser.getId(), testPlanner.getId(), VoteType.UP);

        // Act
        PlannerVote saved = plannerVoteRepository.save(vote);
        Optional<PlannerVote> found = plannerVoteRepository.findByUserIdAndPlannerId(
                testUser.getId(), testPlanner.getId());

        // Assert
        assertNotNull(saved);
        assertNotNull(saved.getCreatedAt());
        assertTrue(found.isPresent());
        assertEquals(VoteType.UP, found.get().getVoteType());
        assertEquals(testUser.getId(), found.get().getUserId());
        assertEquals(testPlanner.getId(), found.get().getPlannerId());
    }

    @Test
    @DisplayName("Should return empty optional when vote not found")
    void testFindVote_NotFound_ReturnsEmpty() {
        // Act
        Optional<PlannerVote> result = plannerVoteRepository.findByUserIdAndPlannerId(
                999L, UUID.randomUUID());

        // Assert
        assertFalse(result.isPresent());
    }

    @Test
    @DisplayName("Should allow multiple users to vote on same planner")
    void testMultipleUsersVoteOnSamePlanner() {
        // Arrange - Create another user
        User secondUser = User.builder()
                .email("second@example.com")
                .provider("google")
                .providerId("google-456")
                .usernameKeyword("W_CORP")
                .usernameSuffix("test2")
                .build();
        secondUser = userRepository.save(secondUser);

        // Act - Both users upvote
        PlannerVote vote1 = new PlannerVote(testUser.getId(), testPlanner.getId(), VoteType.UP);
        PlannerVote vote2 = new PlannerVote(secondUser.getId(), testPlanner.getId(), VoteType.UP);
        plannerVoteRepository.save(vote1);
        plannerVoteRepository.save(vote2);

        // Assert
        Optional<PlannerVote> user1Vote = plannerVoteRepository.findByUserIdAndPlannerId(
                testUser.getId(), testPlanner.getId());
        Optional<PlannerVote> user2Vote = plannerVoteRepository.findByUserIdAndPlannerId(
                secondUser.getId(), testPlanner.getId());

        assertTrue(user1Vote.isPresent());
        assertTrue(user2Vote.isPresent());
        assertEquals(VoteType.UP, user1Vote.get().getVoteType());
        assertEquals(VoteType.UP, user2Vote.get().getVoteType());
    }

    @Test
    @DisplayName("Should allow same user to vote on multiple planners")
    void testSameUserVotesOnMultiplePlanners() {
        // Arrange - Create another planner
        Planner secondPlanner = Planner.builder()
                .id(UUID.randomUUID())
                .user(testUser)
                .title("Second Planner")
                .category("10F")
                .status("draft")
                .content("{\"data\":\"test2\"}")
                .published(true)
                .upvotes(0)
                .schemaVersion(1)
                .contentVersion(6)
                .plannerType(org.danteplanner.backend.entity.PlannerType.MIRROR_DUNGEON)
                .savedAt(Instant.now())
                .build();
        secondPlanner = plannerRepository.save(secondPlanner);

        // Act - User upvotes both planners
        PlannerVote vote1 = new PlannerVote(testUser.getId(), testPlanner.getId(), VoteType.UP);
        PlannerVote vote2 = new PlannerVote(testUser.getId(), secondPlanner.getId(), VoteType.UP);
        plannerVoteRepository.save(vote1);
        plannerVoteRepository.save(vote2);

        // Assert
        Optional<PlannerVote> planner1Vote = plannerVoteRepository.findByUserIdAndPlannerId(
                testUser.getId(), testPlanner.getId());
        Optional<PlannerVote> planner2Vote = plannerVoteRepository.findByUserIdAndPlannerId(
                testUser.getId(), secondPlanner.getId());

        assertTrue(planner1Vote.isPresent());
        assertTrue(planner2Vote.isPresent());
        assertEquals(VoteType.UP, planner1Vote.get().getVoteType());
        assertEquals(VoteType.UP, planner2Vote.get().getVoteType());
    }

    @Test
    @DisplayName("Vote type is immutable - cannot be changed after creation")
    void testVoteType_IsImmutable() {
        // Arrange
        PlannerVote vote = new PlannerVote(testUser.getId(), testPlanner.getId(), VoteType.UP);
        vote = plannerVoteRepository.save(vote);

        // Assert - voteType field is final, no setter exists
        assertEquals(VoteType.UP, vote.getVoteType());
        // This test documents that setVoteType() no longer exists
        // Attempting to change vote type would require creating a new vote (which would violate composite PK)
    }
}
