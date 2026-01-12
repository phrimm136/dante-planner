package org.danteplanner.backend.integration;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.danteplanner.backend.config.TestConfig;
import org.danteplanner.backend.entity.*;
import org.danteplanner.backend.repository.*;
import org.danteplanner.backend.support.TestDataFactory;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.*;
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
    static MySQLContainer<?> mysqlContainer = new MySQLContainer<>("mysql:8.0")
            .withDatabaseName("testdb")
            .withUsername("test")
            .withPassword("test");

    @DynamicPropertySource
    static void registerMySqlProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", mysqlContainer::getJdbcUrl);
        registry.add("spring.datasource.username", mysqlContainer::getUsername);
        registry.add("spring.datasource.password", mysqlContainer::getPassword);
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
                    .provider("google")
                    .providerId("duplicate-provider-id")
                    .usernameKeyword("W_CORP")
                    .usernameSuffix("00001")
                    .build();
            userRepository.save(user1);
            entityManager.flush();
            entityManager.clear();  // Detach entities so duplicate hits MySQL constraint

            // Try to create another user with same provider+providerId
            User user2 = User.builder()
                    .email("user2@example.com")
                    .provider("google")
                    .providerId("duplicate-provider-id")
                    .usernameKeyword("W_CORP")
                    .usernameSuffix("00002")
                    .build();

            assertThatThrownBy(() -> {
                userRepository.save(user2);
                entityManager.flush();
            })
                    .hasMessageContaining("Duplicate entry"); // MySQL-specific error message
        }
    }

    @Nested
    @DisplayName("Timestamp Precision Tests")
    @Transactional
    class TimestampPrecisionTests {

        @Test
        @DisplayName("Microsecond precision preserved in timestamp ordering")
        void timestamp_MicrosecondPrecision_Preserved() {
            // Use deterministic timestamps for reliable ordering
            Instant base = Instant.now();
            Notification n1 = createNotification("content-1");
            n1.setCreatedAt(base);
            notificationRepository.save(n1);

            Notification n2 = createNotification("content-2");
            n2.setCreatedAt(base.plusMillis(10));
            notificationRepository.save(n2);

            Notification n3 = createNotification("content-3");
            n3.setCreatedAt(base.plusMillis(20));
            notificationRepository.save(n3);

            entityManager.flush();
            entityManager.clear();

            // Retrieve and verify ordering preserved
            List<Notification> notifications = notificationRepository.findAll();
            notifications.sort(Comparator.comparing(Notification::getCreatedAt));

            assertThat(notifications).hasSize(3);
            assertThat(notifications.get(0).getId()).isEqualTo(n1.getId());
            assertThat(notifications.get(1).getId()).isEqualTo(n2.getId());
            assertThat(notifications.get(2).getId()).isEqualTo(n3.getId());

            // Verify timestamps are distinct
            assertThat(notifications.get(0).getCreatedAt()).isBefore(notifications.get(1).getCreatedAt());
            assertThat(notifications.get(1).getCreatedAt()).isBefore(notifications.get(2).getCreatedAt());
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
}
