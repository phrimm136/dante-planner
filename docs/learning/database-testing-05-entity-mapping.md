# Database Testing: Entity Mapping & JPA Configuration

**Learning Goal:** Understand how to test that JPA annotations correctly map objects to tables, relationships are configured properly, and cascades work as expected.

---

## Why Test Entity Mapping?

### The Fundamental Problem

**Service tests with mocked repositories don't validate JPA configuration.**

```java
@Test
void findPlanner_ReturnsWithComments() {
    when(plannerRepository.findById(id)).thenReturn(Optional.of(mockPlanner));

    Planner result = plannerService.findById(id);

    assertNotNull(result.getComments());  // ✅ Passes (mocked)
}
```

This test passes even if:
- ❌ `@OneToMany` annotation is missing (no relationship)
- ❌ `mappedBy` is wrong (inverse relationship broken)
- ❌ `CascadeType` is wrong (delete doesn't cascade)
- ❌ `FetchType.LAZY` causes LazyInitializationException
- ❌ Bidirectional sync is broken (child added but parent list not updated)

**Why? No entities are loaded from the database.**

### The Business Impact

**Without entity mapping tests, these bugs reach production:**

1. **LazyInitializationException:** Accessing collection outside transaction crashes
2. **Orphaned records:** Parent deleted, children remain (wrong cascade)
3. **Bidirectional sync bugs:** `planner.getComments()` is empty after `comment.setPlanner(planner)`
4. **N+1 queries:** Lazy loading triggers hundreds of queries
5. **Cascade accidents:** Deleting parent deletes children unexpectedly

**Real example from your codebase:**

```java
@Entity
public class Planner {
    @OneToMany(
        mappedBy = "planner",
        cascade = CascadeType.ALL,          // Delete planner → delete comments
        orphanRemoval = true                // Remove comment from list → delete from DB
    )
    private List<PlannerComment> comments = new ArrayList<>();

    // WITHOUT entity mapping test:
    // - Change to cascade = CascadeType.PERSIST → orphaned comments in production
    // - Change mappedBy = "plannerId" → relationship breaks
    // - Change to FetchType.EAGER → performance degrades (always load comments)
}
```

---

## Entity Mapping Concerns

### 1. Basic Column Mapping

#### Why Column Mapping Matters

**Problem:** JPA annotations don't match database schema

```java
@Entity
@Table(name = "planner")
public class Planner {
    @Column(name = "title", nullable = false, length = 100)
    private String title;

    // If schema has: title VARCHAR(200)
    // Or: title TEXT (no length limit)
    // Tests won't catch the mismatch!
}
```

**What to test:**
- Column names match schema
- Nullable constraints match
- Column lengths match
- Data types match (String → VARCHAR, Instant → TIMESTAMP)
- Enums map correctly

#### Test Pattern:
```java
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class PlannerEntityMappingTest {

    @Autowired private PlannerRepository plannerRepository;
    @Autowired private EntityManager entityManager;

    @Test
    @DisplayName("Column mapping: All fields persist and reload correctly")
    void save_AllFields_RoundTrip() {
        // WHY: Validates @Column annotations map to schema
        // CATCHES: Wrong column names, missing columns

        // Arrange: Entity with all fields populated
        UUID id = UUID.randomUUID();
        Instant now = Instant.now();

        Planner planner = Planner.builder()
                .id(id)
                .user(createTestUser())
                .title("Test Planner")
                .plannerType(PlannerType.MIRROR_DUNGEON)
                .contentVersion("MD6")
                .content("{\"test\":\"data\"}")
                .published(true)
                .upvotes(10)
                .downvotes(2)
                .viewCount(100)
                .createdAt(now)
                .lastModifiedAt(now)
                .build();

        // Act: Save and flush
        plannerRepository.save(planner);
        entityManager.flush();
        entityManager.clear();  // Clear cache

        // Reload from database
        Planner reloaded = plannerRepository.findById(id).orElseThrow();

        // Assert: All fields match (round-trip successful)
        assertEquals(id, reloaded.getId());
        assertEquals("Test Planner", reloaded.getTitle());
        assertEquals(PlannerType.MIRROR_DUNGEON, reloaded.getPlannerType());
        assertEquals("MD6", reloaded.getContentVersion());
        assertEquals("{\"test\":\"data\"}", reloaded.getContent());
        assertTrue(reloaded.isPublished());
        assertEquals(10, reloaded.getUpvotes());
        assertEquals(2, reloaded.getDownvotes());
        assertEquals(100, reloaded.getViewCount());
        assertEquals(now.truncatedTo(ChronoUnit.MILLIS),
                     reloaded.getCreatedAt().truncatedTo(ChronoUnit.MILLIS));

        // LESSON: If any @Column annotation is wrong,
        // field will be null or have wrong value after reload
    }

    @Test
    @DisplayName("Enum mapping: PlannerType persists correctly")
    void save_EnumField_ConvertsToString() {
        // WHY: Documents enum mapping strategy
        // OPTIONS: @Enumerated(STRING) vs @Enumerated(ORDINAL)

        // Arrange
        Planner md = createPlanner(type = PlannerType.MIRROR_DUNGEON);
        Planner rr = createPlanner(type = PlannerType.REFRACTED_RAILWAY);
        entityManager.flush();
        entityManager.clear();

        // Act: Reload
        Planner reloadedMD = plannerRepository.findById(md.getId()).orElseThrow();
        Planner reloadedRR = plannerRepository.findById(rr.getId()).orElseThrow();

        // Assert: Enum values preserved
        assertEquals(PlannerType.MIRROR_DUNGEON, reloadedMD.getPlannerType());
        assertEquals(PlannerType.REFRACTED_RAILWAY, reloadedRR.getPlannerType());

        // Verify database stores as STRING (not ordinal)
        // Query: SELECT planner_type FROM planner WHERE id = ?
        // Expected: "MIRROR_DUNGEON" (not 0)
        // Ordinal storage breaks when enum order changes!
    }

    @Test
    @DisplayName("JSON column: Content stored and retrieved")
    void save_JsonContent_PreservesStructure() {
        // WHY: Validates JSON column mapping (@Column(columnDefinition = "JSON"))
        // MYSQL: JSON type validates syntax

        // Arrange
        String jsonContent = """
            {
                "deck": ["sinner1", "sinner2"],
                "gifts": [1001, 1002]
            }
            """;

        Planner planner = createPlanner();
        planner.setContent(jsonContent);
        plannerRepository.save(planner);
        entityManager.flush();
        entityManager.clear();

        // Act: Reload
        Planner reloaded = plannerRepository.findById(planner.getId()).orElseThrow();

        // Assert: JSON preserved (MySQL validates JSON syntax)
        assertNotNull(reloaded.getContent());
        assertTrue(reloaded.getContent().contains("\"deck\""));
        assertTrue(reloaded.getContent().contains("sinner1"));

        // LESSON: MySQL JSON column validates syntax
        // Invalid JSON → exception at insert
    }
}
```

---

### 2. Relationship Mapping (@OneToMany, @ManyToOne)

#### Why Relationship Mapping Matters

**Problem:** Bidirectional relationships can be misconfigured

```java
// Parent side
@Entity
public class Planner {
    @OneToMany(mappedBy = "planner")  // Inverse relationship
    private List<PlannerComment> comments;
}

// Child side
@Entity
public class PlannerComment {
    @ManyToOne
    @JoinColumn(name = "planner_id")  // Foreign key column
    private Planner planner;
}

// Bug scenarios:
// 1. mappedBy = "plannerId" (wrong field name)
// 2. mappedBy missing → JPA creates join table (wrong schema!)
// 3. @JoinColumn name wrong → references non-existent column
```

**What to test:**
- Relationship navigates both directions
- Foreign key column is correct
- Collection is populated after load
- Bidirectional sync works (add to collection, foreign key set)

#### Test Pattern:
```java
@Nested
@DisplayName("@OneToMany Relationship Mapping")
class OneToManyRelationshipTests {

    @Test
    @DisplayName("Parent → Child navigation works")
    void findPlanner_LoadsComments_Navigation() {
        // WHY: Validates @OneToMany(mappedBy = "planner") is correct
        // CATCHES: Wrong mappedBy field name

        // Arrange
        Planner planner = createTestPlanner();
        PlannerComment comment1 = createComment(planner, "Comment 1");
        PlannerComment comment2 = createComment(planner, "Comment 2");
        entityManager.flush();
        entityManager.clear();

        // Act: Load planner, access comments
        Planner reloaded = plannerRepository.findById(planner.getId()).orElseThrow();
        List<PlannerComment> comments = reloaded.getComments();

        // Assert: Comments loaded via relationship
        assertEquals(2, comments.size());
        assertThat(comments)
            .extracting(PlannerComment::getContent)
            .containsExactlyInAnyOrder("Comment 1", "Comment 2");

        // LESSON: If mappedBy is wrong, comments list is empty
    }

    @Test
    @DisplayName("Child → Parent navigation works")
    void findComment_LoadsPlanner_Navigation() {
        // WHY: Validates @ManyToOne @JoinColumn(name = "planner_id")
        // CATCHES: Wrong column name

        // Arrange
        Planner planner = createTestPlanner();
        PlannerComment comment = createComment(planner, "Test");
        entityManager.flush();
        entityManager.clear();

        // Act: Load comment, access planner
        PlannerComment reloaded = commentRepository.findById(comment.getId()).orElseThrow();
        Planner loadedPlanner = reloaded.getPlanner();

        // Assert: Planner loaded via relationship
        assertNotNull(loadedPlanner);
        assertEquals(planner.getId(), loadedPlanner.getId());
        assertEquals(planner.getTitle(), loadedPlanner.getTitle());

        // LESSON: If @JoinColumn name is wrong,
        // foreign key references non-existent column → exception
    }

    @Test
    @DisplayName("Bidirectional sync: Adding to collection sets foreign key")
    void addCommentToCollection_SetsForeignKey_BidirectionalSync() {
        // WHY: Tests bidirectional relationship synchronization
        // COMMON BUG: Add to collection but forget to set parent

        // Arrange
        Planner planner = createTestPlanner();
        plannerRepository.save(planner);
        entityManager.flush();

        PlannerComment comment = new PlannerComment();
        comment.setContent("Test comment");
        comment.setUser(createTestUser());

        // Act: Add to collection AND set parent (both sides)
        comment.setPlanner(planner);         // Set parent (FK)
        planner.getComments().add(comment);  // Add to collection

        commentRepository.save(comment);
        entityManager.flush();
        entityManager.clear();

        // Assert: Comment saved with correct foreign key
        PlannerComment reloaded = commentRepository.findById(comment.getId()).orElseThrow();
        assertNotNull(reloaded.getPlanner());
        assertEquals(planner.getId(), reloaded.getPlanner().getId());

        // LESSON: Must sync both sides of bidirectional relationship
        // Only adding to collection → foreign key not set → orphan!
    }

    @Test
    @DisplayName("Bidirectional sync: Only setting parent works (JPA manages collection)")
    void setParent_PopulatesCollection_JpaManaged() {
        // WHY: Documents JPA collection management behavior
        // JPA syncs collections after transaction

        // Arrange
        Planner planner = createTestPlanner();
        plannerRepository.save(planner);

        PlannerComment comment = new PlannerComment();
        comment.setContent("Test");
        comment.setUser(createTestUser());
        comment.setPlanner(planner);  // Only set parent (not added to collection)

        commentRepository.save(comment);
        entityManager.flush();
        entityManager.clear();

        // Act: Reload planner
        Planner reloaded = plannerRepository.findById(planner.getId()).orElseThrow();

        // Assert: Comment appears in collection (JPA populated it)
        assertEquals(1, reloaded.getComments().size());
        assertEquals("Test", reloaded.getComments().get(0).getContent());

        // LESSON: JPA synchronizes collections after flush/clear
        // Setting parent is sufficient, collection managed by JPA
    }
}
```

---

### 3. Cascade Operations

#### Why Cascade Testing Matters

**Problem:** Cascade configuration determines what happens when parent is deleted/updated

```java
@OneToMany(
    mappedBy = "planner",
    cascade = CascadeType.ALL,  // What gets cascaded?
    orphanRemoval = true        // Delete orphans?
)
private List<PlannerComment> comments;

// CascadeType options:
// - ALL: Cascade everything (persist, remove, merge, refresh, detach)
// - PERSIST: Only cascade persist (save parent → save children)
// - REMOVE: Only cascade delete (delete parent → delete children)
// - NONE: No cascading (default)

// orphanRemoval:
// - true: Remove from collection → delete from database
// - false: Remove from collection → orphan (FK set to null)
```

**What to test:**
- Deleting parent cascades to children
- orphanRemoval deletes removed entities
- Cascade doesn't delete when it shouldn't

#### Test Pattern:
```java
@Nested
@DisplayName("Cascade Operations")
class CascadeTests {

    @Test
    @DisplayName("CASCADE ALL: Deleting planner deletes comments")
    void deletePlanner_CascadesComments_AllDeleted() {
        // WHY: Validates cascade = CascadeType.ALL works
        // BUSINESS RULE: Deleting planner removes all associated comments

        // Arrange
        Planner planner = createTestPlanner();
        PlannerComment comment1 = createComment(planner, "Comment 1");
        PlannerComment comment2 = createComment(planner, "Comment 2");
        entityManager.flush();

        UUID plannerId = planner.getId();
        Long comment1Id = comment1.getId();
        Long comment2Id = comment2.getId();

        entityManager.clear();

        // Act: Delete planner
        plannerRepository.deleteById(plannerId);
        entityManager.flush();

        // Assert: Comments also deleted (cascade)
        assertFalse(commentRepository.findById(comment1Id).isPresent());
        assertFalse(commentRepository.findById(comment2Id).isPresent());

        // LESSON: Without CascadeType.ALL or CascadeType.REMOVE,
        // comments would be orphaned (FK constraint violation on delete)
    }

    @Test
    @DisplayName("orphanRemoval = true: Removing from collection deletes entity")
    void removeCommentFromCollection_DeletesEntity_OrphanRemoval() {
        // WHY: Validates orphanRemoval = true works
        // BUSINESS RULE: Removing comment from planner → delete from DB

        // Arrange
        Planner planner = createTestPlanner();
        PlannerComment comment = createComment(planner, "Will be removed");
        entityManager.flush();

        Long commentId = comment.getId();

        // Act: Remove from collection
        planner.getComments().remove(comment);
        plannerRepository.save(planner);
        entityManager.flush();
        entityManager.clear();

        // Assert: Comment deleted from database
        assertFalse(commentRepository.findById(commentId).isPresent());

        // LESSON: orphanRemoval = true → removed entity is deleted
        // orphanRemoval = false → removed entity is orphaned (FK = null)
    }

    @Test
    @DisplayName("CASCADE PERSIST: Saving parent saves children")
    void savePlanner_CascadesNewComments_PersistCascade() {
        // WHY: Validates cascade = CascadeType.PERSIST (or ALL)
        // CONVENIENCE: Don't need to save comments explicitly

        // Arrange
        Planner planner = new Planner();
        planner.setId(UUID.randomUUID());
        planner.setUser(createTestUser());
        planner.setTitle("Test Planner");
        // ... set other required fields ...

        PlannerComment comment = new PlannerComment();
        comment.setContent("New comment");
        comment.setUser(createTestUser());
        comment.setPlanner(planner);
        planner.getComments().add(comment);

        // Act: Save planner only (not comment explicitly)
        plannerRepository.save(planner);
        entityManager.flush();
        entityManager.clear();

        // Assert: Comment also saved (cascade persist)
        Planner reloaded = plannerRepository.findById(planner.getId()).orElseThrow();
        assertEquals(1, reloaded.getComments().size());
        assertEquals("New comment", reloaded.getComments().get(0).getContent());

        // LESSON: Without CascadeType.PERSIST or CascadeType.ALL,
        // must explicitly save comment → extra repository call
    }

    @Test
    @DisplayName("No cascade to non-owned side: Deleting comment doesn't delete planner")
    void deleteComment_DoesNotCascadePlanner_Unidirectional() {
        // WHY: Documents cascade directionality
        // SAFETY: Child deletion doesn't affect parent

        // Arrange
        Planner planner = createTestPlanner();
        PlannerComment comment = createComment(planner, "To be deleted");
        entityManager.flush();

        UUID plannerId = planner.getId();
        Long commentId = comment.getId();

        entityManager.clear();

        // Act: Delete comment
        commentRepository.deleteById(commentId);
        entityManager.flush();

        // Assert: Planner still exists
        assertTrue(plannerRepository.findById(plannerId).isPresent());

        // LESSON: Cascade is one-directional (parent → child)
        // @ManyToOne doesn't cascade by default (child → parent)
    }
}
```

---

### 4. Lazy Loading

#### Why Lazy Loading Testing Matters

**Problem:** LazyInitializationException when accessing collection outside transaction

```java
// Controller
@GetMapping("/api/planner/{id}")
public PlannerResponse getPlanner(@PathVariable UUID id) {
    Planner planner = plannerService.findById(id);  // Transaction ends here

    // Access lazy collection OUTSIDE transaction
    int commentCount = planner.getComments().size();  // ❌ LazyInitializationException!

    return PlannerResponse.builder()
        .id(planner.getId())
        .commentCount(commentCount)
        .build();
}
```

**What to test:**
- Lazy collections can be accessed in transaction
- Lazy collections throw exception outside transaction (documents behavior)
- Eager fetching works where needed

#### Test Pattern:
```java
@Nested
@DisplayName("Lazy Loading Behavior")
class LazyLoadingTests {

    @Test
    @DisplayName("Lazy collection: Accessible within transaction")
    @Transactional  // Transaction active
    void lazyCollection_WithinTransaction_Loads() {
        // WHY: Documents expected behavior - lazy collections work in transaction
        // CONTEXT: @Transactional keeps session open

        // Arrange
        Planner planner = createTestPlanner();
        createComment(planner, "Comment 1");
        createComment(planner, "Comment 2");
        entityManager.flush();
        entityManager.clear();

        // Act: Load planner, access lazy collection
        Planner reloaded = plannerRepository.findById(planner.getId()).orElseThrow();

        // Assert: Lazy collection loads successfully (transaction still open)
        assertEquals(2, reloaded.getComments().size());

        // LESSON: Within @Transactional method, lazy loading works
    }

    @Test
    @DisplayName("Lazy collection: Throws exception outside transaction")
    void lazyCollection_OutsideTransaction_ThrowsException() {
        // WHY: Documents LazyInitializationException behavior
        // EDUCATIONAL: This is what happens if you forget to fetch

        // Arrange
        UUID plannerId;
        // Setup in transaction
        {
            Planner planner = createTestPlanner();
            createComment(planner, "Comment");
            plannerRepository.save(planner);
            entityManager.flush();
            plannerId = planner.getId();
        }
        // Transaction ended, session closed

        // Act: Load planner outside transaction
        Planner planner = plannerRepository.findById(plannerId).orElseThrow();

        // Assert: Accessing lazy collection throws exception
        assertThrows(LazyInitializationException.class, () -> {
            planner.getComments().size();  // Lazy load outside transaction
        });

        // LESSON: Must use JOIN FETCH or @EntityGraph to eagerly load
        // Or: keep transaction open (controller → service → repository)
    }

    @Test
    @DisplayName("Eager fetching: @EntityGraph prevents lazy load exception")
    void eagerFetching_EntityGraph_LoadsInOneQuery() {
        // WHY: Validates @EntityGraph solution to lazy loading
        // SOLUTION: Fetch associations in single query

        // Arrange
        Planner planner = createTestPlanner();
        createComment(planner, "Comment 1");
        createComment(planner, "Comment 2");
        entityManager.flush();
        entityManager.clear();

        // Act: Load with @EntityGraph (eager fetch)
        Planner reloaded = plannerRepository.findByIdWithComments(planner.getId())
            .orElseThrow();

        // Clear session to simulate end of transaction
        entityManager.clear();

        // Assert: Comments accessible outside transaction (eagerly fetched)
        assertDoesNotThrow(() -> {
            assertEquals(2, reloaded.getComments().size());
        });

        // LESSON: @EntityGraph or JOIN FETCH prevents lazy loading issues
        // Collections are loaded immediately, available outside transaction
    }
}
```

---

### 5. Composite Keys

#### Why Composite Key Testing Matters

**Problem:** Composite keys require correct `@IdClass` and `equals()/hashCode()`

```java
// Composite key class
@Embeddable
public class PlannerVoteId implements Serializable {
    private Long userId;
    private UUID plannerId;

    // CRITICAL: Must implement equals() and hashCode()
    // Without correct implementation:
    // - Set<PlannerVote> has duplicates
    // - em.find() doesn't work correctly
    // - Hibernate cache breaks
}

// Entity using composite key
@Entity
@IdClass(PlannerVoteId.class)
public class PlannerVote {
    @Id
    private Long userId;

    @Id
    private UUID plannerId;

    // ...
}
```

**What to test:**
- Composite key save and reload works
- equals()/hashCode() work correctly
- Set operations work (no duplicates)
- EntityManager.find() works with composite key

#### Test Pattern:
```java
@Nested
@DisplayName("Composite Key (@IdClass)")
class CompositeKeyTests {

    @Test
    @DisplayName("Composite key: Save and reload works")
    void save_CompositeKey_RoundTrip() {
        // WHY: Validates @IdClass configuration
        // CATCHES: Missing @Id annotations, wrong IdClass

        // Arrange
        User user = createTestUser();
        Planner planner = createTestPlanner();

        PlannerVote vote = new PlannerVote(user.getId(), planner.getId(), VoteType.UP);

        // Act: Save with composite key
        voteRepository.save(vote);
        entityManager.flush();
        entityManager.clear();

        // Reload using composite key
        PlannerVoteId id = new PlannerVoteId(user.getId(), planner.getId());
        PlannerVote reloaded = entityManager.find(PlannerVote.class, id);

        // Assert: Vote reloaded correctly
        assertNotNull(reloaded);
        assertEquals(user.getId(), reloaded.getUserId());
        assertEquals(planner.getId(), reloaded.getPlannerId());
        assertEquals(VoteType.UP, reloaded.getVoteType());

        // LESSON: If @IdClass or @Id annotations wrong,
        // entityManager.find() returns null
    }

    @Test
    @DisplayName("Composite key: equals() and hashCode() work correctly")
    void compositeKey_EqualsHashCode_SetOperations() {
        // WHY: Validates equals()/hashCode() implementation in IdClass
        // CRITICAL: Required for Set operations and Hibernate cache

        // Arrange
        PlannerVoteId id1 = new PlannerVoteId(1L, UUID.randomUUID());
        PlannerVoteId id2 = new PlannerVoteId(1L, id1.getPlannerId());  // Same values
        PlannerVoteId id3 = new PlannerVoteId(2L, id1.getPlannerId());  // Different user

        // Assert: equals() works
        assertEquals(id1, id2, "Same values should be equal");
        assertNotEquals(id1, id3, "Different values should not be equal");

        // Assert: hashCode() consistent with equals()
        assertEquals(id1.hashCode(), id2.hashCode(),
            "Equal objects must have same hashCode");

        // Assert: Set operations work
        Set<PlannerVoteId> set = new HashSet<>();
        set.add(id1);
        set.add(id2);  // Duplicate (same as id1)
        set.add(id3);  // Different

        assertEquals(2, set.size(), "Set should contain only unique IDs");
        assertTrue(set.contains(id1));
        assertTrue(set.contains(id3));

        // LESSON: Without correct equals()/hashCode(),
        // Set operations break (duplicates appear)
    }

    @Test
    @DisplayName("Composite key: Persistable interface with isNew()")
    void compositeKey_Persistable_IsNewLogic() {
        // WHY: Documents Persistable interface for composite keys
        // OPTIMIZATION: Prevents SELECT before INSERT

        // Arrange
        User user = createTestUser();
        Planner planner = createTestPlanner();

        // New vote (not yet persisted)
        PlannerVote vote = new PlannerVote(user.getId(), planner.getId(), VoteType.UP);

        // Assert: isNew() returns true
        assertTrue(vote.isNew(), "New entity should return true from isNew()");

        // Act: Save
        voteRepository.save(vote);
        entityManager.flush();

        // Assert: isNew() returns false after save
        assertFalse(vote.isNew(), "Persisted entity should return false from isNew()");

        // LESSON: Persistable.isNew() tells JPA if entity is new
        // Without it: JPA does SELECT before INSERT (inefficient)
        // With it: JPA directly INSERT (efficient)
    }
}
```

---

## Summary Checklist

When testing entity mapping:

**Column Mapping:**
- [ ] All fields round-trip (save → reload → equals)
- [ ] Enum fields persist correctly (STRING not ORDINAL)
- [ ] Date/time fields preserve precision
- [ ] JSON columns validate syntax (MySQL)
- [ ] Nullable constraints match database

**Relationships:**
- [ ] Parent → Child navigation works (@OneToMany)
- [ ] Child → Parent navigation works (@ManyToOne)
- [ ] Bidirectional sync tested (both sides updated)
- [ ] mappedBy field name correct
- [ ] @JoinColumn name matches database column

**Cascade Operations:**
- [ ] DELETE cascades to children (CascadeType.ALL/REMOVE)
- [ ] orphanRemoval deletes removed entities
- [ ] PERSIST cascades saves children (convenience)
- [ ] Non-owned side doesn't cascade (safety)

**Lazy Loading:**
- [ ] Lazy collections accessible in transaction
- [ ] LazyInitializationException documented (outside transaction)
- [ ] @EntityGraph/JOIN FETCH prevents lazy load issues
- [ ] N+1 queries prevented (see query correctness guide)

**Composite Keys:**
- [ ] @IdClass configuration correct
- [ ] Save and reload with composite key works
- [ ] equals()/hashCode() implemented correctly
- [ ] Set operations work (no duplicates)
- [ ] Persistable.isNew() optimizes INSERT

**Entity Lifecycle:**
- [ ] @PrePersist callbacks execute
- [ ] @PreUpdate callbacks execute
- [ ] @Version increments on update (optimistic locking)
- [ ] Timestamps auto-populate (createdAt, lastModifiedAt)

**Testing Mechanics:**
- [ ] entityManager.flush() forces SQL execution
- [ ] entityManager.clear() invalidates cache (forces reload)
- [ ] @Transactional for test isolation
- [ ] Test data cleaned up (auto-rollback)

---

## Complete Database Testing Suite

You've now learned all five database testing concerns:

1. ✅ **Constraint Enforcement** - FK, UNIQUE, NOT NULL, CHECK constraints
2. ✅ **Query Correctness** - @Query syntax, N+1 problems, JOIN correctness
3. ✅ **Transaction Boundaries** - Lost updates, race conditions, isolation, deadlocks
4. ✅ **Dialect Differences** - H2 vs MySQL, TestContainers for production testing
5. ✅ **Entity Mapping** - JPA annotations, relationships, cascades, lazy loading

**Together, these tests validate the persistence layer comprehensively:**

```
Service Layer Tests (with mocks)
         ↓
    Business Logic
         ↓
Repository Layer Tests (with real DB)
         ↓
┌──────────────────────────────┐
│  Database Integration Tests   │
├──────────────────────────────┤
│ ✓ Constraints enforced       │
│ ✓ Queries execute correctly  │
│ ✓ Transactions isolated      │
│ ✓ Dialect compatibility      │
│ ✓ Entities mapped correctly  │
└──────────────────────────────┘
         ↓
    Production Database
```

**Recommended test distribution:**
- 60% Service tests (fast, mocked)
- 30% Repository tests (H2, basic queries)
- 10% Integration tests (TestContainers, critical paths)

This layered approach catches different bug classes at appropriate boundaries, ensuring database-related bugs are caught before production deployment.
