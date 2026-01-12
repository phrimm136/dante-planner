# Database Testing: Transaction Boundaries & Concurrency

**Learning Goal:** Understand how to test transaction isolation, race conditions, lost updates, and concurrent access patterns to prevent data corruption bugs.

---

## Why Test Transaction Boundaries?

### The Fundamental Problem

**Service tests with @Transactional auto-rollback don't test real transaction behavior.**

```java
@Test
@Transactional  // Auto-rollback after test
void testVoting() {
    voteService.castVote(userId, plannerId, UP);

    // Test ends, transaction rolls back
    // ❌ Never tests: What if two users vote simultaneously?
    // ❌ Never tests: Is vote count update atomic?
    // ❌ Never tests: Does transaction isolation prevent dirty reads?
}
```

**Problems @Transactional hides:**
- **Lost updates:** Two concurrent modifications, one overwrites the other
- **Race conditions:** Vote count incremented by 1 instead of 2 (lost update)
- **Dirty reads:** Reading uncommitted data from another transaction
- **Phantom reads:** Query returns different rows mid-transaction
- **Deadlocks:** Two transactions wait for each other's locks

### The Business Impact

**Without transaction tests, these bugs reach production:**

1. **Data corruption:** Vote counts wrong (off by hundreds)
2. **Race condition bugs:** Notification sent twice or not at all
3. **Deadlocks:** Transactions timeout, users see 500 errors
4. **Lost updates:** User's action disappears (silently overwritten)

**Real example from your codebase:**

```java
// Before atomic increment (V017):
public void castVote(Long userId, UUID plannerId, VoteType type) {
    Planner planner = plannerRepository.findById(plannerId);
    planner.setUpvotes(planner.getUpvotes() + 1);  // ❌ Not atomic!
    plannerRepository.save(planner);
}

// Concurrent scenario:
// Thread 1: Read upvotes = 10
// Thread 2: Read upvotes = 10
// Thread 1: Set upvotes = 11, save
// Thread 2: Set upvotes = 11, save  (overwrites Thread 1!)
// Result: Upvotes = 11 (should be 12) - LOST UPDATE

// After atomic increment (V017):
@Modifying
@Query("UPDATE Planner p SET p.upvotes = p.upvotes + 1 WHERE p.id = :id")
int incrementUpvotes(@Param("id") UUID id);

// Now atomic at database level - no lost updates
```

---

## Transaction Testing Concerns

### 1. Lost Updates (Read-Modify-Write Race)

#### Why Lost Updates Matter

**Problem:** Concurrent modifications overwrite each other

```java
// Non-atomic update pattern
Planner planner = plannerRepository.findById(id);  // Read
planner.setUpvotes(planner.getUpvotes() + 1);      // Modify
plannerRepository.save(planner);                    // Write

// Timeline:
// T0: Thread 1 reads upvotes = 100
// T1: Thread 2 reads upvotes = 100
// T2: Thread 1 writes upvotes = 101
// T3: Thread 2 writes upvotes = 101  (overwrites Thread 1!)
// RESULT: 101 (expected 102)
```

**Production impact:**
- Vote counts drift from reality (off by hundreds)
- View counts inaccurate (analytics broken)
- Inventory counts wrong (overselling items)
- Balance calculations incorrect (financial loss)

#### Test Pattern for Lost Updates:
```java
@SpringBootTest
@ActiveProfiles("test")
class AtomicUpdateTest {

    @Autowired private PlannerRepository plannerRepository;
    @Autowired private EntityManager entityManager;

    @Test
    @DisplayName("Concurrent votes: Atomic increment prevents lost updates")
    void concurrentVotes_AtomicIncrement_NoLostUpdates() throws Exception {
        // WHY: Validates atomic operations work correctly under concurrency
        // PREVENTS: Lost update bug (vote count off by N)

        // Arrange: Create planner with 0 upvotes
        Planner planner = createTestPlanner();
        planner.setUpvotes(0);
        plannerRepository.save(planner);
        entityManager.flush();
        entityManager.clear();

        UUID plannerId = planner.getId();

        // Act: Simulate 10 concurrent votes
        ExecutorService executor = Executors.newFixedThreadPool(10);
        CountDownLatch startGate = new CountDownLatch(1);
        CountDownLatch endGate = new CountDownLatch(10);

        for (int i = 0; i < 10; i++) {
            executor.submit(() -> {
                try {
                    startGate.await(); // Wait for all threads ready

                    // Each thread increments vote count
                    plannerRepository.incrementUpvotes(plannerId);

                    endGate.countDown();
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            });
        }

        // Start all threads simultaneously
        startGate.countDown();

        // Wait for all to finish
        endGate.await(10, TimeUnit.SECONDS);
        executor.shutdown();

        // Assert: All 10 votes counted (no lost updates)
        entityManager.clear();
        Planner updated = plannerRepository.findById(plannerId).orElseThrow();
        assertEquals(10, updated.getUpvotes(),
            "Atomic increment should handle concurrent updates without loss");

        // LESSON: If incrementUpvotes() doesn't use atomic operation,
        // upvotes < 10 (lost updates occurred)
    }

    @Test
    @DisplayName("Non-atomic update: Demonstrates lost update problem")
    void concurrentUpdates_NonAtomic_LosesUpdates() throws Exception {
        // WHY: Documents the bug that atomic operations prevent
        // EDUCATIONAL: Shows what happens WITHOUT atomic operations

        // Arrange
        Planner planner = createTestPlanner();
        planner.setUpvotes(0);
        plannerRepository.save(planner);
        entityManager.flush();
        UUID plannerId = planner.getId();

        // Act: Non-atomic increment (read-modify-write)
        ExecutorService executor = Executors.newFixedThreadPool(5);
        CountDownLatch latch = new CountDownLatch(1);

        for (int i = 0; i < 5; i++) {
            executor.submit(() -> {
                try {
                    latch.await();

                    // NON-ATOMIC: Read, modify, write
                    Planner p = plannerRepository.findById(plannerId).orElseThrow();
                    p.setUpvotes(p.getUpvotes() + 1);  // Race condition here!
                    plannerRepository.save(p);

                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            });
        }

        latch.countDown();
        executor.shutdown();
        executor.awaitTermination(10, TimeUnit.SECONDS);

        // Assert: Lost updates occurred
        entityManager.clear();
        Planner updated = plannerRepository.findById(plannerId).orElseThrow();

        // Expect LESS than 5 due to lost updates
        assertThat(updated.getUpvotes())
            .as("Non-atomic updates should lose some increments")
            .isLessThan(5);

        // LESSON: This test DOCUMENTS the bug
        // In real code, use atomic operations to prevent this
    }
}
```

---

### 2. Notification Race Conditions

#### Why Notification Atomicity Matters

**Problem:** Multiple threads crossing threshold simultaneously

```java
// Business rule: Send notification when net votes reaches 10

// Thread 1: Reads net votes = 9, increments to 10, sends notification
// Thread 2: Reads net votes = 9, increments to 10, sends notification
// RESULT: TWO notifications sent (should be ONE)
```

**Your solution (V020):**
```java
// Atomic flag in database (one-time notification)
@Modifying
@Query("""
    UPDATE Planner p
    SET p.recommendedNotifiedAt = :notifiedAt
    WHERE p.id = :id
    AND p.recommendedNotifiedAt IS NULL
    """)
int trySetRecommendedNotified(
    @Param("id") UUID id,
    @Param("notifiedAt") Instant notifiedAt
);

// Returns 1 if update succeeded (flag was NULL)
// Returns 0 if already set (another thread won)
```

#### Test Pattern from Your Codebase:
```java
@SpringBootTest
@ActiveProfiles("test")
@Import(TestConfig.class)
@Transactional
class VoteNotificationFlowTest {

    @Autowired private PlannerService plannerService;
    @Autowired private NotificationRepository notificationRepository;
    @Autowired private PlannerRepository plannerRepository;

    @Value("${planner.recommended-threshold}")
    private int recommendedThreshold;

    @Test
    @DisplayName("IT2: Concurrent votes crossing threshold create single notification")
    void concurrentVotesCrossingThreshold_CreatesSingleNotification() throws Exception {
        // WHY: Prevents duplicate notifications from race condition
        // BUSINESS RULE: One notification per planner when threshold crossed

        // Arrange: Planner at threshold - 1
        User owner = createTestUser("owner@example.com");
        Planner planner = createTestPlanner(owner);
        planner.setUpvotes(recommendedThreshold - 1);  // 9 votes (threshold = 10)
        plannerRepository.save(planner);
        plannerRepository.flush();
        entityManager.clear();

        // Create 5 voters
        List<User> voters = IntStream.range(0, 5)
            .mapToObj(i -> createTestUser("voter" + i + "@example.com"))
            .collect(Collectors.toList());

        // Act: 5 concurrent votes (all cross threshold simultaneously)
        ExecutorService executor = Executors.newFixedThreadPool(5);
        CountDownLatch startGate = new CountDownLatch(1);
        List<Future<Exception>> results = new ArrayList<>();

        for (User voter : voters) {
            results.add(executor.submit(() -> {
                try {
                    startGate.await(); // Synchronize start

                    // Each thread crosses threshold (9 → 10+)
                    plannerService.castVote(voter.getId(), planner.getId(), VoteType.UP);

                    return null;
                } catch (Exception e) {
                    return e;
                }
            }));
        }

        // Start all threads simultaneously
        startGate.countDown();
        executor.shutdown();
        executor.awaitTermination(10, TimeUnit.SECONDS);

        // Check for exceptions
        for (Future<Exception> result : results) {
            Exception ex = result.get();
            if (ex != null && !(ex instanceof VoteAlreadyExistsException)) {
                throw ex;
            }
        }

        // Flush and clear to ensure database state visible
        entityManager.flush();
        entityManager.clear();

        // Assert: Only ONE notification created
        List<Notification> notifications = notificationRepository
            .findByUserIdAndDeletedAtIsNullOrderByCreatedAtDesc(
                owner.getId(),
                PageRequest.of(0, 10)
            )
            .getContent();

        assertEquals(1, notifications.size(),
            "Should create exactly one notification despite concurrent votes");
        assertEquals(NotificationType.PLANNER_RECOMMENDED, notifications.get(0).getNotificationType());

        // Verify atomic flag was set
        Planner updated = plannerRepository.findById(planner.getId()).orElseThrow();
        assertNotNull(updated.getRecommendedNotifiedAt(),
            "Atomic flag should be set");

        // LESSON: trySetRecommendedNotified() uses WHERE ... IS NULL
        // First thread sets flag, subsequent threads get 0 rows affected
    }
}
```

**Key insights from this test:**
- **CountDownLatch:** Synchronizes thread start (all threads cross threshold "at once")
- **ExecutorService:** Simulates realistic concurrency
- **Atomic flag check:** Verifies `recommendedNotifiedAt` set exactly once
- **Database-level atomicity:** Relies on `UPDATE ... WHERE ... IS NULL` (row-level lock)

---

### 3. Transaction Isolation Levels

#### Why Isolation Matters

**Problem:** Transactions can see each other's uncommitted changes

```java
// Isolation level: READ_UNCOMMITTED (dirty reads possible)
// Transaction 1: Updates planner votes = 10 (not committed)
// Transaction 2: Reads votes = 10, sends notification
// Transaction 1: Rolls back (error occurred)
// RESULT: Notification sent for update that never happened!
```

**Isolation levels (from least to most strict):**

| Level | Dirty Read | Non-Repeatable Read | Phantom Read |
|-------|-----------|-------------------|--------------|
| READ_UNCOMMITTED | ✓ Possible | ✓ Possible | ✓ Possible |
| READ_COMMITTED | ✗ Prevented | ✓ Possible | ✓ Possible |
| REPEATABLE_READ | ✗ Prevented | ✗ Prevented | ✓ Possible |
| SERIALIZABLE | ✗ Prevented | ✗ Prevented | ✗ Prevented |

**Spring default:** READ_COMMITTED (safe for most cases)

#### Test Pattern:
```java
@Test
@DisplayName("Isolation: Uncommitted changes not visible to other transactions")
void isolation_UncommittedChanges_NotVisible() throws Exception {
    // WHY: Validates READ_COMMITTED prevents dirty reads
    // PREVENTS: Acting on data that gets rolled back

    // Arrange
    Planner planner = createTestPlanner();
    planner.setUpvotes(0);
    plannerRepository.save(planner);
    entityManager.flush();
    UUID plannerId = planner.getId();

    CountDownLatch txStarted = new CountDownLatch(1);
    CountDownLatch readAttempted = new CountDownLatch(1);
    AtomicInteger readValue = new AtomicInteger(-1);

    // Thread 1: Start transaction, update, wait, then rollback
    Thread tx1 = new Thread(() -> {
        try {
            // New transaction
            transactionTemplate.execute(status -> {
                Planner p = plannerRepository.findById(plannerId).orElseThrow();
                p.setUpvotes(999);  // Uncommitted change
                plannerRepository.save(p);
                plannerRepository.flush();

                txStarted.countDown();  // Signal: I've made change

                try {
                    readAttempted.await(5, TimeUnit.SECONDS);  // Wait for read attempt
                    Thread.sleep(100);  // Give reader time
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }

                status.setRollbackOnly();  // Rollback (change never committed)
                return null;
            });
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    });

    // Thread 2: Read value after Thread 1 updates but before commit
    Thread tx2 = new Thread(() -> {
        try {
            txStarted.await();  // Wait for update
            Thread.sleep(50);   // Ensure update happened

            // New transaction
            transactionTemplate.execute(status -> {
                Planner p = plannerRepository.findById(plannerId).orElseThrow();
                readValue.set(p.getUpvotes());  // Should NOT see 999
                readAttempted.countDown();
                return null;
            });
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    });

    // Act
    tx1.start();
    tx2.start();
    tx1.join(10000);
    tx2.join(10000);

    // Assert: Thread 2 did NOT see uncommitted value
    assertEquals(0, readValue.get(),
        "READ_COMMITTED should prevent dirty reads");

    // LESSON: If isolation level is READ_UNCOMMITTED,
    // readValue would be 999 (dirty read occurred)
}
```

---

### 4. Deadlock Prevention

#### Why Deadlock Testing Matters

**Problem:** Two transactions wait for each other's locks

```java
// Transaction 1: Lock planner A → wait for planner B
// Transaction 2: Lock planner B → wait for planner A
// RESULT: Deadlock! Both wait forever (until timeout)
```

**How deadlocks happen:**
```java
// Thread 1: castVote(plannerA) → castVote(plannerB)
// Thread 2: castVote(plannerB) → castVote(plannerA)

// Timeline:
// T0: TX1 locks plannerA
// T1: TX2 locks plannerB
// T2: TX1 tries to lock plannerB (waits for TX2)
// T3: TX2 tries to lock plannerA (waits for TX1)
// DEADLOCK!
```

**Prevention strategies:**
- **Lock ordering:** Always acquire locks in consistent order (by ID)
- **Timeout:** Set transaction timeout to detect deadlocks
- **Retry logic:** Automatically retry on deadlock exception

#### Test Pattern:
```java
@Test
@DisplayName("Deadlock prevention: Lock ordering prevents deadlock")
void multipleUpdates_ConsistentOrdering_NoDeadlock() throws Exception {
    // WHY: Documents deadlock prevention strategy
    // STRATEGY: Always lock resources in ID order

    // Arrange: Two planners
    Planner plannerA = createTestPlanner();
    Planner plannerB = createTestPlanner();
    entityManager.flush();

    // Ensure consistent ordering (lock smaller ID first)
    UUID firstId = plannerA.getId().compareTo(plannerB.getId()) < 0
        ? plannerA.getId()
        : plannerB.getId();
    UUID secondId = plannerA.getId().compareTo(plannerB.getId()) < 0
        ? plannerB.getId()
        : plannerA.getId();

    // Act: Two transactions update both planners (same order)
    ExecutorService executor = Executors.newFixedThreadPool(2);
    CountDownLatch latch = new CountDownLatch(1);

    Future<?> tx1 = executor.submit(() -> {
        try {
            latch.await();
            transactionTemplate.execute(status -> {
                // Lock in consistent order
                plannerRepository.incrementUpvotes(firstId);
                plannerRepository.incrementUpvotes(secondId);
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
                // Same order as tx1 (no deadlock)
                plannerRepository.incrementUpvotes(firstId);
                plannerRepository.incrementUpvotes(secondId);
                return null;
            });
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    });

    latch.countDown();
    executor.shutdown();

    // Assert: Both transactions complete (no deadlock)
    assertDoesNotThrow(() -> {
        tx1.get(5, TimeUnit.SECONDS);
        tx2.get(5, TimeUnit.SECONDS);
    }, "Consistent lock ordering should prevent deadlock");

    // LESSON: If locks acquired in different order,
    // DeadlockLoserDataAccessException would be thrown
}
```

---

### 5. Optimistic vs Pessimistic Locking

#### Why Locking Strategy Matters

**Optimistic locking:** Assume no conflicts, check version at commit
```java
@Entity
public class Planner {
    @Version
    private Long version;  // Auto-incremented on update

    // Update:
    // 1. Read entity (version = 5)
    // 2. Modify fields
    // 3. Save: UPDATE ... WHERE id = ? AND version = 5
    // 4. If 0 rows affected → OptimisticLockException (someone else updated)
}
```

**Pessimistic locking:** Lock row immediately, prevent conflicts
```java
@Lock(LockModeType.PESSIMISTIC_WRITE)
@Query("SELECT p FROM Planner p WHERE p.id = :id")
Planner findByIdWithLock(@Param("id") UUID id);

// SELECT ... FOR UPDATE (row locked until transaction ends)
```

**When to use:**
- **Optimistic:** Low contention, rare conflicts (most use cases)
- **Pessimistic:** High contention, conflicts likely (bank accounts, inventory)

#### Test Pattern:
```java
@Test
@DisplayName("Optimistic locking: Version conflict detected")
void optimisticLocking_ConcurrentUpdate_ThrowsException() {
    // WHY: Validates @Version field prevents lost updates
    // STRATEGY: Optimistic locking for low-contention resources

    // Arrange
    Planner planner = createTestPlanner();
    plannerRepository.save(planner);
    entityManager.flush();
    entityManager.clear();

    // Act: Two transactions read same version, both try to update
    UUID plannerId = planner.getId();

    // TX1: Read planner (version = 0)
    Planner copy1 = plannerRepository.findById(plannerId).orElseThrow();
    assertEquals(0L, copy1.getVersion());

    // TX2: Read planner (version = 0)
    entityManager.clear();
    Planner copy2 = plannerRepository.findById(plannerId).orElseThrow();
    assertEquals(0L, copy2.getVersion());

    // TX1: Update and commit (version 0 → 1)
    copy1.setTitle("Updated by TX1");
    plannerRepository.save(copy1);
    entityManager.flush();

    // TX2: Try to update (expects version 0, but now version 1)
    copy2.setTitle("Updated by TX2");

    // Assert: Optimistic lock exception thrown
    assertThrows(OptimisticLockException.class, () -> {
        plannerRepository.save(copy2);
        entityManager.flush();
    });

    // LESSON: @Version prevents lost updates
    // Application should catch exception and retry with fresh data
}
```

---

## Summary Checklist

When testing transactions:

**Lost Updates:**
- [ ] Atomic operations tested with concurrent threads
- [ ] Read-modify-write patterns use atomic queries
- [ ] Vote counts, view counts accurate under concurrency
- [ ] Repository methods use `@Modifying` + atomic SQL

**Notification Race Conditions:**
- [ ] Single notification sent despite concurrent threshold crossing
- [ ] Atomic flag (`recommendedNotifiedAt`) set exactly once
- [ ] `trySetRecommendedNotified()` returns 0 or 1 correctly
- [ ] CountDownLatch used to simulate simultaneous actions

**Isolation:**
- [ ] Dirty reads prevented (uncommitted changes not visible)
- [ ] Non-repeatable reads understood (document expected behavior)
- [ ] Transaction isolation level explicitly set if needed
- [ ] Rollback behavior tested (uncommitted changes discarded)

**Deadlocks:**
- [ ] Lock ordering prevents circular waits
- [ ] Transaction timeout configured
- [ ] Deadlock exceptions handled gracefully
- [ ] Resource locking tested with multiple threads

**Locking Strategy:**
- [ ] Optimistic locking (`@Version`) tested for conflicts
- [ ] Pessimistic locking (`FOR UPDATE`) used where needed
- [ ] OptimisticLockException caught and retried
- [ ] Lock wait timeouts configured

**Testing Mechanics:**
- [ ] ExecutorService used for concurrent test threads
- [ ] CountDownLatch synchronizes thread start
- [ ] `entityManager.flush()` + `clear()` before assertions
- [ ] Timeouts prevent test hangs

**Next:** See `database-testing-04-dialect-differences.md` for testing H2 vs production database compatibility.
