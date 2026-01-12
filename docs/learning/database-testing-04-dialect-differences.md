# Database Testing: H2 vs Production Dialect Differences

**Learning Goal:** Understand why H2 tests aren't enough, what dialect-specific bugs slip through, and how to use TestContainers for production-identical testing.

---

## Why H2 Isn't Enough

### The Fundamental Problem

**H2 is convenient but doesn't match production database behavior.**

```java
@SpringBootTest
@ActiveProfiles("test")  // Uses H2 in-memory database
class RepositoryTest {
    // All tests pass with H2 ✅

    // Production uses MySQL
    // Tests deploy to production
    // Production breaks 💥
}
```

**What H2 gets wrong:**
- Different locking behavior (pessimistic vs optimistic)
- Different date/time precision (milliseconds vs microseconds)
- Different string collation (case-insensitive vs case-sensitive)
- Different constraint error messages
- Different query optimizer behavior
- Missing database-specific features (JSON functions, full-text search)

### The Business Impact

**Production-only bugs that H2 tests miss:**

1. **Deadlocks:** H2 uses pessimistic locking, MySQL uses optimistic → deadlocks only in production
2. **Date precision bugs:** Timestamp comparisons fail due to microsecond differences
3. **Query performance:** H2 optimizer is naive, MySQL optimizer is sophisticated → slow queries only in production
4. **Constraint violations:** Different error messages break error handling logic
5. **Encoding issues:** H2 handles UTF-8 differently than MySQL

**Real example:**
```java
// Test passes in H2
@Test
void save_PlannersWithSameTitle_BothSucceed() {
    plannerRepository.save(createPlanner(title = "Test"));
    plannerRepository.save(createPlanner(title = "test"));  // lowercase
    entityManager.flush();

    assertEquals(2, plannerRepository.count());  // ✅ H2: Pass (case-insensitive)
}

// Production breaks
// MySQL with utf8mb4_bin collation: Case-sensitive!
// If UNIQUE constraint on title → second save fails
// Result: 500 error in production, but tests passed
```

---

## Dialect Differences That Matter

### 1. Locking Behavior

| Database | Default Strategy | Deadlock Detection |
|----------|-----------------|-------------------|
| **H2** | Pessimistic (lock immediately) | Basic (timeout-based) |
| **MySQL** | Optimistic (lock at commit) | Advanced (cycle detection) |
| **PostgreSQL** | MVCC (multi-version) | Advanced |

**Why it matters:**
```java
// Thread 1: Update planner A, then update planner B
// Thread 2: Update planner B, then update planner A

// H2: First lock wins, second waits → no deadlock (pessimistic)
// MySQL: Both acquire optimistic locks → deadlock at commit!

// Test passes in H2, production deadlocks ❌
```

#### Test Pattern for Locking:
```java
@Test
@Tag("containerized")  // Run with TestContainers MySQL
@DisplayName("MySQL locking: Deadlock detected")
void concurrentUpdates_OppositeOrder_Deadlock() throws Exception {
    // WHY: Documents MySQL-specific locking behavior
    // H2 won't catch this deadlock!

    // Arrange
    Planner plannerA = createTestPlanner();
    Planner plannerB = createTestPlanner();
    entityManager.flush();

    // Act: Two transactions, opposite lock order
    ExecutorService executor = Executors.newFixedThreadPool(2);
    CountDownLatch latch = new CountDownLatch(1);

    Future<?> tx1 = executor.submit(() -> {
        try {
            latch.await();
            transactionTemplate.execute(status -> {
                plannerRepository.incrementUpvotes(plannerA.getId());
                Thread.sleep(100);  // Hold lock
                plannerRepository.incrementUpvotes(plannerB.getId());
                return null;
            });
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    });

    Future<?> tx2 = executor.submit(() -> {
        try {
            latch.await();
            transactionTemplate.execute(status -> {
                plannerRepository.incrementUpvotes(plannerB.getId());
                Thread.sleep(100);  // Hold lock
                plannerRepository.incrementUpvotes(plannerA.getId());
                return null;
            });
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    });

    latch.countDown();

    // Assert: MySQL detects deadlock (one transaction fails)
    executor.shutdown();
    assertThat(List.of(tx1, tx2))
        .anyMatch(future -> {
            try {
                future.get(5, TimeUnit.SECONDS);
                return false;
            } catch (ExecutionException e) {
                return e.getCause() instanceof DeadlockLoserDataAccessException;
            } catch (Exception e) {
                return false;
            }
        });

    // LESSON: H2 won't fail this test (uses different locking)
    // Only TestContainers MySQL catches this
}
```

---

### 2. Date/Time Precision

| Database | TIMESTAMP Precision | Behavior |
|----------|-------------------|----------|
| **H2** | Milliseconds (3 digits) | 2025-01-01 12:00:00.123 |
| **MySQL** | Microseconds (6 digits) | 2025-01-01 12:00:00.123456 |

**Why it matters:**
```java
// Create two planners rapidly
Instant now = Instant.now();  // 2025-01-01T12:00:00.123456Z
planner1.setCreatedAt(now);
plannerRepository.save(planner1);

planner2.setCreatedAt(now);  // Same instant!
plannerRepository.save(planner2);

// H2: Both timestamps truncated to milliseconds → same timestamp
// SELECT * FROM planner ORDER BY created_at DESC
// Result: Ordering unpredictable (both have same timestamp)

// MySQL: Full microsecond precision → different timestamps
// Result: Ordering deterministic
```

**Production bug:**
```java
// Test assumes ordering by timestamp is deterministic
@Test
void findAll_OrderByCreated_DeterministicOrder() {
    Planner first = createPlanner();
    Planner second = createPlanner();  // Created microseconds later

    List<Planner> result = plannerRepository.findAllOrderByCreatedAtDesc();

    // H2: May fail (millisecond precision, same timestamp)
    // MySQL: Passes (microsecond precision, different timestamps)
    assertEquals(second.getId(), result.get(0).getId());
}
```

#### Test Pattern:
```java
@Test
@Tag("containerized")
@DisplayName("MySQL timestamp precision: Microsecond ordering")
void findAll_RapidCreation_MicrosecondOrdering() {
    // WHY: Validates timestamp precision matches production
    // H2: milliseconds, MySQL: microseconds

    // Arrange: Create planners in rapid succession
    Instant now = Instant.now();
    List<Planner> planners = new ArrayList<>();

    for (int i = 0; i < 10; i++) {
        Planner p = createPlanner();
        p.setCreatedAt(now.plusNanos(i * 1000));  // 1 microsecond apart
        planners.add(plannerRepository.save(p));
    }
    entityManager.flush();

    // Act: Query ordered by created_at DESC
    List<Planner> result = plannerRepository
        .findAllByOrderByCreatedAtDesc();

    // Assert: Reverse order (newest first)
    for (int i = 0; i < 10; i++) {
        assertEquals(planners.get(9 - i).getId(), result.get(i).getId(),
            "MySQL should preserve microsecond ordering");
    }

    // LESSON: H2 truncates to milliseconds, all timestamps same,
    // order is unpredictable (test fails or flaky)
}
```

---

### 3. String Collation

| Database | Default Collation | Case Sensitivity |
|----------|------------------|-----------------|
| **H2** | Case-insensitive | "Test" == "test" |
| **MySQL utf8mb4_general_ci** | Case-insensitive | "Test" == "test" |
| **MySQL utf8mb4_bin** | Case-sensitive | "Test" != "test" |

**Why it matters:**
```java
// UNIQUE constraint on username
User user1 = User.builder()
    .usernameKeyword("W_CORP")
    .usernameSuffix("abc")
    .build();
userRepository.save(user1);

User user2 = User.builder()
    .usernameKeyword("W_CORP")
    .usernameSuffix("ABC")  // Different case!
    .build();
userRepository.save(user2);

// H2 (case-insensitive): Duplicate! ❌ Exception
// MySQL utf8mb4_bin (case-sensitive): Different usernames ✅ Success

// If tests use H2, production behavior is OPPOSITE
```

#### Test Pattern:
```java
@Test
@Tag("containerized")
@DisplayName("MySQL collation: Case-sensitive username uniqueness")
void save_UsernamesWithDifferentCase_AllowedInBinary() {
    // WHY: Documents production collation behavior
    // H2 vs MySQL: Different case sensitivity

    // Arrange
    User user1 = User.builder()
        .email("user1@example.com")
        .usernameKeyword("W_CORP")
        .usernameSuffix("abc")
        .build();
    userRepository.save(user1);
    entityManager.flush();

    // Act: Same suffix, different case
    User user2 = User.builder()
        .email("user2@example.com")
        .usernameKeyword("W_CORP")
        .usernameSuffix("ABC")  // Uppercase
        .build();

    // Assert: MySQL utf8mb4_bin allows different case
    assertDoesNotThrow(() -> {
        userRepository.save(user2);
        entityManager.flush();
    }, "Case-sensitive collation should allow different case");

    // LESSON: If production uses case-insensitive collation,
    // this test would fail (duplicate)
    // Check your schema: COLLATE utf8mb4_bin vs utf8mb4_general_ci
}
```

---

### 4. Constraint Error Messages

| Database | Error Format | Example |
|----------|-------------|---------|
| **H2** | Generic | "Unique index or primary key violation" |
| **MySQL** | Detailed | "Duplicate entry 'W_CORP-abc' for key 'users.unique_username'" |

**Why it matters:**
```java
// Application parses error messages for user-friendly feedback
try {
    userRepository.save(duplicateUser);
} catch (DataIntegrityViolationException e) {
    // Extract constraint name from message
    if (e.getMessage().contains("unique_username")) {
        throw new DuplicateUsernameException("Username already taken");
    }
}

// H2: Generic message → constraint name not extracted → wrong error shown
// MySQL: Detailed message → constraint name extracted → correct error shown
```

---

## Using TestContainers for Production Testing

### Why TestContainers?

**TestContainers:** Docker-based testing with real databases

**Benefits:**
- ✅ Production-identical database (MySQL, PostgreSQL, etc.)
- ✅ Catches dialect-specific bugs before deployment
- ✅ Tests real locking, precision, collation behavior
- ✅ Automated setup/teardown (no manual DB management)

**Tradeoffs:**
- ❌ Slower than H2 (+10-15 seconds for Docker startup)
- ❌ Requires Docker daemon running
- ❌ More resource-intensive (memory, disk)

**Strategy:** Use both
- H2 for fast unit/repository tests (90% of tests)
- TestContainers for critical integration tests (10% of tests)

### Setup TestContainers MySQL

**1. Add dependencies to pom.xml:**
```xml
<dependency>
    <groupId>org.testcontainers</groupId>
    <artifactId>testcontainers</artifactId>
    <version>1.19.3</version>
    <scope>test</scope>
</dependency>
<dependency>
    <groupId>org.testcontainers</groupId>
    <artifactId>mysql</artifactId>
    <version>1.19.3</version>
    <scope>test</scope>
</dependency>
<dependency>
    <groupId>org.testcontainers</groupId>
    <artifactId>junit-jupiter</artifactId>
    <version>1.19.3</version>
    <scope>test</scope>
</dependency>
```

**2. Create application-it.properties:**
```properties
# TestContainers will override datasource URL dynamically
spring.datasource.url=jdbc:tc:mysql:8.0:///testdb
spring.datasource.driver-class-name=org.testcontainers.jdbc.ContainerDatabaseDriver

# Use production dialect
spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.MySQLDialect

# Validate schema (don't auto-create)
spring.jpa.hibernate.ddl-auto=validate

# Show SQL for debugging
logging.level.org.hibernate.SQL=DEBUG
```

**3. Create base test class:**
```java
@SpringBootTest
@ActiveProfiles("it")  // Use application-it.properties
@Testcontainers
@Tag("containerized")  // Separate CI job
public abstract class MySQLIntegrationTestBase {

    @Container
    static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.0")
            .withDatabaseName("testdb")
            .withUsername("test")
            .withPassword("test")
            .withCommand(
                "--character-set-server=utf8mb4",
                "--collation-server=utf8mb4_bin"  // Case-sensitive
            );

    @DynamicPropertySource
    static void setProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", mysql::getJdbcUrl);
        registry.add("spring.datasource.username", mysql::getUsername);
        registry.add("spring.datasource.password", mysql::getPassword);
    }
}
```

**4. Write MySQL-specific tests:**
```java
class VotingAtomicityMySQLTest extends MySQLIntegrationTestBase {

    @Autowired private PlannerRepository plannerRepository;
    @Autowired private EntityManager entityManager;

    @Test
    @DisplayName("MySQL: Atomic increment handles 50 concurrent votes")
    void atomicIncrement_50ConcurrentVotes_AllCounted() throws Exception {
        // WHY: Validates atomic operations work with MySQL's optimistic locking
        // PRODUCTION-IDENTICAL: Uses same database as production

        // Arrange
        Planner planner = createTestPlanner();
        planner.setUpvotes(0);
        plannerRepository.save(planner);
        entityManager.flush();
        entityManager.clear();

        UUID plannerId = planner.getId();

        // Act: 50 concurrent votes (realistic load)
        ExecutorService executor = Executors.newFixedThreadPool(50);
        CountDownLatch startGate = new CountDownLatch(1);
        CountDownLatch endGate = new CountDownLatch(50);

        for (int i = 0; i < 50; i++) {
            executor.submit(() -> {
                try {
                    startGate.await();
                    plannerRepository.incrementUpvotes(plannerId);
                    endGate.countDown();
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            });
        }

        startGate.countDown();
        endGate.await(30, TimeUnit.SECONDS);
        executor.shutdown();

        // Assert: All 50 votes counted
        entityManager.clear();
        Planner updated = plannerRepository.findById(plannerId).orElseThrow();
        assertEquals(50, updated.getUpvotes(),
            "MySQL atomic increment should handle high concurrency");

        // LESSON: H2 might pass with less concurrency
        // MySQL stress test reveals real-world behavior
    }

    @Test
    @DisplayName("MySQL: UNIQUE constraint error message format")
    void save_DuplicateUsername_MySQLErrorFormat() {
        // WHY: Documents MySQL-specific error message format
        // APPLICATION: Error parsing logic must handle MySQL format

        // Arrange
        User user1 = createUser("user@example.com", "W_CORP", "abc");
        userRepository.save(user1);
        entityManager.flush();

        // Act: Duplicate username
        User user2 = createUser("other@example.com", "W_CORP", "abc");

        DataIntegrityViolationException ex = assertThrows(
            DataIntegrityViolationException.class,
            () -> {
                userRepository.save(user2);
                entityManager.flush();
            }
        );

        // Assert: MySQL error format
        String message = ex.getMessage();
        assertThat(message)
            .contains("Duplicate entry")
            .containsPattern("for key '.*unique_username'");

        // LESSON: Application error handling should parse this format
        // H2 message is different, won't test error parsing logic
    }
}
```

---

## When to Use TestContainers

**Use TestContainers for:**
- ✅ Atomic operations (concurrent voting, notifications)
- ✅ Race condition testing (real locking behavior)
- ✅ Date/time precision (timestamp ordering)
- ✅ Constraint validation (error message parsing)
- ✅ Performance testing (query optimization)
- ✅ Migration validation (schema changes)

**Keep H2 for:**
- ✅ Simple CRUD tests
- ✅ Query syntax validation
- ✅ Service layer tests (mocked repositories)
- ✅ Fast feedback loop (local development)

**Recommended split:**
- 90% H2 tests (fast, local)
- 10% TestContainers tests (slow, production-identical)

---

## CI/CD Integration

**Separate test jobs for speed:**

```yaml
# .github/workflows/test.yml
jobs:
  unit-tests:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v3
      - name: Run H2 tests
        run: mvn test -Dtest=!*MySQL*Test

  integration-tests:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v3
      - name: Run TestContainers tests
        run: mvn test -Dgroups=containerized

# Run unit tests on every push (fast)
# Run integration tests on PR only (slow but thorough)
```

**Tag pattern:**
```java
@Test
@Tag("containerized")  // Separate job
void mysqlSpecificTest() { ... }

// Run: mvn test -Dgroups=containerized
```

---

## Summary Checklist

When testing with production database:

**Locking Behavior:**
- [ ] Deadlock detection tested (opposite lock order)
- [ ] Optimistic locking tested (MySQL-specific)
- [ ] Lock timeout configuration validated
- [ ] High concurrency stress tests (50+ threads)

**Date/Time Precision:**
- [ ] Timestamp ordering tested (rapid creation)
- [ ] Microsecond precision preserved
- [ ] Date comparison queries tested
- [ ] Timestamp boundaries tested

**String Collation:**
- [ ] Case sensitivity tested (utf8mb4_bin)
- [ ] UNIQUE constraints tested with case variants
- [ ] Search queries tested (LIKE with case)
- [ ] Sorting behavior tested (ORDER BY)

**Constraint Errors:**
- [ ] Error message format documented
- [ ] Application error parsing tested
- [ ] Constraint names extractable
- [ ] User-friendly messages generated

**TestContainers Setup:**
- [ ] Dependencies added to pom.xml
- [ ] application-it.properties configured
- [ ] Base test class created
- [ ] @Tag("containerized") used consistently
- [ ] CI/CD jobs separated (fast/slow)

**Test Organization:**
- [ ] 90% H2 tests (fast feedback)
- [ ] 10% TestContainers tests (production validation)
- [ ] Critical paths tested with MySQL
- [ ] Local development uses H2 by default

**Next:** See `database-testing-05-entity-mapping.md` for testing JPA annotations and relationship configurations.
