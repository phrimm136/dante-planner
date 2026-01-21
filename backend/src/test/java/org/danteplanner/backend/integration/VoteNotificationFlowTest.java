package org.danteplanner.backend.integration;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

import org.danteplanner.backend.config.TestConfig;
import org.danteplanner.backend.entity.*;
import org.danteplanner.backend.repository.*;
import org.danteplanner.backend.service.NotificationService;
import org.danteplanner.backend.service.PlannerService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.transaction.TestTransaction;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration test for Vote → Notification Flow.
 *
 * <p>Tests the complete end-to-end flow from vote crossing threshold
 * → atomic flag check → notification creation with race condition prevention.</p>
 *
 * <p>DISABLED: Cannot test AFTER_COMMIT events in @Transactional tests.
 * The transaction never commits due to test rollback, so @TransactionalEventListener(AFTER_COMMIT)
 * never fires. TestTransaction.flagForCommit() approach conflicts with service layer transactions.</p>
 *
 * @see <a href="https://www.baeldung.com/spring-test-programmatic-transactions">Baeldung - Programmatic Transactions</a>
 */
@SpringBootTest
@ActiveProfiles("test")
@Import(TestConfig.class)
@Transactional
@Disabled("AFTER_COMMIT events incompatible with @Transactional test rollback. " +
          "TestTransaction.flagForCommit() conflicts with service layer transactions.")
class VoteNotificationFlowTest {

    @Autowired
    private PlannerService plannerService;

    @Autowired
    private NotificationService notificationService;

    @Autowired
    private PlannerRepository plannerRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PlannerVoteRepository plannerVoteRepository;

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
        // Clean up
        notificationRepository.deleteAll();
        plannerVoteRepository.deleteAll();
        plannerRepository.deleteAll();
        userRepository.deleteAll();

        // Recreate sentinel user using native SQL (JPA IDENTITY ignores explicit IDs)
        org.danteplanner.backend.config.TestDataInitializer.createSentinelUser(entityManager);

        // Create planner owner
        plannerOwner = User.builder()
                .email("owner@example.com")
                .provider("google")
                .providerId("google-owner")
                .usernameEpithet("W_CORP")
                .usernameSuffix("own01")
                .build();
        plannerOwner = userRepository.save(plannerOwner);

        // Create voters
        voter1 = User.builder()
                .email("voter1@example.com")
                .provider("google")
                .providerId("google-voter1")
                .usernameEpithet("W_CORP")
                .usernameSuffix("vot01")
                .build();
        voter1 = userRepository.save(voter1);

        voter2 = User.builder()
                .email("voter2@example.com")
                .provider("google")
                .providerId("google-voter2")
                .usernameEpithet("W_CORP")
                .usernameSuffix("vot02")
                .build();
        voter2 = userRepository.save(voter2);

        voter3 = User.builder()
                .email("voter3@example.com")
                .provider("google")
                .providerId("google-voter3")
                .usernameEpithet("W_CORP")
                .usernameSuffix("vot03")
                .build();
        voter3 = userRepository.save(voter3);

        // Create published planner with initial vote counts at 0
        testPlanner = Planner.builder()
                .id(UUID.randomUUID())
                .user(plannerOwner)
                .title("Test Planner for Notification")
                .category("5F")
                .status("published")
                .content("{\"data\":\"test\"}")
                .published(true)
                .upvotes(0)
                .schemaVersion(1)
                .contentVersion(6)
                .plannerType(PlannerType.MIRROR_DUNGEON)
                .savedAt(Instant.now())
                .build();
        testPlanner = plannerRepository.save(testPlanner);
        plannerRepository.flush();
        entityManager.clear();
    }

    /**
     * Commits the current transaction to trigger AFTER_COMMIT listeners,
     * then starts a new transaction for assertions.
     */
    private void commitAndStartNewTransaction() {
        TestTransaction.flagForCommit();
        TestTransaction.end();
        TestTransaction.start();
    }

    // ==================== IT1: Vote Crossing Threshold Creates Notification ====================

    @Test
    @DisplayName("IT1: Vote crossing threshold (9→10) creates PLANNER_RECOMMENDED notification")
    void voteCrossingThreshold_CreatesNotification() {
        // Arrange - Set planner to 1 vote below threshold
        testPlanner.setUpvotes(recommendedThreshold - 1);
        plannerRepository.save(testPlanner);
        plannerRepository.flush();
        entityManager.clear();

        // Verify no notification exists before
        long notificationsBefore = notificationRepository.findByUserIdAndDeletedAtIsNullOrderByCreatedAtDesc(
                plannerOwner.getId(), org.springframework.data.domain.PageRequest.of(0, 10)).getTotalElements();
        assertEquals(0, notificationsBefore);

        // Act - Cast vote that crosses threshold
        plannerService.castVote(voter1.getId(), testPlanner.getId(), VoteType.UP);

        // Commit to trigger AFTER_COMMIT listener, then start new transaction for assertions
        commitAndStartNewTransaction();

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

        // Verify planner flag set
        Planner updated = plannerRepository.findById(testPlanner.getId()).orElseThrow();
        assertNotNull(updated.getRecommendedNotifiedAt());
    }

    @Test
    @DisplayName("IT1.1: Vote below threshold does not create notification")
    void voteBelowThreshold_NoNotification() {
        // Arrange - Set planner to 2 votes below threshold
        testPlanner.setUpvotes(recommendedThreshold - 2);
        plannerRepository.save(testPlanner);
        plannerRepository.flush();
        entityManager.clear();

        // Act - Cast vote that doesn't cross threshold
        plannerService.castVote(voter1.getId(), testPlanner.getId(), VoteType.UP);

        // Commit to trigger any listeners, then start new transaction for assertions
        commitAndStartNewTransaction();

        // Assert - No notification created
        long notificationsCount = notificationRepository.findByUserIdAndDeletedAtIsNullOrderByCreatedAtDesc(
                plannerOwner.getId(), org.springframework.data.domain.PageRequest.of(0, 10)).getTotalElements();
        assertEquals(0, notificationsCount);

        // Verify planner flag NOT set
        Planner updated = plannerRepository.findById(testPlanner.getId()).orElseThrow();
        assertNull(updated.getRecommendedNotifiedAt());
    }

    @Test
    @DisplayName("IT1.2: Vote exactly at threshold creates notification")
    void voteExactlyAtThreshold_CreatesNotification() {
        // Arrange - Set planner to 1 vote below threshold
        testPlanner.setUpvotes(recommendedThreshold - 1);
        plannerRepository.save(testPlanner);
        plannerRepository.flush();
        entityManager.clear();

        // Act - Cast vote that exactly meets threshold
        plannerService.castVote(voter1.getId(), testPlanner.getId(), VoteType.UP);

        // Commit to trigger AFTER_COMMIT listener
        commitAndStartNewTransaction();

        // Assert - Notification created
        long notificationsCount = notificationRepository.findByUserIdAndDeletedAtIsNullOrderByCreatedAtDesc(
                plannerOwner.getId(), org.springframework.data.domain.PageRequest.of(0, 10)).getTotalElements();
        assertEquals(1, notificationsCount);

        Planner updated = plannerRepository.findById(testPlanner.getId()).orElseThrow();
        assertEquals(recommendedThreshold, updated.getUpvotes());
    }

    // ==================== IT2: Concurrent Votes Create Single Notification ====================

    @Test
    @DisplayName("IT2: Concurrent votes on threshold-1 planner create single notification (race condition test)")
    void concurrentVotesCrossingThreshold_CreatesSingleNotification() throws Exception {
        // Arrange - Set planner to 1 vote below threshold
        testPlanner.setUpvotes(recommendedThreshold - 1);
        plannerRepository.save(testPlanner);

        // Create additional voters for concurrent test
        List<User> voters = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            User voter = User.builder()
                    .email("concurrent" + i + "@example.com")
                    .provider("google")
                    .providerId("google-concurrent-" + i)
                    .usernameEpithet("W_CORP")
                    .usernameSuffix("conc" + i)
                    .build();
            voters.add(userRepository.save(voter));
        }

        // Commit setup data so concurrent threads can see it
        commitAndStartNewTransaction();

        // Act - Simulate concurrent votes using ExecutorService
        // Each thread will run in its own transaction (managed by service layer)
        ExecutorService executor = Executors.newFixedThreadPool(5);
        CountDownLatch latch = new CountDownLatch(1);
        List<Future<Exception>> results = new ArrayList<>();

        for (User voter : voters) {
            results.add(executor.submit(() -> {
                try {
                    latch.await(); // Wait for signal to start all threads simultaneously
                    plannerService.castVote(voter.getId(), testPlanner.getId(), VoteType.UP);
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

        // Allow time for AFTER_COMMIT listeners to execute
        Thread.sleep(100);

        // Refresh transaction to see committed data
        entityManager.clear();

        // Assert - Only ONE notification created despite multiple concurrent votes
        List<Notification> notifications = notificationRepository.findByUserIdAndDeletedAtIsNullOrderByCreatedAtDesc(
                plannerOwner.getId(), org.springframework.data.domain.PageRequest.of(0, 10)).getContent();

        assertEquals(1, notifications.size(), "Only one notification should be created despite concurrent votes");
        assertEquals(NotificationType.PLANNER_RECOMMENDED, notifications.get(0).getNotificationType());

        // Verify atomic flag was set exactly once
        Planner updated = plannerRepository.findById(testPlanner.getId()).orElseThrow();
        assertNotNull(updated.getRecommendedNotifiedAt(), "Recommended notification flag should be set");

        // Verify multiple votes were cast (at least some succeeded)
        long voteCount = plannerVoteRepository.count();
        assertTrue(voteCount > 0, "At least one vote should succeed");
    }

    // ==================== IT3: No Duplicate Notifications ====================

    @Test
    @DisplayName("IT3: Second threshold crossing does not create duplicate notification")
    void secondThresholdCrossing_NoDuplicateNotification() {
        // Arrange - Set planner to exactly threshold with flag already set
        testPlanner.setUpvotes(recommendedThreshold);
        testPlanner.setRecommendedNotifiedAt(Instant.now());
        plannerRepository.save(testPlanner);

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
        plannerService.castVote(voter1.getId(), testPlanner.getId(), VoteType.UP);

        // Commit and verify
        commitAndStartNewTransaction();

        // Assert - No new notification created
        long notificationsAfter = notificationRepository.findByUserIdAndDeletedAtIsNullOrderByCreatedAtDesc(
                plannerOwner.getId(), org.springframework.data.domain.PageRequest.of(0, 10)).getTotalElements();
        assertEquals(1, notificationsAfter, "No duplicate notification should be created");
    }

    @Test
    @DisplayName("IT3.1: Vote after threshold maintains single notification")
    void voteAfterThreshold_MaintainsSingleNotification() {
        // Arrange - Set planner well above threshold with notification already sent
        testPlanner.setUpvotes(recommendedThreshold + 5);
        testPlanner.setRecommendedNotifiedAt(Instant.now());
        plannerRepository.save(testPlanner);

        Notification existingNotification = new Notification(
                plannerOwner.getId(),
                testPlanner.getId().toString(),
                NotificationType.PLANNER_RECOMMENDED
        );
        notificationRepository.save(existingNotification);

        // Act - Cast additional upvote
        plannerService.castVote(voter1.getId(), testPlanner.getId(), VoteType.UP);

        // Commit and verify
        commitAndStartNewTransaction();

        // Assert - Still only 1 notification
        long notificationsCount = notificationRepository.findByUserIdAndDeletedAtIsNullOrderByCreatedAtDesc(
                plannerOwner.getId(), org.springframework.data.domain.PageRequest.of(0, 10)).getTotalElements();
        assertEquals(1, notificationsCount);
    }

    // ==================== IT4: Transaction Consistency ====================

    @Test
    @DisplayName("IT4: Vote and notification are committed together (transaction consistency)")
    void voteAndNotification_CommittedTogether() {
        // Arrange - Set planner to 1 below threshold
        testPlanner.setUpvotes(recommendedThreshold - 1);
        plannerRepository.save(testPlanner);
        plannerRepository.flush();
        entityManager.clear();

        // Act - Cast vote crossing threshold
        plannerService.castVote(voter1.getId(), testPlanner.getId(), VoteType.UP);

        // Commit to trigger AFTER_COMMIT listener
        commitAndStartNewTransaction();

        // Assert - Both vote and notification persisted
        // 1. Vote exists
        PlannerVoteId voteId = new PlannerVoteId(voter1.getId(), testPlanner.getId());
        assertTrue(plannerVoteRepository.existsById(voteId), "Vote should be persisted");

        // 2. Notification exists
        long notificationsCount = notificationRepository.findByUserIdAndDeletedAtIsNullOrderByCreatedAtDesc(
                plannerOwner.getId(), org.springframework.data.domain.PageRequest.of(0, 10)).getTotalElements();
        assertEquals(1, notificationsCount, "Notification should be persisted");

        // 3. Planner vote count updated
        Planner updated = plannerRepository.findById(testPlanner.getId()).orElseThrow();
        assertEquals(recommendedThreshold, updated.getUpvotes());

        // 4. Atomic flag set
        assertNotNull(updated.getRecommendedNotifiedAt(), "Atomic flag should be set");
    }

    @Test
    @DisplayName("IT4.1: UNIQUE constraint prevents duplicate notifications")
    void uniqueConstraint_PreventsDuplicateNotifications() {
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
    void downwardCrossing_NoNotification() {
        // Arrange - Planner above threshold, notification sent, then votes drop below
        testPlanner.setUpvotes(recommendedThreshold);
        testPlanner.setRecommendedNotifiedAt(Instant.now());
        plannerRepository.save(testPlanner);

        // Create notification
        Notification notification = new Notification(
                plannerOwner.getId(),
                testPlanner.getId().toString(),
                NotificationType.PLANNER_RECOMMENDED
        );
        notificationRepository.save(notification);

        // Commit setup
        commitAndStartNewTransaction();

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
