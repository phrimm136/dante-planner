package org.danteplanner.backend.integration;
import org.danteplanner.backend.planner.repository.PlannerVoteRepository;
import org.junit.jupiter.api.BeforeEach;
import org.danteplanner.backend.planner.repository.PlannerRepository;
import org.danteplanner.backend.planner.repository.PlannerStatsRepository;
import org.danteplanner.backend.planner.entity.VoteType;
import org.danteplanner.backend.planner.entity.PlannerStats;
import org.danteplanner.backend.planner.entity.PlannerStatus;
import org.danteplanner.backend.planner.entity.PlannerVote;
import org.danteplanner.backend.planner.entity.Planner;
import org.danteplanner.backend.user.entity.User;
import org.danteplanner.backend.user.repository.UserRepository;

import org.danteplanner.backend.notification.repository.NotificationRepository;

import org.danteplanner.backend.notification.api.NotificationType;

import org.danteplanner.backend.notification.entity.Notification;

import org.danteplanner.backend.user.api.AuthProviderType;
import jakarta.persistence.EntityManager;
import org.danteplanner.backend.config.TestConfig;
import org.danteplanner.backend.planner.entity.PlannerKeywords;
import org.danteplanner.backend.planner.service.PlannerEngagementService;
import org.danteplanner.backend.support.TestDataFactory;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * MySQL Integration Tests using TestContainers.
 *
 * <p>Tests MySQL-specific behavior that H2 in-memory database cannot validate:
 * <ul>
 *   <li>Concurrent atomic operations with real MySQL locking</li>
 *   <li>UNIQUE constraint error message format validation</li>
 *   <li>Timestamp precision (microseconds)</li>
 *   <li>Transaction isolation behavior</li>
 * </ul>
 * </p>
 *
 * <p>Tagged with @Tag("containerized") for optional CI execution.
 * Requires Docker daemon running.</p>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@ActiveProfiles("it")
@Tag("containerized")
@Import(TestConfig.class)
class MySQLIT extends SharedMySqlContainerSupport {





    @Autowired
    private PlannerRepository plannerRepository;

    @Autowired
    private PlannerVoteRepository voteRepository;

    @Autowired
    private PlannerStatsRepository statsRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private PlannerEngagementService plannerEngagementService;

    @Value("${planner.recommended-threshold}")
    private int recommendedThreshold;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private TransactionTemplate transactionTemplate;

    private User testUser;
    private Planner testPlanner;

    @BeforeEach
    void setUp() {
        // Clean up in dependency order (no sentinel user needed for these tests)

        testUser = TestDataFactory.createTestUser(userRepository, "test@example.com");
        testPlanner = TestDataFactory.createTestPlanner(plannerRepository, testUser, true);
    }

    @Nested
    class ConcurrentAtomicTests {

        @Test
        void concurrentVotes_When20Threads_NoLostUpdates() throws InterruptedException {
            // Create 20 users (reduced from 50 to lower CI resource pressure)
            List<User> users = IntStream.range(0, 20)
                    .mapToObj(i -> TestDataFactory.createTestUser(userRepository, "user" + i + "@example.com"))
                    .collect(Collectors.toList());

            // Create ExecutorService for concurrent execution
            ExecutorService executor = Executors.newFixedThreadPool(20);
            CountDownLatch startLatch = new CountDownLatch(1);
            CountDownLatch doneLatch = new CountDownLatch(20);
            List<Throwable> failures = Collections.synchronizedList(new ArrayList<>());

            // Submit 20 concurrent vote tasks
            for (User user : users) {
                executor.submit(() -> {
                    try {
                        startLatch.await();

                        PlannerVote vote = new PlannerVote(user.getId(), testPlanner.getId(), VoteType.UP);
                        voteRepository.save(vote);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    } catch (RuntimeException e) {
                        failures.add(e);
                    } finally {
                        doneLatch.countDown();
                    }
                });
            }

            // Start all threads simultaneously
            startLatch.countDown();

            // The writers queue through a Hikari pool of five that concurrent classes on this
            // context also draw from, so this wait covers contention, not the subject.
            boolean completed = doneLatch.await(30, TimeUnit.SECONDS);
            assertThat(failures).as("every concurrent vote commits").isEmpty();
            assertThat(completed).isTrue();

            executor.shutdown();

            // Verify all 20 votes counted
            // Scoped to this test's planner: the database is shared with every other integration
            // class, so a bare count() also sees their rows and this class's earlier methods'.
            long voteCount = voteRepository.findAll().stream()
                    .filter(vote -> testPlanner.getId().equals(vote.getPlannerId()))
                    .count();
            assertThat(voteCount).isEqualTo(20);
        }
    }

    @Nested
    class ConcurrentCastVoteTests {

        // concurrent-vote-no-deadlock
        @Test
        void concurrentVote_WhenNoDeadlock_AllCountedAndSingleNotification() throws InterruptedException {
            // Seed to one below the threshold sequentially (no notification yet), then run a
            // concurrent burst that crosses it — many burst transactions read upvotes==threshold-1,
            // so the CAS (trySetRecommendedNotified) must dedupe them to exactly one notification.
            int seedVotes = recommendedThreshold - 1;
            int burstVoters = 11;
            int totalVoters = seedVotes + burstVoters;
            List<User> users = IntStream.range(0, totalVoters)
                    .mapToObj(i -> TestDataFactory.createTestUser(userRepository, "voter" + i + "@example.com"))
                    .collect(Collectors.toList());

            for (int i = 0; i < seedVotes; i++) {
                plannerEngagementService.castVote(users.get(i).getId(), testPlanner.getId(), VoteType.UP);
            }
            assertThat(notificationRepository.findAll().stream()
                            .filter(n -> testPlanner.getId().equals(n.getPlannerId()))
                            .count())
                    .as("seeding below the threshold raises no recommendation notification")
                    .isZero();

            ExecutorService executor = Executors.newFixedThreadPool(burstVoters);
            CountDownLatch startLatch = new CountDownLatch(1);
            CountDownLatch doneLatch = new CountDownLatch(burstVoters);
            List<Throwable> failures = Collections.synchronizedList(new ArrayList<>());

            for (int i = seedVotes; i < totalVoters; i++) {
                User voter = users.get(i);
                executor.submit(() -> {
                    try {
                        startLatch.await();
                        // No deadlock retry: every voter writes only planner_stats
                        // (atomic upsert) and the vote row's FK targets the
                        // write-once planner core, so no lock upgrade can cycle
                        plannerEngagementService.castVote(voter.getId(), testPlanner.getId(), VoteType.UP);
                    } catch (Throwable t) {
                        failures.add(t);
                    } finally {
                        doneLatch.countDown();
                    }
                });
            }

            startLatch.countDown();
            boolean completed = doneLatch.await(30, TimeUnit.SECONDS);
            executor.shutdown();

            assertThat(completed).isTrue();
            assertThat(failures).isEmpty();

            // Atomic increment: the denormalized counter equals the real vote-row count (no lost update)
            long voteRows = voteRepository.findAll().stream()
                    .filter(vote -> testPlanner.getId().equals(vote.getPlannerId()))
                    .count();
            int upvotes = statsRepository.findById(testPlanner.getId())
                    .map(PlannerStats::getUpvotes).orElse(0);
            assertThat(voteRows).isEqualTo(totalVoters);
            assertThat(upvotes).isEqualTo(totalVoters);

            // CAS gate: exactly one vote records the event, and its dispatch derives one row.
            // The derivation runs off the voting threads, so the count is polled rather than read
            // the instant the last vote returns.
            long deadline = System.nanoTime() + DERIVATION_DEADLINE_MS * 1_000_000L;
            long recommendedNotifications = countRecommended();
            while (recommendedNotifications < 1 && System.nanoTime() < deadline) {
                Thread.sleep(DERIVATION_POLL_INTERVAL_MS);
                recommendedNotifications = countRecommended();
            }
            assertThat(recommendedNotifications).isEqualTo(1);
        }
    }

    /** Deadline for the outbox dispatch to derive what a committed event owes. */
    private static final long DERIVATION_DEADLINE_MS = 10_000L;

    private static final long DERIVATION_POLL_INTERVAL_MS = 50L;

    private long countRecommended() {
        return notificationRepository.findAll().stream()
                .filter(n -> n.getUserId().equals(testUser.getId()))
                .filter(n -> n.getNotificationType() == NotificationType.PLANNER_RECOMMENDED)
                .count();
    }

    @Nested
    class ConstraintValidationTests {

        @Test
        void uniqueConstraint_WhenDuplicateVote_ThrowsException() {
            PlannerVote vote1 = new PlannerVote(testUser.getId(), testPlanner.getId(), VoteType.UP);
            voteRepository.save(vote1);

            // Try to create duplicate vote - will hit MySQL constraint
            PlannerVote vote2 = new PlannerVote(testUser.getId(), testPlanner.getId(), VoteType.UP);

            assertThatThrownBy(() -> transactionTemplate.executeWithoutResult(status -> {
                voteRepository.save(vote2);
                entityManager.flush();
            }))
                    .hasMessageContaining("Duplicate entry"); // MySQL-specific error message
        }

        @Test
        void uniqueConstraint_WhenMySQLErrorFormat_ValidatesMessage() {
            String providerId = "duplicate-provider-" + UUID.randomUUID();
            // Create user with specific provider ID
            User user1 = User.builder()
                    .email("user1@example.com")
                    .provider(AuthProviderType.GOOGLE)
                    .providerId(providerId)
                    .usernameEpithet("W_CORP")
                    .usernameSuffix(TestDataFactory.uniqueSuffix(""))
                    .build();
            userRepository.save(user1);

            // Try to create another user with same provider+providerId
            User user2 = User.builder()
                    .email("user2@example.com")
                    .provider(AuthProviderType.GOOGLE)
                    .providerId(providerId)
                    .usernameEpithet("W_CORP")
                    .usernameSuffix(TestDataFactory.uniqueSuffix(""))
                    .build();

            assertThatThrownBy(() -> transactionTemplate.executeWithoutResult(status -> {
                userRepository.save(user2);
                entityManager.flush();
            }))
                    .hasMessageContaining("Duplicate entry"); // MySQL-specific error message
        }

        @Test
        void authProviderType_WhenGoogle_RoundTripsThroughVarcharColumn() {
            String providerId = "roundtrip-provider-" + UUID.randomUUID();
            User user = User.builder()
                    .email("provider-roundtrip@example.com")
                    .provider(AuthProviderType.GOOGLE)
                    .providerId(providerId)
                    .usernameEpithet("W_CORP")
                    .usernameSuffix(TestDataFactory.uniqueSuffix(""))
                    .build();
            userRepository.save(user);

            String rawColumn = (String) entityManager.createNativeQuery(
                            "SELECT provider FROM users WHERE id = ?")
                    .setParameter(1, user.getId())
                    .getSingleResult();
            assertThat(rawColumn).isEqualTo("google");

            User reloaded = userRepository.findByProviderAndProviderId(
                    AuthProviderType.GOOGLE, providerId).orElseThrow();
            assertThat(reloaded.getProvider()).isEqualTo(AuthProviderType.GOOGLE);
        }
    }

    @Nested
    class PlannerStatusEnumTests {

        @Test
        void plannerStatus_WhenSaved_RoundTripsThroughEnumColumn() {
            Planner planner = TestDataFactory.planner(testUser)
                    .status(PlannerStatus.SAVED)
                    .save(plannerRepository);

            String hexId = planner.getId().toString().replace("-", "");
            String rawColumn = (String) entityManager.createNativeQuery(
                            "SELECT status FROM planner_content WHERE planner_id = UNHEX(?)")
                    .setParameter(1, hexId)
                    .getSingleResult();
            assertThat(rawColumn).isEqualTo("saved");

            Planner reloaded = plannerRepository.findById(planner.getId()).orElseThrow();
            assertThat(reloaded.getStatus()).isEqualTo(PlannerStatus.SAVED);
        }
    }

    @Nested
    class HardDeleteVoteReassignmentTests {

        @Test
        void reassignVotes_WhenSentinelAlreadyVotedSamePlanner_DeletesCollisionThenReassigns() {
            User leaving = TestDataFactory.createTestUser(userRepository, "leaving@example.com");
            User sentinel = TestDataFactory.createTestUser(userRepository, "sentinel@example.com");
            Planner sharedPlanner = testPlanner;
            Planner soloPlanner = TestDataFactory.createTestPlanner(plannerRepository, leaving, true);

            // Sentinel and the leaving user both voted on sharedPlanner -> collision on (user_id, planner_id).
            // The leaving user also voted on soloPlanner -> must be reassigned cleanly.
            voteRepository.save(new PlannerVote(sentinel.getId(), sharedPlanner.getId(), VoteType.UP));
            voteRepository.save(new PlannerVote(leaving.getId(), sharedPlanner.getId(), VoteType.UP));
            voteRepository.save(new PlannerVote(leaving.getId(), soloPlanner.getId(), VoteType.UP));

            // Replicates the hard-delete vote step: drop collisions, then reassign the rest.
            transactionTemplate.executeWithoutResult(status -> {
                voteRepository.deleteVotesCollidingWithSentinel(leaving.getId(), sentinel.getId());
                voteRepository.reassignUserVotes(leaving.getId(), sentinel.getId());
            });

            assertThat(voteRepository.findByUserIdAndPlannerId(leaving.getId(), sharedPlanner.getId())).isEmpty();
            assertThat(voteRepository.findByUserIdAndPlannerId(leaving.getId(), soloPlanner.getId())).isEmpty();
            assertThat(voteRepository.findByUserIdAndPlannerId(sentinel.getId(), sharedPlanner.getId())).isPresent();
            assertThat(voteRepository.findByUserIdAndPlannerId(sentinel.getId(), soloPlanner.getId())).isPresent();
        }
    }

    @Nested
    class TimestampPrecisionTests {

        @Test
        void timestamp_WhenMicrosecondPrecision_Preserved() {
            // created_at is @PrePersist-assigned and updatable=false, so set
            // deterministic microsecond-spaced values via native SQL to verify the
            // TIMESTAMP(6) column preserves sub-second ordering (V042).
            Instant base = Instant.now().truncatedTo(ChronoUnit.MILLIS);
            Notification n1 = createNotification("content-1");
            Notification n2 = createNotification("content-2");
            Notification n3 = createNotification("content-3");

            transactionTemplate.executeWithoutResult(status -> {
                overrideCreatedAt(n1.getId(), base);
                overrideCreatedAt(n2.getId(), base.plusNanos(10_000));
                overrideCreatedAt(n3.getId(), base.plusNanos(20_000));
            });

            // The three this test wrote, in created order: the table also holds every other
            // class's notifications.
            List<Notification> notifications = notificationRepository.findAll().stream()
                    .filter(n -> List.of(n1.getId(), n2.getId(), n3.getId()).contains(n.getId()))
                    .sorted(Comparator.comparing(Notification::getCreatedAt))
                    .collect(Collectors.toCollection(ArrayList::new));

            assertThat(notifications).hasSize(3);
            assertThat(notifications.get(0).getId()).isEqualTo(n1.getId());
            assertThat(notifications.get(1).getId()).isEqualTo(n2.getId());
            assertThat(notifications.get(2).getId()).isEqualTo(n3.getId());

            // Sub-second differences survive the round-trip through TIMESTAMP(6)
            assertThat(notifications.get(0).getCreatedAt()).isBefore(notifications.get(1).getCreatedAt());
            assertThat(notifications.get(1).getCreatedAt()).isBefore(notifications.get(2).getCreatedAt());
        }

        private void overrideCreatedAt(Long id, Instant value) {
            entityManager.createNativeQuery(
                            "UPDATE notifications SET created_at = ? WHERE id = ?")
                    .setParameter(1, java.sql.Timestamp.from(value))
                    .setParameter(2, id)
                    .executeUpdate();
        }

        private Notification createNotification(String contentId) {
            Notification notification = new Notification(
                    testUser.getId(),
                    contentId,
                    NotificationType.COMMENT_RECEIVED
            );
            return notificationRepository.save(notification);
        }
    }

    @Nested
    class SelectedKeywordsTests {

        @Test
        void saveAllKeywords_WhenPersistedAndReloaded_RoundTripsThroughSetColumn() {
            // Empirical all-keywords save against the real MySQL JSON column: the full
            // set must survive the converter's write and read paths end-to-end, which
            // is why it lives in the containerized tier.
            Planner planner = TestDataFactory.planner(testUser)
                    .selectedKeywords(new HashSet<>(PlannerKeywords.VALID_KEYWORDS))
                    .save(plannerRepository);

            // No surrounding @Transactional: save commits in its own session, so this
            // findById re-reads from the DB and exercises the converter's read path.
            Planner reloaded = plannerRepository.findById(planner.getId()).orElseThrow();
            assertThat(reloaded.getSelectedKeywords())
                    .containsExactlyInAnyOrderElementsOf(PlannerKeywords.VALID_KEYWORDS);
        }
    }
}
