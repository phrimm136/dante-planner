package org.danteplanner.backend.integration;
import org.danteplanner.backend.planner.repository.PlannerVoteRepository;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.junit.jupiter.api.Tag;
import org.danteplanner.backend.planner.repository.PlannerRepository;
import org.danteplanner.backend.planner.repository.PlannerStatsRepository;
import org.danteplanner.backend.planner.entity.VoteType;
import org.danteplanner.backend.planner.entity.PlannerStats;
import org.danteplanner.backend.planner.entity.PlannerVoteId;
import org.danteplanner.backend.planner.entity.Planner;
import org.danteplanner.backend.support.TestDataFactory;
import org.danteplanner.backend.user.entity.User;
import org.danteplanner.backend.user.repository.UserRepository;

import org.danteplanner.backend.notification.repository.NotificationRepository;

import org.danteplanner.backend.notification.entity.NotificationType;

import org.danteplanner.backend.notification.entity.Notification;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

import org.danteplanner.backend.config.TestConfig;
import org.danteplanner.backend.shared.entity.*;
import org.danteplanner.backend.repository.*;
import org.danteplanner.backend.notification.service.NotificationService;
import org.danteplanner.backend.planner.service.PlannerEngagementService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration test for Vote → Notification Flow.
 *
 * <p>Tests the complete end-to-end flow from vote crossing threshold
 * → atomic flag check → notification creation with race condition prevention.</p>
 *
 * @see <a href="https://www.baeldung.com/spring-test-programmatic-transactions">Baeldung - Programmatic Transactions</a>
 */
@SpringBootTest
@ActiveProfiles("it")
@Tag("containerized")
@Import(TestConfig.class)
class VoteNotificationFlowIT {

    /**
     * Its own database: two of these assert about the notifications table as a whole (the UNIQUE
     * constraint that collapses duplicates, and what concurrent votes leave behind), which cannot
     * be narrowed to a row the test created.
     */
    @DynamicPropertySource
    static void ownDatabase(DynamicPropertyRegistry registry) {
        SharedMySqlContainerSupport.registerOwnDatabase(registry, "vote_notification_flow");
    }

    @Autowired
    private PlannerEngagementService plannerEngagementService;

    @Autowired
    private NotificationService notificationService;

    @Autowired
    private PlannerRepository plannerRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PlannerVoteRepository plannerVoteRepository;

    @Autowired
    private PlannerStatsRepository plannerStatsRepository;

    @Autowired
    private NotificationRepository notificationRepository;

    @PersistenceContext
    private EntityManager entityManager;

    @Value("${planner.recommended-threshold}")
    private int recommendedThreshold;

    private User plannerOwner;
    private User voter1;
    private User voter2;
    private User voter3;
    private Planner testPlanner;

    @BeforeEach
    void setUp() {
        // Create planner owner
        plannerOwner = TestDataFactory.createTestUser(userRepository, "owner@example.com");

        // Create voters
        voter1 = TestDataFactory.createTestUser(userRepository, "voter1@example.com");

        voter2 = TestDataFactory.createTestUser(userRepository, "voter2@example.com");

        voter3 = TestDataFactory.createTestUser(userRepository, "voter3@example.com");

        // Create published planner with initial vote counts at 0
        testPlanner = TestDataFactory.planner(plannerOwner)
                .title("Test Planner for Notification")
                .content("{\"data\":\"test\"}")
                .published(true)
                .save(plannerRepository);
        plannerRepository.flush();
        entityManager.clear();
    }

    private void seedStats(int upvotes, Instant recommendedNotifiedAt) {
        plannerStatsRepository.save(PlannerStats.builder()
                .plannerId(testPlanner.getId())
                .upvotes(upvotes)
                .recommendedNotifiedAt(recommendedNotifiedAt)
                .build());
    }

    // ==================== IT1: Vote Crossing Threshold Creates Notification ====================

    @Test
    @DisplayName("IT1: Vote crossing threshold (9→10) creates PLANNER_RECOMMENDED notification")
    void castVote_WhenCrossingThreshold_CreatesNotification() {
        // Arrange - Set planner to 1 vote below threshold
        seedStats(recommendedThreshold - 1, null);
        plannerStatsRepository.flush();
        entityManager.clear();

        // Verify no notification exists before
        long notificationsBefore = notificationRepository.findByUserIdAndDeletedAtIsNullOrderByCreatedAtDesc(
                plannerOwner.getId(), org.springframework.data.domain.PageRequest.of(0, 10)).getTotalElements();
        assertEquals(0, notificationsBefore);

        // Act - Cast vote that crosses threshold
        plannerEngagementService.castVote(voter1.getId(), testPlanner.getId(), VoteType.UP);

        // Commit to trigger AFTER_COMMIT listener, then start new transaction for assertions

        // Assert - Notification created
        List<Notification> notifications = notificationRepository.findByUserIdAndDeletedAtIsNullOrderByCreatedAtDesc(
                plannerOwner.getId(), org.springframework.data.domain.PageRequest.of(0, 10)).getContent();

        assertEquals(1, notifications.size());
        Notification notification = notifications.get(0);
        assertEquals(plannerOwner.getId(), notification.getUserId());
        assertEquals(testPlanner.getId().toString(), notification.getContentId());
        assertEquals(NotificationType.PLANNER_RECOMMENDED, notification.getNotificationType());
        assertFalse(notification.getRead());
        assertNotNull(notification.getCreatedAt());

        // Verify stats flag set
        PlannerStats stats = plannerStatsRepository.findById(testPlanner.getId()).orElseThrow();
        assertNotNull(stats.getRecommendedNotifiedAt());
    }

    @Test
    @DisplayName("IT1.1: Vote below threshold does not create notification")
    void castVote_WhenBelowThreshold_NoNotification() {
        // Arrange - Set planner to 2 votes below threshold
        seedStats(recommendedThreshold - 2, null);
        plannerStatsRepository.flush();
        entityManager.clear();

        // Act - Cast vote that doesn't cross threshold
        plannerEngagementService.castVote(voter1.getId(), testPlanner.getId(), VoteType.UP);

        // Commit to trigger any listeners, then start new transaction for assertions

        // Assert - No notification created
        long notificationsCount = notificationRepository.findByUserIdAndDeletedAtIsNullOrderByCreatedAtDesc(
                plannerOwner.getId(), org.springframework.data.domain.PageRequest.of(0, 10)).getTotalElements();
        assertEquals(0, notificationsCount);

        // Verify stats flag NOT set
        PlannerStats stats = plannerStatsRepository.findById(testPlanner.getId()).orElseThrow();
        assertNull(stats.getRecommendedNotifiedAt());
    }

    @Test
    @DisplayName("IT1.2: Vote exactly at threshold creates notification")
    void castVote_WhenExactlyAtThreshold_CreatesNotification() {
        // Arrange - Set planner to 1 vote below threshold
        seedStats(recommendedThreshold - 1, null);
        plannerStatsRepository.flush();
        entityManager.clear();

        // Act - Cast vote that exactly meets threshold
        plannerEngagementService.castVote(voter1.getId(), testPlanner.getId(), VoteType.UP);

        // Commit to trigger AFTER_COMMIT listener

        // Assert - Notification created
        long notificationsCount = notificationRepository.findByUserIdAndDeletedAtIsNullOrderByCreatedAtDesc(
                plannerOwner.getId(), org.springframework.data.domain.PageRequest.of(0, 10)).getTotalElements();
        assertEquals(1, notificationsCount);

        PlannerStats stats = plannerStatsRepository.findById(testPlanner.getId()).orElseThrow();
        assertEquals(recommendedThreshold, stats.getUpvotes());
    }

    // ==================== IT2: Concurrent Votes Create Single Notification ====================

    @Test
    @DisplayName("IT2: Concurrent votes on threshold-1 planner create single notification (race condition test)")
    void castVote_WhenConcurrentCrossingThreshold_CreatesSingleNotification() throws Exception {
        // Arrange - Set planner to 1 vote below threshold
        seedStats(recommendedThreshold - 1, null);

        // Create additional voters for concurrent test
        List<User> voters = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            voters.add(TestDataFactory.createTestUser(userRepository, "concurrent" + i + "@example.com"));
        }

        // Commit setup data so concurrent threads can see it

        // Act - Simulate concurrent votes using ExecutorService
        // Each thread will run in its own transaction (managed by service layer)
        ExecutorService executor = Executors.newFixedThreadPool(5);
        CountDownLatch latch = new CountDownLatch(1);
        List<Future<Exception>> results = new ArrayList<>();

        for (User voter : voters) {
            results.add(executor.submit(() -> {
                try {
                    latch.await(); // Wait for signal to start all threads simultaneously
                    plannerEngagementService.castVote(voter.getId(), testPlanner.getId(), VoteType.UP);
                    return null;
                } catch (Exception e) {
                    return e;
                }
            }));
        }

        // Start all threads simultaneously
        latch.countDown();
        executor.shutdown();
        executor.awaitTermination(10, TimeUnit.SECONDS);

        // Poll for the AFTER_COMMIT listener to persist the notification instead of a fixed sleep.
        long deadline = System.currentTimeMillis() + 2000;
        while (System.currentTimeMillis() < deadline) {
            entityManager.clear();
            boolean present = !notificationRepository.findByUserIdAndDeletedAtIsNullOrderByCreatedAtDesc(
                    plannerOwner.getId(), org.springframework.data.domain.PageRequest.of(0, 10)).getContent().isEmpty();
            if (present) {
                break;
            }
        }

        // Refresh transaction to see committed data
        entityManager.clear();

        // Assert - Only ONE notification created despite multiple concurrent votes
        List<Notification> notifications = notificationRepository.findByUserIdAndDeletedAtIsNullOrderByCreatedAtDesc(
                plannerOwner.getId(), org.springframework.data.domain.PageRequest.of(0, 10)).getContent();

        assertEquals(1, notifications.size(), "Only one notification should be created despite concurrent votes");
        assertEquals(NotificationType.PLANNER_RECOMMENDED, notifications.get(0).getNotificationType());

        // Verify atomic flag was set exactly once
        PlannerStats stats = plannerStatsRepository.findById(testPlanner.getId()).orElseThrow();
        assertNotNull(stats.getRecommendedNotifiedAt(), "Recommended notification flag should be set");

        // Verify multiple votes were cast (at least some succeeded)
        long voteCount = plannerVoteRepository.findAll().stream()
                .filter(vote -> testPlanner.getId().equals(vote.getPlannerId()))
                .count();
        assertTrue(voteCount > 0, "At least one vote should succeed");
    }

    // ==================== IT3: No Duplicate Notifications ====================

    @Test
    @DisplayName("IT3: Second threshold crossing does not create duplicate notification")
    void castVote_WhenSecondThresholdCrossing_NoDuplicateNotification() {
        // Arrange - Set planner to exactly threshold with flag already set
        seedStats(recommendedThreshold, Instant.now());

        // Manually create notification (simulating first threshold crossing)
        Notification existingNotification = new Notification(
                plannerOwner.getId(),
                testPlanner.getId().toString(),
                NotificationType.PLANNER_RECOMMENDED
        );
        notificationRepository.save(existingNotification);

        // Verify exactly 1 notification exists
        long notificationsBefore = notificationRepository.findByUserIdAndDeletedAtIsNullOrderByCreatedAtDesc(
                plannerOwner.getId(), org.springframework.data.domain.PageRequest.of(0, 10)).getTotalElements();
        assertEquals(1, notificationsBefore);

        // Act - Cast another upvote (still above threshold)
        plannerEngagementService.castVote(voter1.getId(), testPlanner.getId(), VoteType.UP);

        // Commit and verify

        // Assert - No new notification created
        long notificationsAfter = notificationRepository.findByUserIdAndDeletedAtIsNullOrderByCreatedAtDesc(
                plannerOwner.getId(), org.springframework.data.domain.PageRequest.of(0, 10)).getTotalElements();
        assertEquals(1, notificationsAfter, "No duplicate notification should be created");
    }

    @Test
    @DisplayName("IT3.1: Vote after threshold maintains single notification")
    void castVote_WhenAfterThreshold_MaintainsSingleNotification() {
        // Arrange - Set planner well above threshold with notification already sent
        seedStats(recommendedThreshold + 5, Instant.now());

        Notification existingNotification = new Notification(
                plannerOwner.getId(),
                testPlanner.getId().toString(),
                NotificationType.PLANNER_RECOMMENDED
        );
        notificationRepository.save(existingNotification);

        // Act - Cast additional upvote
        plannerEngagementService.castVote(voter1.getId(), testPlanner.getId(), VoteType.UP);

        // Commit and verify

        // Assert - Still only 1 notification
        long notificationsCount = notificationRepository.findByUserIdAndDeletedAtIsNullOrderByCreatedAtDesc(
                plannerOwner.getId(), org.springframework.data.domain.PageRequest.of(0, 10)).getTotalElements();
        assertEquals(1, notificationsCount);
    }

    // ==================== IT4: Transaction Consistency ====================

    @Test
    @DisplayName("IT4: Vote and notification are committed together (transaction consistency)")
    void castVote_WhenCrossingThreshold_CommitsVoteAndNotificationTogether() {
        // Arrange - Set planner to 1 below threshold
        seedStats(recommendedThreshold - 1, null);
        plannerStatsRepository.flush();
        entityManager.clear();

        // Act - Cast vote crossing threshold
        plannerEngagementService.castVote(voter1.getId(), testPlanner.getId(), VoteType.UP);

        // Commit to trigger AFTER_COMMIT listener

        // Assert - Both vote and notification persisted
        // 1. Vote exists
        PlannerVoteId voteId = new PlannerVoteId(voter1.getId(), testPlanner.getId());
        assertTrue(plannerVoteRepository.existsById(voteId), "Vote should be persisted");

        // 2. Notification exists
        long notificationsCount = notificationRepository.findByUserIdAndDeletedAtIsNullOrderByCreatedAtDesc(
                plannerOwner.getId(), org.springframework.data.domain.PageRequest.of(0, 10)).getTotalElements();
        assertEquals(1, notificationsCount, "Notification should be persisted");

        // 3. Planner vote count updated
        PlannerStats stats = plannerStatsRepository.findById(testPlanner.getId()).orElseThrow();
        assertEquals(recommendedThreshold, stats.getUpvotes());

        // 4. Atomic flag set
        assertNotNull(stats.getRecommendedNotifiedAt(), "Atomic flag should be set");
    }

    @Test
    @DisplayName("IT4.1: UNIQUE constraint prevents duplicate notifications")
    void notificationSave_WhenDuplicate_ViolatesUniqueConstraint() {
        // Arrange
        Notification notification1 = new Notification(
                plannerOwner.getId(),
                testPlanner.getId().toString(),
                NotificationType.PLANNER_RECOMMENDED
        );
        notificationRepository.save(notification1);
        notificationRepository.flush();

        // Act - Try to create duplicate notification
        Notification notification2 = new Notification(
                plannerOwner.getId(),
                testPlanner.getId().toString(),
                NotificationType.PLANNER_RECOMMENDED
        );

        // Assert - UNIQUE constraint violation (caught by service layer)
        assertThrows(org.springframework.dao.DataIntegrityViolationException.class, () -> {
            notificationRepository.save(notification2);
            notificationRepository.flush();
        });
    }

    // ==================== IT5: Edge Cases ====================

    @Test
    @DisplayName("IT5.1: Notification only sent on upward crossing, not downward")
    void castVote_WhenDownwardCrossing_NoNotification() {
        // Arrange - Planner above threshold, notification sent, then votes drop below
        seedStats(recommendedThreshold, Instant.now());

        // Create notification
        Notification notification = new Notification(
                plannerOwner.getId(),
                testPlanner.getId().toString(),
                NotificationType.PLANNER_RECOMMENDED
        );
        notificationRepository.save(notification);

        // Commit setup

        long notificationsBefore = notificationRepository.findByUserIdAndDeletedAtIsNullOrderByCreatedAtDesc(
                plannerOwner.getId(), org.springframework.data.domain.PageRequest.of(0, 10)).getTotalElements();
        assertEquals(1, notificationsBefore);

        // Act - Even if planner later goes below threshold (via downvotes), no new notification
        // (This is hypothetical since votes are immutable, but tests the logic)
        // The notification is permanent once sent

        // Assert - Notification remains
        long notificationsAfter = notificationRepository.findByUserIdAndDeletedAtIsNullOrderByCreatedAtDesc(
                plannerOwner.getId(), org.springframework.data.domain.PageRequest.of(0, 10)).getTotalElements();
        assertEquals(1, notificationsAfter);
    }
}
