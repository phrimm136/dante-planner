package org.danteplanner.backend.integration;
import org.danteplanner.backend.planner.repository.PlannerVoteRepository;
import org.danteplanner.backend.planner.repository.PlannerRepository;
import org.danteplanner.backend.planner.entity.VoteType;
import org.danteplanner.backend.planner.entity.PlannerStatus;
import org.danteplanner.backend.planner.entity.PlannerVote;
import org.danteplanner.backend.planner.entity.Planner;
import org.danteplanner.backend.user.entity.User;
import org.danteplanner.backend.user.repository.UserRepository;

import org.danteplanner.backend.notification.repository.NotificationRepository;

import org.danteplanner.backend.notification.entity.NotificationType;

import org.danteplanner.backend.notification.entity.Notification;

import org.danteplanner.backend.auth.entity.AuthProviderType;
import jakarta.persistence.EntityManager;
import org.danteplanner.backend.config.TestConfig;
import org.danteplanner.backend.planner.converter.KeywordSetConverter;
import org.danteplanner.backend.shared.entity.*;
import org.danteplanner.backend.repository.*;
import org.danteplanner.backend.planner.service.PlannerEngagementService;
import org.danteplanner.backend.support.TestDataFactory;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.mysql.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.locks.LockSupport;
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
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("it")
@Testcontainers
@Tag("containerized")
@Import(TestConfig.class)
class MySQLIntegrationTest {

    @Container
    static MySQLContainer mysqlContainer = new MySQLContainer("mysql:8.0")
            .withDatabaseName("testdb")
            .withUsername("test")
            .withPassword("test");

    @DynamicPropertySource
    static void registerMySqlProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", mysqlContainer::getJdbcUrl);
        registry.add("spring.datasource.username", mysqlContainer::getUsername);
        registry.add("spring.datasource.password", mysqlContainer::getPassword);
        // Flyway has explicit url/user/password in application.properties; without
        // pointing them at the container it connects to the production DB and fails.
        registry.add("spring.flyway.url", mysqlContainer::getJdbcUrl);
        registry.add("spring.flyway.user", mysqlContainer::getUsername);
        registry.add("spring.flyway.password", mysqlContainer::getPassword);
    }

    @Autowired
    private PlannerRepository plannerRepository;

    @Autowired
    private PlannerVoteRepository voteRepository;

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

    private User testUser;
    private Planner testPlanner;

    @BeforeEach
    void setUp() {
        // Clean up in dependency order (no sentinel user needed for these tests)
        voteRepository.deleteAll();
        plannerRepository.deleteAll();
        notificationRepository.deleteAll();
        userRepository.deleteAll();

        testUser = TestDataFactory.createTestUser(userRepository, "test@example.com");
        testPlanner = TestDataFactory.createTestPlanner(plannerRepository, testUser, true);
    }

    @Nested
    @DisplayName("Concurrent Atomic Operations")
    class ConcurrentAtomicTests {

        @Test
        @DisplayName("20 concurrent votes with no lost updates")
        void concurrentVotes_20Threads_NoLostUpdates() throws InterruptedException {
            // Create 20 users (reduced from 50 to lower CI resource pressure)
            List<User> users = IntStream.range(0, 20)
                    .mapToObj(i -> TestDataFactory.createTestUser(userRepository, "user" + i + "@example.com"))
                    .collect(Collectors.toList());

            // Create ExecutorService for concurrent execution
            ExecutorService executor = Executors.newFixedThreadPool(20);
            CountDownLatch startLatch = new CountDownLatch(1);
            CountDownLatch doneLatch = new CountDownLatch(20);

            // Submit 20 concurrent vote tasks
            for (User user : users) {
                executor.submit(() -> {
                    try {
                        startLatch.await();

                        PlannerVote vote = new PlannerVote(user.getId(), testPlanner.getId(), VoteType.UP);
                        voteRepository.save(vote);

                        doneLatch.countDown();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                });
            }

            // Start all threads simultaneously
            startLatch.countDown();

            // Wait for completion (5 second timeout - reduced from 10s)
            boolean completed = doneLatch.await(5, TimeUnit.SECONDS);
            assertThat(completed).isTrue();

            executor.shutdown();

            // Verify all 20 votes counted
            long voteCount = voteRepository.count();
            assertThat(voteCount).isEqualTo(20);
        }
    }

    @Nested
    @DisplayName("Concurrent castVote Through Service")
    class ConcurrentCastVoteTests {

        @Test
        @DisplayName("N concurrent castVote() calls: upvotes == vote rows and exactly one recommended notification")
        void concurrentCastVote_throughService_atomicCountAndSingleNotification() throws InterruptedException {
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
            assertThat(notificationRepository.count()).isZero();

            ExecutorService executor = Executors.newFixedThreadPool(burstVoters);
            CountDownLatch startLatch = new CountDownLatch(1);
            CountDownLatch doneLatch = new CountDownLatch(burstVoters);
            List<Throwable> failures = Collections.synchronizedList(new ArrayList<>());

            for (int i = seedVotes; i < totalVoters; i++) {
                User voter = users.get(i);
                executor.submit(() -> {
                    try {
                        startLatch.await();
                        castVoteWithDeadlockRetry(voter.getId(), testPlanner.getId());
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
            long voteRows = voteRepository.count();
            Planner reloaded = plannerRepository.findById(testPlanner.getId()).orElseThrow();
            assertThat(voteRows).isEqualTo(totalVoters);
            assertThat(reloaded.getUpvotes()).isEqualTo(totalVoters);

            // CAS gate: the recommended-threshold notification fires exactly once
            long recommendedNotifications = notificationRepository.findAll().stream()
                    .filter(n -> n.getUserId().equals(testUser.getId()))
                    .filter(n -> n.getNotificationType() == NotificationType.PLANNER_RECOMMENDED)
                    .count();
            assertThat(recommendedNotifications).isEqualTo(1);
        }

        // Concurrent votes deadlock on the planner_votes FK + upvotes update (InnoDB
        // lock upgrade); production maps this to a retryable error, so retry like a client.
        private void castVoteWithDeadlockRetry(Long userId, UUID plannerId) {
            for (int attempt = 1; ; attempt++) {
                try {
                    plannerEngagementService.castVote(userId, plannerId, VoteType.UP);
                    return;
                } catch (CannotAcquireLockException deadlock) {
                    if (attempt >= 25) {
                        throw deadlock;
                    }
                    // Backoff before retrying so the winning transaction can commit and
                    // release its locks (parkNanos, not a synchronizer — the retry is the wait).
                    LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(5));
                }
            }
        }
    }

    @Nested
    @DisplayName("MySQL Constraint Validation")
    @Transactional
    class ConstraintValidationTests {

        @Test
        @DisplayName("UNIQUE constraint violation throws DataIntegrityViolationException with 'Duplicate entry'")
        void uniqueConstraint_DuplicateVote_ThrowsException() {
            PlannerVote vote1 = new PlannerVote(testUser.getId(), testPlanner.getId(), VoteType.UP);
            voteRepository.save(vote1);
            entityManager.flush();
            entityManager.clear();  // Detach entities so Hibernate doesn't catch duplicate at session level

            // Try to create duplicate vote - will hit MySQL constraint
            PlannerVote vote2 = new PlannerVote(testUser.getId(), testPlanner.getId(), VoteType.UP);

            assertThatThrownBy(() -> {
                voteRepository.save(vote2);
                entityManager.flush();
            })
                    .hasMessageContaining("Duplicate entry"); // MySQL-specific error message
        }

        @Test
        @DisplayName("UNIQUE constraint on provider+providerId validates MySQL error format")
        void uniqueConstraint_MySQLErrorFormat_ValidatesMessage() {
            // Create user with specific provider ID
            User user1 = User.builder()
                    .email("user1@example.com")
                    .provider(AuthProviderType.GOOGLE)
                    .providerId("duplicate-provider-id")
                    .usernameEpithet("W_CORP")
                    .usernameSuffix("00001")
                    .build();
            userRepository.save(user1);
            entityManager.flush();
            entityManager.clear();  // Detach entities so duplicate hits MySQL constraint

            // Try to create another user with same provider+providerId
            User user2 = User.builder()
                    .email("user2@example.com")
                    .provider(AuthProviderType.GOOGLE)
                    .providerId("duplicate-provider-id")
                    .usernameEpithet("W_CORP")
                    .usernameSuffix("00002")
                    .build();

            assertThatThrownBy(() -> {
                userRepository.save(user2);
                entityManager.flush();
            })
                    .hasMessageContaining("Duplicate entry"); // MySQL-specific error message
        }

        @Test
        @DisplayName("GOOGLE persists as lowercase 'google' in the provider VARCHAR column and reads back as GOOGLE")
        void authProviderType_Google_RoundTripsThroughVarcharColumn() {
            User user = User.builder()
                    .email("provider-roundtrip@example.com")
                    .provider(AuthProviderType.GOOGLE)
                    .providerId("roundtrip-provider-id")
                    .usernameEpithet("W_CORP")
                    .usernameSuffix("00003")
                    .build();
            userRepository.save(user);
            entityManager.flush();
            entityManager.clear();

            String rawColumn = (String) entityManager.createNativeQuery(
                            "SELECT provider FROM users WHERE id = ?")
                    .setParameter(1, user.getId())
                    .getSingleResult();
            assertThat(rawColumn).isEqualTo("google");

            User reloaded = userRepository.findByProviderAndProviderId(
                    AuthProviderType.GOOGLE, "roundtrip-provider-id").orElseThrow();
            assertThat(reloaded.getProvider()).isEqualTo(AuthProviderType.GOOGLE);
        }
    }

    @Nested
    @DisplayName("PlannerStatus ENUM Round-Trip")
    @Transactional
    class PlannerStatusEnumTests {

        @Test
        @DisplayName("SAVED persists as lowercase 'saved' in the ENUM column and reads back as SAVED")
        void plannerStatus_Saved_RoundTripsThroughEnumColumn() {
            Planner planner = TestDataFactory.createTestPlanner(plannerRepository, testUser, false);
            planner.setStatus(PlannerStatus.SAVED);
            plannerRepository.save(planner);
            entityManager.flush();
            entityManager.clear();

            String hexId = planner.getId().toString().replace("-", "");
            String rawColumn = (String) entityManager.createNativeQuery(
                            "SELECT status FROM planners WHERE id = UNHEX(?)")
                    .setParameter(1, hexId)
                    .getSingleResult();
            assertThat(rawColumn).isEqualTo("saved");

            Planner reloaded = plannerRepository.findById(planner.getId()).orElseThrow();
            assertThat(reloaded.getStatus()).isEqualTo(PlannerStatus.SAVED);
        }
    }

    @Nested
    @DisplayName("Hard-Delete Vote Reassignment")
    @Transactional
    class HardDeleteVoteReassignmentTests {

        @Test
        @DisplayName("Reassigning to a sentinel that already voted on the same planner deletes the collision instead of violating the PK")
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
            entityManager.flush();
            entityManager.clear();

            // Replicates the hard-delete vote step: drop collisions, then reassign the rest.
            voteRepository.deleteVotesCollidingWithSentinel(leaving.getId(), sentinel.getId());
            voteRepository.reassignUserVotes(leaving.getId(), sentinel.getId());
            entityManager.flush();
            entityManager.clear();

            assertThat(voteRepository.findByUserIdAndPlannerId(leaving.getId(), sharedPlanner.getId())).isEmpty();
            assertThat(voteRepository.findByUserIdAndPlannerId(leaving.getId(), soloPlanner.getId())).isEmpty();
            assertThat(voteRepository.findByUserIdAndPlannerId(sentinel.getId(), sharedPlanner.getId())).isPresent();
            assertThat(voteRepository.findByUserIdAndPlannerId(sentinel.getId(), soloPlanner.getId())).isPresent();
        }
    }

    @Nested
    @DisplayName("Timestamp Precision Tests")
    @Transactional
    class TimestampPrecisionTests {

        @Test
        @DisplayName("Microsecond precision preserved in timestamp ordering")
        void timestamp_MicrosecondPrecision_Preserved() {
            // created_at is @PrePersist-assigned and updatable=false, so set
            // deterministic microsecond-spaced values via native SQL to verify the
            // TIMESTAMP(6) column preserves sub-second ordering (V042).
            Instant base = Instant.now().truncatedTo(ChronoUnit.MILLIS);
            Notification n1 = createNotification("content-1");
            Notification n2 = createNotification("content-2");
            Notification n3 = createNotification("content-3");
            entityManager.flush();

            overrideCreatedAt(n1.getId(), base);
            overrideCreatedAt(n2.getId(), base.plusNanos(10_000));
            overrideCreatedAt(n3.getId(), base.plusNanos(20_000));
            entityManager.clear();

            List<Notification> notifications = notificationRepository.findAll();
            notifications.sort(Comparator.comparing(Notification::getCreatedAt));

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
    @DisplayName("SelectedKeywords SET Column")
    class SelectedKeywordsTests {

        @Test
        @DisplayName("planner persists and restores every FE keyword through the real SET column")
        void saveAllKeywords_WhenPersistedAndReloaded_RoundTripsThroughSetColumn() {
            // The full set joins to ~375 chars; only the MySQL SET(...) column (bitmask
            // storage) holds it. H2's converter-backed VARCHAR(255) cannot, which is why
            // this empirical all-keywords save lives in the containerized tier.
            Planner planner = TestDataFactory.createTestPlanner(plannerRepository, testUser, false);
            planner.setSelectedKeywords(new HashSet<>(KeywordSetConverter.VALID_KEYWORDS));
            plannerRepository.save(planner);

            // No surrounding @Transactional: save commits in its own session, so this
            // findById re-reads from the DB and exercises the converter's read path.
            Planner reloaded = plannerRepository.findById(planner.getId()).orElseThrow();
            assertThat(reloaded.getSelectedKeywords())
                    .containsExactlyInAnyOrderElementsOf(KeywordSetConverter.VALID_KEYWORDS);
        }
    }
}
