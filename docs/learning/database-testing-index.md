# Database Testing: Complete Guide Index

**Purpose:** Comprehensive learning path for understanding database testing in Spring Boot applications with JPA/Hibernate.

---

## Why Database Testing Matters

**The Core Problem:** Service layer tests with mocked repositories verify business logic but skip the persistence boundary entirely.

```java
// Service test - all mocked
@Test
void castVote_IncrementCount() {
    when(repository.save(any())).thenReturn(mockPlanner);
    service.castVote(userId, plannerId, UP);
    verify(repository).save(any());  // ✅ Passes
}

// What this test DOESN'T catch:
// ❌ SQL syntax errors
// ❌ Constraint violations
// ❌ Race conditions (lost updates)
// ❌ N+1 query problems
// ❌ JPA mapping errors
// ❌ Dialect-specific bugs
```

**Database tests close this gap** by validating the persistence boundary where your object model meets the relational database.

---

## The Five Database Testing Concerns

### 1. [Constraint Enforcement](database-testing-01-constraint-enforcement.md)

**What it tests:** Database constraints actually prevent invalid data

**Topics covered:**
- Foreign key constraints (referential integrity)
- UNIQUE constraints (duplicate prevention)
- NOT NULL constraints (required fields)
- CHECK constraints (domain validation)
- When to use `entityManager.flush()`
- Why `@Transactional` auto-rollback hides bugs

**Key insight:** Your JPA entities might allow invalid data that the database rejects. Without constraint tests, schema changes that remove constraints go unnoticed until production breaks.

**Estimated reading time:** 30 minutes
**Practical exercises:** 15 test cases with code examples

---

### 2. [Query Correctness & N+1 Problems](database-testing-02-query-correctness.md)

**What it tests:** JPA/Hibernate generates the SQL you expect

**Topics covered:**
- @Query annotation syntax validation
- N+1 query detection (Hibernate Statistics)
- Parameter binding (@Param correctness)
- JOIN correctness (INNER vs LEFT, Cartesian products)
- @EntityGraph and JOIN FETCH for eager loading
- Query performance testing

**Key insight:** Service tests with mocks can't detect query typos, N+1 problems, or JOIN errors. These bugs only appear when real SQL executes against a database.

**Estimated reading time:** 35 minutes
**Practical exercises:** 20+ test patterns with performance monitoring

---

### 3. [Transaction Boundaries & Concurrency](database-testing-03-transaction-boundaries.md)

**What it tests:** Transaction isolation, race conditions, and concurrent access

**Topics covered:**
- Lost updates (read-modify-write races)
- Atomic operations (database-level atomicity)
- Notification race conditions (V020 pattern)
- Transaction isolation levels (READ_COMMITTED behavior)
- Deadlock prevention and detection
- Optimistic vs pessimistic locking

**Key insight:** @Transactional auto-rollback in tests hides concurrency bugs. Production-only race conditions cause data corruption (vote counts off by hundreds, notifications sent twice).

**Estimated reading time:** 40 minutes
**Practical exercises:** Your `VoteNotificationFlowTest` as reference, plus 10+ concurrency patterns

---

### 4. [H2 vs Production Dialect Differences](database-testing-04-dialect-differences.md)

**What it tests:** Your tests work on the production database, not just H2

**Topics covered:**
- Locking behavior differences (pessimistic vs optimistic)
- Date/time precision (milliseconds vs microseconds)
- String collation (case-sensitive vs case-insensitive)
- Constraint error message formats
- TestContainers setup and usage
- When to use H2 vs TestContainers (90/10 split)

**Key insight:** H2 is convenient but doesn't match MySQL/PostgreSQL behavior. Production-only bugs (deadlocks, timestamp ordering, collation) slip through H2 tests.

**Estimated reading time:** 35 minutes
**Practical exercises:** TestContainers setup guide + 8 dialect-specific tests

---

### 5. [Entity Mapping & JPA Configuration](database-testing-05-entity-mapping.md)

**What it tests:** JPA annotations correctly map objects to tables

**Topics covered:**
- Basic column mapping (@Column, @Enumerated)
- Relationship mapping (@OneToMany, @ManyToOne, mappedBy)
- Cascade operations (CascadeType.ALL, orphanRemoval)
- Lazy loading (LazyInitializationException)
- Composite keys (@IdClass, equals/hashCode)
- Bidirectional relationship synchronization

**Key insight:** Service tests can't detect mapping errors. Misconfigured cascades cause orphaned records, wrong mappedBy breaks relationships, and lazy loading crashes outside transactions.

**Estimated reading time:** 40 minutes
**Practical exercises:** 25+ entity mapping test patterns

---

## Learning Path

### For Beginners (New to Database Testing)

**Start here:**
1. Read [Constraint Enforcement](database-testing-01-constraint-enforcement.md) - Most concrete, immediate value
2. Read [Entity Mapping](database-testing-05-entity-mapping.md) - Validates your JPA configuration
3. Skim [Query Correctness](database-testing-02-query-correctness.md) - Focus on N+1 detection
4. Optional: [Transaction Boundaries](database-testing-03-transaction-boundaries.md) - Advanced topic
5. Optional: [Dialect Differences](database-testing-04-dialect-differences.md) - When you're ready for TestContainers

**Practice exercise:** Write constraint tests for your `PlannerVote` entity (1 hour)

---

### For Intermediate Developers (Familiar with JPA)

**Recommended order:**
1. [Transaction Boundaries](database-testing-03-transaction-boundaries.md) - Your `VoteNotificationFlowTest` is excellent reference
2. [Query Correctness](database-testing-02-query-correctness.md) - Add N+1 detection to existing tests
3. [Dialect Differences](database-testing-04-dialect-differences.md) - Set up TestContainers for critical tests
4. [Constraint Enforcement](database-testing-01-constraint-enforcement.md) - Fill gaps in repository tests
5. [Entity Mapping](database-testing-05-entity-mapping.md) - Validate lazy loading behavior

**Practice exercise:** Add Hibernate Statistics to detect N+1 queries (2 hours)

---

### For Your Current Project (LimbusPlanner)

Based on the architectural research findings, prioritize:

**High Priority (Week 1):**
1. Add constraint tests ([Guide 1](database-testing-01-constraint-enforcement.md))
   - `PlannerVote` UNIQUE constraint
   - `PlannerComment` FK constraints
   - `User` NOT NULL constraints
   - Estimated: 3-4 hours

2. Add N+1 query detection ([Guide 2](database-testing-02-query-correctness.md))
   - `findPublished` with user data
   - `getRecommended` with vote counts
   - Comment queries with author data
   - Estimated: 2-3 hours

**Medium Priority (Week 2):**
3. Set up TestContainers ([Guide 4](database-testing-04-dialect-differences.md))
   - MySQL container configuration
   - Tag critical tests with `@Tag("containerized")`
   - CI/CD job separation
   - Estimated: 4-6 hours

4. Test entity cascades ([Guide 5](database-testing-05-entity-mapping.md))
   - Planner → Comments cascade
   - User → Votes cascade
   - orphanRemoval behavior
   - Estimated: 2-3 hours

**Lower Priority (Week 3+):**
5. Concurrency stress tests ([Guide 3](database-testing-03-transaction-boundaries.md))
   - 50+ concurrent votes (TestContainers)
   - Multiple simultaneous threshold crossings
   - Deadlock detection
   - Estimated: 4-5 hours

---

## Quick Reference: When to Use Each Test Type

### Constraint Tests (Guide 1)
**Use when:**
- Adding/modifying database constraints
- Creating new entities with relationships
- Migrating schema (validate constraints applied)

**Example:** Testing that duplicate votes are rejected by UNIQUE constraint

---

### Query Tests (Guide 2)
**Use when:**
- Writing custom @Query methods
- Optimizing queries (JOIN FETCH, @EntityGraph)
- Suspecting N+1 problems

**Example:** Validating that `findPublished` doesn't trigger N+1 queries

---

### Transaction Tests (Guide 3)
**Use when:**
- Implementing atomic operations (voting, counters)
- Adding notification systems (threshold detection)
- Working with concurrent access patterns

**Example:** Your `VoteNotificationFlowTest` - testing race conditions

---

### Dialect Tests (Guide 4)
**Use when:**
- Using database-specific features (JSON columns, full-text search)
- Encountering production-only bugs (deadlocks, timestamp issues)
- Before major deployments (validate production behavior)

**Example:** Testing atomic operations with MySQL's locking behavior

---

### Entity Mapping Tests (Guide 5)
**Use when:**
- Adding new relationships (@OneToMany, @ManyToOne)
- Configuring cascade operations
- Fixing LazyInitializationException issues

**Example:** Testing that deleting planner cascades to comments

---

## Testing Strategy Summary

**The Three-Layer Approach:**

```
┌─────────────────────────────────────────────────────┐
│ Service Layer Tests (60% of tests)                   │
│ - Fast (milliseconds)                                │
│ - Mocked repositories                                │
│ - Business logic only                                │
│ - Run on every save                                  │
└─────────────────────────────────────────────────────┘
                      ↓
┌─────────────────────────────────────────────────────┐
│ Repository Layer Tests (30% of tests)                │
│ - Medium speed (seconds)                             │
│ - H2 in-memory database                              │
│ - Query correctness, constraints                     │
│ - Run on every commit                                │
└─────────────────────────────────────────────────────┘
                      ↓
┌─────────────────────────────────────────────────────┐
│ Integration Tests (10% of tests)                     │
│ - Slow (10-30 seconds)                               │
│ - TestContainers MySQL                               │
│ - Concurrency, dialect-specific bugs                 │
│ - Run on PR / before deploy                          │
└─────────────────────────────────────────────────────┘
```

**Key principle:** Each layer catches different bug classes. Don't skip layers.

---

## Common Anti-Patterns to Avoid

### ❌ Anti-Pattern 1: Testing Without flush()
```java
@Test
void testConstraint() {
    repository.save(invalidEntity);
    // Test ends, @Transactional rolls back
    // SQL never executed, constraint not checked! ❌
}
```

**Fix:** Always `entityManager.flush()` to force SQL execution

---

### ❌ Anti-Pattern 2: Only Testing with H2
```java
// All tests use H2
// Production uses MySQL
// Deadlocks only occur in production ❌
```

**Fix:** Use TestContainers for critical paths (10% of tests)

---

### ❌ Anti-Pattern 3: Ignoring N+1 Queries
```java
@Test
void testFindAll() {
    List<Planner> result = repository.findAll();
    assertEquals(100, result.size());  // ✅ Passes
    // But: 101 queries executed (N+1 problem) ❌
}
```

**Fix:** Monitor query count with Hibernate Statistics

---

### ❌ Anti-Pattern 4: Not Testing Concurrency
```java
@Test
void testVoting() {
    service.castVote(user1, planner, UP);
    // But: What if two users vote simultaneously? ❌
}
```

**Fix:** Use ExecutorService + CountDownLatch for concurrent tests

---

### ❌ Anti-Pattern 5: Assuming Cascade Behavior
```java
// Deletes planner
// Assumes comments are deleted too
// But: No cascade configured! Orphaned comments ❌
```

**Fix:** Explicitly test cascade operations (delete, persist, orphan removal)

---

## Tools and Utilities

### Hibernate Statistics (N+1 Detection)
```java
SessionFactory sf = entityManager.getEntityManagerFactory()
    .unwrap(SessionFactory.class);
Statistics stats = sf.getStatistics();
stats.clear();
stats.setStatisticsEnabled(true);

// Execute query
List<Planner> result = repository.findAll();

// Check query count
long queryCount = stats.getPrepareStatementCount();
assertThat(queryCount).isLessThanOrEqualTo(2);
```

### TestContainers MySQL
```java
@Container
static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.0")
    .withDatabaseName("testdb")
    .withUsername("test")
    .withPassword("test");
```

### Concurrent Test Pattern
```java
ExecutorService executor = Executors.newFixedThreadPool(10);
CountDownLatch startGate = new CountDownLatch(1);

// Submit tasks...

startGate.countDown();  // Start all threads simultaneously
executor.shutdown();
executor.awaitTermination(10, TimeUnit.SECONDS);
```

---

## Next Steps

1. **Start with constraints:** Read [Guide 1](database-testing-01-constraint-enforcement.md) and add 5 constraint tests to your project
2. **Detect N+1 queries:** Read [Guide 2](database-testing-02-query-correctness.md) and add Hibernate Statistics to one repository test
3. **Study your VoteNotificationFlowTest:** It's an excellent example of transaction boundary testing ([Guide 3](database-testing-03-transaction-boundaries.md))
4. **Plan TestContainers adoption:** Read [Guide 4](database-testing-04-dialect-differences.md) and identify 3 critical tests to run with MySQL
5. **Validate entity mappings:** Read [Guide 5](database-testing-05-entity-mapping.md) and test your cascade configurations

**Remember:** Database tests are an investment. They catch production bugs before deployment, prevent data corruption, and document persistence behavior for future developers.

---

## Glossary

| Term | Definition | Guide Reference |
|------|------------|-----------------|
| **N+1 Query** | Loading collection triggers N separate queries (performance bug) | [Guide 2](database-testing-02-query-correctness.md) |
| **Lost Update** | Concurrent modifications overwrite each other (race condition) | [Guide 3](database-testing-03-transaction-boundaries.md) |
| **LazyInitializationException** | Accessing lazy collection outside transaction | [Guide 5](database-testing-05-entity-mapping.md) |
| **Orphaned Record** | Child exists but parent deleted (wrong cascade) | [Guides 1](database-testing-01-constraint-enforcement.md), [5](database-testing-05-entity-mapping.md) |
| **TestContainers** | Docker-based testing with production database | [Guide 4](database-testing-04-dialect-differences.md) |
| **Optimistic Locking** | Check version at commit (assume no conflicts) | [Guide 3](database-testing-03-transaction-boundaries.md) |
| **Pessimistic Locking** | Lock row immediately (prevent conflicts) | [Guide 3](database-testing-03-transaction-boundaries.md) |
| **Cascade** | Parent operation affects children (persist, delete) | [Guide 5](database-testing-05-entity-mapping.md) |
| **entityManager.flush()** | Force SQL execution (required for constraint testing) | [All guides] |
| **Dialect** | Database-specific SQL variations (H2 vs MySQL) | [Guide 4](database-testing-04-dialect-differences.md) |

---

**Total reading time:** ~3 hours
**Total exercises:** 75+ practical test patterns
**Difficulty:** Beginner → Advanced
**Prerequisites:** Basic JPA/Hibernate knowledge, Spring Boot familiarity
