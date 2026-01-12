# Database Testing: Query Correctness & N+1 Problems

**Learning Goal:** Understand how to test that JPA/Hibernate generates the SQL you expect, and catch performance problems like N+1 queries before they reach production.

---

## Why Test Query Correctness?

### The Fundamental Problem

**Service layer tests with mocked repositories don't execute real queries.**

```java
// Service test with mock
@Test
void findPublishedPlanners_ReturnsPage() {
    when(plannerRepository.findByPublishedTrue(any()))
        .thenReturn(mockPage);

    Page<Planner> result = plannerService.getPublishedPlanners(pageable);

    assertNotNull(result);  // ✅ Test passes
}
```

This test passes even if:
- ❌ The `@Query` annotation has a typo (`Planer` instead of `Planner`)
- ❌ The query references a non-existent column
- ❌ The JOIN syntax is invalid for your database
- ❌ The query has an N+1 problem (loads 1 planner, then 100 separate queries for comments)
- ❌ The parameter name doesn't match (`@Param("id")` vs `:userId`)

**Why? No SQL is generated or executed.**

### The Business Impact

**Without query tests, these bugs reach production:**

1. **500 errors:** Invalid SQL crashes at runtime
2. **Performance degradation:** N+1 queries make pages load for 10+ seconds
3. **Incorrect results:** Wrong JOIN type returns too many/few rows
4. **Subtle bugs:** Query works in H2, fails in MySQL (dialect differences)

**Real example from your codebase:**

```java
// Custom query for finding recommended planners
@Query("""
    SELECT p FROM Planner p
    WHERE p.published = true
    AND p.upvotes - p.downvotes >= :threshold
    AND p.hiddenFromRecommended = false
    ORDER BY p.upvotes - p.downvotes DESC, p.createdAt DESC
    """)
Page<Planner> findRecommendedPlanners(
    @Param("threshold") int threshold,
    Pageable pageable
);

// WITHOUT query test:
// - Typo in field name → Runtime error when queried
// - Wrong parameter name → Exception at startup (lucky!)
// - Complex WHERE clause never validated

// WITH query test:
// - Query executes against real database
// - Results verified for correctness
// - Performance measured (query time, row count)
```

---

## Query Testing Concerns

### 1. @Query Annotation Correctness

#### Why @Query Tests Matter

**Problem:** JPQL/HQL syntax errors compile but fail at runtime

```java
// Typo in entity name (Planer vs Planner)
@Query("SELECT p FROM Planer p WHERE p.id = :id")
Planner findByIdCustom(@Param("id") UUID id);

// Service test with mock: ✅ Passes
// Real query test: ❌ Fails with "Planer is not mapped"
```

**What to test:**
- Query executes without exceptions
- Query returns expected number of rows
- Query filters correctly (WHERE clauses work)
- Query sorts correctly (ORDER BY works)
- Query paginates correctly (LIMIT/OFFSET)

#### Test Pattern:
```java
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class PlannerRepositoryQueryTest {

    @Autowired private PlannerRepository plannerRepository;
    @Autowired private EntityManager entityManager;

    @Nested
    @DisplayName("findRecommendedPlanners Query")
    class RecommendedPlannersTests {

        @Test
        @DisplayName("Query executes without errors")
        void findRecommended_ValidQuery_Executes() {
            // WHY: Validates @Query syntax is correct
            // CATCHES: Typos, invalid JPQL, missing parameters

            // Arrange
            createTestPlanner(upvotes = 10, published = true);

            // Act: Query should execute without throwing
            Page<Planner> result = plannerRepository.findRecommendedPlanners(
                5,  // threshold
                PageRequest.of(0, 20)
            );

            // Assert: Query executed successfully
            assertNotNull(result);
        }

        @Test
        @DisplayName("Filters by threshold correctly")
        void findRecommended_ThresholdFilter_ReturnsAboveOnly() {
            // WHY: Validates WHERE clause logic
            // BUSINESS RULE: Only planners with net votes >= threshold

            // Arrange
            Planner below = createPlanner(upvotes = 8, downvotes = 0);  // 8 net
            Planner at = createPlanner(upvotes = 10, downvotes = 0);    // 10 net
            Planner above = createPlanner(upvotes = 15, downvotes = 0); // 15 net
            entityManager.flush();

            // Act
            Page<Planner> result = plannerRepository.findRecommendedPlanners(
                10,  // threshold = 10
                PageRequest.of(0, 20)
            );

            // Assert: Only >= threshold returned
            assertEquals(2, result.getTotalElements());
            assertThat(result.getContent())
                .extracting(Planner::getId)
                .containsExactlyInAnyOrder(at.getId(), above.getId())
                .doesNotContain(below.getId());

            // LESSON: If WHERE clause is wrong (e.g., > instead of >=),
            // this test catches it
        }

        @Test
        @DisplayName("Excludes hidden planners")
        void findRecommended_HiddenFlag_Excludes() {
            // WHY: Validates moderation filter works
            // BUSINESS RULE: Hidden planners don't appear in recommended

            // Arrange
            Planner visible = createPlanner(hidden = false, upvotes = 15);
            Planner hidden = createPlanner(hidden = true, upvotes = 20); // Higher votes!
            entityManager.flush();

            // Act
            Page<Planner> result = plannerRepository.findRecommendedPlanners(10, pageable);

            // Assert: Hidden planner excluded despite high votes
            assertEquals(1, result.getTotalElements());
            assertEquals(visible.getId(), result.getContent().get(0).getId());
        }

        @Test
        @DisplayName("Sorts by net votes DESC, then createdAt DESC")
        void findRecommended_Sorting_CorrectOrder() {
            // WHY: Validates ORDER BY clause
            // BUSINESS RULE: Best planners (most votes) first, ties broken by date

            // Arrange
            Instant now = Instant.now();
            Planner newest = createPlanner(upvotes = 10, createdAt = now);
            Planner oldest = createPlanner(upvotes = 10, createdAt = now.minus(1, DAYS));
            Planner topVoted = createPlanner(upvotes = 20, createdAt = now.minus(2, DAYS));
            entityManager.flush();

            // Act
            List<Planner> result = plannerRepository.findRecommendedPlanners(5, pageable)
                .getContent();

            // Assert: Order is topVoted, newest, oldest
            assertEquals(3, result.size());
            assertEquals(topVoted.getId(), result.get(0).getId());   // Highest votes first
            assertEquals(newest.getId(), result.get(1).getId());     // Tie: newest first
            assertEquals(oldest.getId(), result.get(2).getId());     // Tie: oldest last

            // LESSON: If ORDER BY is wrong, this test fails
            // Common bug: Forgot DESC, query returns oldest first
        }
    }
}
```

---

### 2. N+1 Query Problems

#### Why N+1 Matters

**Problem:** Loading collection triggers separate query for each item

```java
// Load 100 planners
List<Planner> planners = plannerRepository.findAll();

// Frontend displays comments for each planner
for (Planner planner : planners) {
    List<Comment> comments = planner.getComments();  // Lazy load!
    // Each access = 1 query
}

// Result: 1 query for planners + 100 queries for comments = 101 total!
// Page load: 10+ seconds
```

**Production impact:**
- **Slow page loads:** 100+ queries take seconds, not milliseconds
- **Database overload:** Connection pool exhausted
- **Cascading failures:** Timeouts trigger retries, making it worse
- **Poor user experience:** "Is the app broken?" → churn

**Real example:**
```java
// Controller endpoint
@GetMapping("/api/planner/published")
public Page<PublicPlannerResponse> getPublished(Pageable pageable) {
    Page<Planner> planners = plannerService.getPublished(pageable);

    // Map to DTO
    return planners.map(planner -> PublicPlannerResponse.builder()
        .id(planner.getId())
        .title(planner.getTitle())
        .authorName(planner.getUser().getUsername())  // N+1 HERE!
        .commentCount(planner.getComments().size())   // ANOTHER N+1!
        .build());
}

// Query count:
// - 1 query for planners (page of 20)
// - 20 queries for users (each planner.getUser())
// - 20 queries for comments (each planner.getComments())
// Total: 41 queries for one page!
```

#### How to Detect N+1:

**Method 1: Hibernate Statistics**
```java
@Test
void findAll_WithComments_NoN+1() {
    // Enable Hibernate statistics
    SessionFactory sessionFactory = entityManager.getEntityManagerFactory()
        .unwrap(SessionFactory.class);
    Statistics stats = sessionFactory.getStatistics();
    stats.clear();
    stats.setStatisticsEnabled(true);

    // Create test data
    createPlannerWithComments(100); // 100 planners, each with 5 comments

    // Act: Load planners with comments
    List<Planner> planners = plannerRepository.findAllWithComments();

    // Assert: Should be 1-2 queries (not 101)
    long queryCount = stats.getPrepareStatementCount();
    assertThat(queryCount)
        .as("Query count should be minimal (no N+1)")
        .isLessThanOrEqualTo(2);

    // Access comments (should not trigger queries)
    for (Planner planner : planners) {
        planner.getComments().size(); // Should not query
    }

    // Verify no additional queries executed
    assertEquals(queryCount, stats.getPrepareStatementCount());
}
```

**Method 2: Manual Query Logging**
```java
@Test
void findAll_LogsQueryCount() {
    // application-test.properties:
    // logging.level.org.hibernate.SQL=DEBUG
    // logging.level.org.hibernate.type.descriptor.sql.BasicBinder=TRACE

    List<Planner> planners = plannerRepository.findAll();

    // Manually inspect logs - should see 1 query
    // If you see multiple SELECT statements → N+1 problem

    for (Planner planner : planners) {
        planner.getComments().size();
        // Check logs - should NOT see additional queries
    }
}
```

#### How to Fix N+1:

**Solution 1: @EntityGraph**
```java
@EntityGraph(attributePaths = {"user", "comments"})
@Query("SELECT p FROM Planner p WHERE p.published = true")
List<Planner> findAllPublishedWithDetails();

// Generates single query with LEFT JOIN
```

**Solution 2: JOIN FETCH**
```java
@Query("""
    SELECT DISTINCT p FROM Planner p
    LEFT JOIN FETCH p.user
    LEFT JOIN FETCH p.comments
    WHERE p.published = true
    """)
List<Planner> findAllPublishedWithDetails();

// Note: DISTINCT required to avoid duplicate planners when fetching collections
```

#### Test Pattern for N+1 Prevention:
```java
@Test
@DisplayName("findAllWithComments: No N+1 query problem")
void findAllWithComments_EagerLoading_SingleQuery() {
    // WHY: Prevents performance regression (N+1 queries)
    // PERFORMANCE REQUIREMENT: Load 100 planners in 1-2 queries

    // Arrange: Create 100 planners with comments
    for (int i = 0; i < 100; i++) {
        Planner planner = createPlanner();
        createComment(planner, "Comment 1");
        createComment(planner, "Comment 2");
        createComment(planner, "Comment 3");
    }
    entityManager.flush();
    entityManager.clear();

    // Act: Load with statistics tracking
    SessionFactory sf = entityManager.getEntityManagerFactory()
        .unwrap(SessionFactory.class);
    Statistics stats = sf.getStatistics();
    stats.clear();
    stats.setStatisticsEnabled(true);

    List<Planner> planners = plannerRepository.findAllWithComments();

    // Assert: Query count should be minimal (1-2)
    long queryCount = stats.getPrepareStatementCount();
    assertThat(queryCount)
        .as("Should use JOIN FETCH or @EntityGraph to avoid N+1")
        .isLessThanOrEqualTo(2);  // 1 for planners+comments, maybe 1 for users

    // Verify collections are loaded
    for (Planner planner : planners) {
        assertFalse(planner.getComments().isEmpty());
    }

    // Verify no additional queries when accessing collections
    long queriesAfter = stats.getPrepareStatementCount();
    assertEquals(queryCount, queriesAfter, "Accessing comments should not trigger queries");
}
```

---

### 3. Parameter Binding

#### Why Parameter Binding Matters

**Problem:** @Param name mismatch causes runtime error

```java
@Query("SELECT p FROM Planner p WHERE p.user.id = :userId")
Planner findByUserId(@Param("id") Long userId);  // WRONG: :userId != id
//                            ^^^ mismatch!

// Service test with mock: ✅ Passes
// Real query: ❌ QueryException: "Parameter :userId not bound"
```

**What to test:**
- All @Param names match query placeholders
- Named parameters (`:name`) vs positional (`?1`)
- Multiple parameters bound correctly
- NULL parameters handled correctly

#### Test Pattern:
```java
@Test
@DisplayName("Parameter binding: All parameters bound correctly")
void findByUserAndType_ParameterBinding_Works() {
    // WHY: Validates @Param names match query placeholders
    // CATCHES: Typos, missing parameters, wrong order

    // Arrange
    User user = createTestUser();
    Planner md = createPlanner(user, PlannerType.MIRROR_DUNGEON);
    Planner rr = createPlanner(user, PlannerType.REFRACTED_RAILWAY);
    entityManager.flush();

    // Act: Query with multiple parameters
    List<Planner> result = plannerRepository.findByUserAndType(
        user.getId(),                      // @Param("userId")
        PlannerType.MIRROR_DUNGEON        // @Param("plannerType")
    );

    // Assert: Query executed and filtered correctly
    assertEquals(1, result.size());
    assertEquals(md.getId(), result.get(0).getId());

    // LESSON: If @Param("userId") doesn't match :userId in query,
    // exception is thrown here
}

@Test
@DisplayName("NULL parameter: Handled correctly")
void findByOptionalFilter_NullParameter_ReturnsAll() {
    // WHY: Documents NULL parameter behavior
    // COMMON BUG: NULL in WHERE clause has special SQL semantics

    // Arrange
    createPlanner(title = "Test 1");
    createPlanner(title = "Test 2");
    entityManager.flush();

    // Act: Query with NULL (should match all)
    List<Planner> result = plannerRepository.findByTitleContaining(null);

    // Assert: NULL parameter returns all results
    // (Depends on query implementation - test documents expected behavior)
    assertEquals(2, result.size());

    // LESSON: SQL WHERE title LIKE NULL returns no rows (NULL != NULL)
    // Query must handle: WHERE (:title IS NULL OR title LIKE :title)
}
```

---

### 4. Join Correctness

#### Why Join Testing Matters

**Problem:** Wrong JOIN type returns incorrect results

```java
// INNER JOIN: Only planners with comments
@Query("SELECT p FROM Planner p JOIN p.comments c")
List<Planner> findAllWithComments();

// LEFT JOIN: All planners (even without comments)
@Query("SELECT p FROM Planner p LEFT JOIN p.comments c")
List<Planner> findAllWithOptionalComments();

// Service test: Can't tell the difference (mocked)
// Real query test: Verifies correct JOIN type used
```

**What to test:**
- INNER JOIN excludes rows without matches
- LEFT JOIN includes rows without matches
- JOIN conditions correct (ON clause)
- Cartesian products avoided (missing ON clause)

#### Test Pattern:
```java
@Test
@DisplayName("LEFT JOIN: Returns planners without comments")
void findAll_LeftJoin_IncludesEmpty() {
    // WHY: Documents JOIN type behavior
    // BUSINESS RULE: Show all planners (even if no comments)

    // Arrange
    Planner withComments = createPlanner();
    createComment(withComments, "Comment 1");

    Planner withoutComments = createPlanner();
    // No comments for this one

    entityManager.flush();

    // Act
    List<Planner> result = plannerRepository.findAllWithOptionalComments();

    // Assert: Both planners returned
    assertEquals(2, result.size());

    // LESSON: If query uses INNER JOIN instead of LEFT JOIN,
    // only withComments is returned (test fails)
}

@Test
@DisplayName("INNER JOIN: Excludes planners without comments")
void findWithComments_InnerJoin_RequiresComments() {
    // WHY: Validates INNER JOIN excludes empty collections
    // USE CASE: "Show only planners that have been commented on"

    // Arrange
    Planner with = createPlannerWithComments(3);
    Planner without = createPlanner(); // No comments
    entityManager.flush();

    // Act
    List<Planner> result = plannerRepository.findOnlyWithComments();

    // Assert: Only planner with comments returned
    assertEquals(1, result.size());
    assertEquals(with.getId(), result.get(0).getId());
}
```

---

## Real-World Example: Published Planners Query

**Complete test class for a complex query:**

```java
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class PublishedPlannersQueryTest {

    @Autowired private PlannerRepository plannerRepository;
    @Autowired private EntityManager entityManager;

    // Query being tested:
    // @Query("""
    //     SELECT p FROM Planner p
    //     LEFT JOIN FETCH p.user u
    //     WHERE p.published = true
    //     AND p.deletedAt IS NULL
    //     ORDER BY p.createdAt DESC
    //     """)
    // Page<Planner> findPublished(Pageable pageable);

    @Test
    @DisplayName("Query syntax: Executes without errors")
    void findPublished_ValidQuery_Executes() {
        createPlanner(published = true);

        Page<Planner> result = plannerRepository.findPublished(
            PageRequest.of(0, 20)
        );

        assertNotNull(result);
    }

    @Test
    @DisplayName("WHERE clause: Filters unpublished planners")
    void findPublished_OnlyPublished_Returned() {
        Planner published = createPlanner(published = true);
        Planner unpublished = createPlanner(published = false);
        entityManager.flush();

        Page<Planner> result = plannerRepository.findPublished(pageable);

        assertEquals(1, result.getTotalElements());
        assertEquals(published.getId(), result.getContent().get(0).getId());
    }

    @Test
    @DisplayName("WHERE clause: Excludes soft-deleted planners")
    void findPublished_ExcludesDeleted_Returned() {
        Planner active = createPlanner(published = true, deletedAt = null);
        Planner deleted = createPlanner(published = true, deletedAt = Instant.now());
        entityManager.flush();

        Page<Planner> result = plannerRepository.findPublished(pageable);

        assertEquals(1, result.getTotalElements());
        assertEquals(active.getId(), result.getContent().get(0).getId());
    }

    @Test
    @DisplayName("ORDER BY: Sorts by createdAt DESC")
    void findPublished_OrderByCreated_NewestFirst() {
        Instant now = Instant.now();
        Planner oldest = createPlanner(createdAt = now.minus(2, DAYS));
        Planner middle = createPlanner(createdAt = now.minus(1, DAYS));
        Planner newest = createPlanner(createdAt = now);
        entityManager.flush();

        List<Planner> result = plannerRepository.findPublished(pageable)
            .getContent();

        // Assert: Newest first
        assertEquals(3, result.size());
        assertEquals(newest.getId(), result.get(0).getId());
        assertEquals(middle.getId(), result.get(1).getId());
        assertEquals(oldest.getId(), result.get(2).getId());
    }

    @Test
    @DisplayName("LEFT JOIN FETCH: No N+1 for user")
    void findPublished_FetchesUser_NoN+1() {
        // Create 50 planners with different users
        for (int i = 0; i < 50; i++) {
            User user = createUser("user" + i + "@example.com");
            createPlanner(user, published = true);
        }
        entityManager.flush();
        entityManager.clear();

        // Enable statistics
        Statistics stats = getHibernateStatistics();
        stats.clear();
        stats.setStatisticsEnabled(true);

        // Act: Load published planners
        Page<Planner> result = plannerRepository.findPublished(
            PageRequest.of(0, 50)
        );

        // Assert: 1 query (with JOIN FETCH user)
        long queryCount = stats.getPrepareStatementCount();
        assertEquals(1, queryCount, "Should use LEFT JOIN FETCH to avoid N+1");

        // Verify users are loaded
        for (Planner planner : result.getContent()) {
            assertNotNull(planner.getUser());
            planner.getUser().getEmail(); // Should not trigger query
        }

        // No additional queries after accessing users
        assertEquals(queryCount, stats.getPrepareStatementCount());
    }

    @Test
    @DisplayName("Pagination: Returns correct page")
    void findPublished_Pagination_CorrectPage() {
        // Create 25 planners
        for (int i = 0; i < 25; i++) {
            createPlanner(title = "Planner " + i);
        }
        entityManager.flush();

        // Act: Request page 1 (second page), size 10
        Page<Planner> page1 = plannerRepository.findPublished(
            PageRequest.of(1, 10)
        );

        // Assert: Page metadata correct
        assertEquals(25, page1.getTotalElements());
        assertEquals(3, page1.getTotalPages());  // 25 / 10 = 3 pages
        assertEquals(1, page1.getNumber());      // Current page
        assertEquals(10, page1.getSize());       // Page size
        assertEquals(10, page1.getContent().size()); // Items on this page

        // Page 2 (third page) should have 5 items
        Page<Planner> page2 = plannerRepository.findPublished(
            PageRequest.of(2, 10)
        );
        assertEquals(5, page2.getContent().size());
    }
}
```

---

## Summary Checklist

When testing queries:

**Query Syntax:**
- [ ] @Query annotation executes without exceptions
- [ ] JPQL/HQL entity names match actual entities
- [ ] @Param names match query placeholders (:name)
- [ ] All parameters bound correctly

**WHERE Clauses:**
- [ ] Filters return correct subset of data
- [ ] Boundary conditions tested (equals, greater than, less than)
- [ ] NULL handling tested (IS NULL, IS NOT NULL)
- [ ] Complex conditions (AND, OR) work correctly

**JOIN Clauses:**
- [ ] INNER JOIN excludes unmatched rows
- [ ] LEFT JOIN includes unmatched rows
- [ ] JOIN conditions correct (no Cartesian products)
- [ ] @EntityGraph or JOIN FETCH prevents N+1

**ORDER BY:**
- [ ] Sorting direction correct (ASC/DESC)
- [ ] Multi-column sorting works (primary, secondary sort)
- [ ] NULL values sorted correctly (NULLS FIRST/LAST)

**Pagination:**
- [ ] Page size respected
- [ ] Page number correct
- [ ] Total elements/pages calculated correctly
- [ ] Last page has correct item count

**Performance:**
- [ ] N+1 queries prevented (Hibernate Statistics)
- [ ] Query count <= expected threshold
- [ ] Large result sets don't cause memory issues

**Next:** See `database-testing-03-transaction-boundaries.md` for testing concurrency and transaction isolation.
