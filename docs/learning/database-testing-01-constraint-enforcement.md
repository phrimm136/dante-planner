# Database Testing: Constraint Enforcement

**Learning Goal:** Understand how to test that database constraints (FK, UNIQUE, NOT NULL, CHECK) actually prevent invalid data from being persisted.

---

## Why Test Constraints?

### The Fundamental Problem

**Service layer tests with mocks verify business logic assuming the database works correctly.**

```java
// Service test with mocked repository
@Test
void createVote_ValidData_Success() {
    when(voteRepository.save(any())).thenReturn(mockVote);

    VoteResponse response = voteService.castVote(userId, plannerId, VoteType.UP);

    assertNotNull(response);
    verify(voteRepository).save(any());  // ✅ Test passes
}
```

This test passes even if:
- ❌ The vote violates a UNIQUE constraint (duplicate vote)
- ❌ The planner ID doesn't exist (FK violation)
- ❌ The database schema was changed (constraint removed)
- ❌ The SQL has a syntax error

**Why? The repository is mocked - no real SQL ever executes.**

### The Business Impact

**Without constraint tests, these bugs reach production:**

1. **500 errors for users:** Constraint violations crash the application
2. **Data corruption:** Missing constraints allow invalid data to persist
3. **Silent failures:** Schema migrations remove constraints without breaking tests
4. **Debugging nightmares:** Stack traces point to database, not the root cause

**Real example from your codebase:**

```java
// V018 Migration: Made votes immutable
// Old schema: Votes could be deleted (no UNIQUE, had deleted_at)
// New schema: Votes are immutable (UNIQUE constraint on user_id + planner_id)

// WITHOUT constraint test:
// - Migration runs
// - Tests still pass (mocked)
// - Production breaks: Duplicate vote attempts crash with 500

// WITH constraint test:
// - Migration runs
// - Constraint test fails: "Expected exception not thrown"
// - Developer adds @Unique annotation to entity
// - Build passes, migration validated
```

### What Constraint Tests Actually Verify

**Constraint tests verify the persistence boundary:**
- ✅ JPA entity annotations match database schema
- ✅ Migrations actually create/modify constraints
- ✅ Hibernate generates correct DDL
- ✅ Database enforces data integrity

**They complement, not replace, service tests:**

| Layer | Service Test Verifies | Constraint Test Verifies |
|-------|----------------------|-------------------------|
| Business Logic | Vote threshold calculation correct | N/A |
| Application Validation | @Valid catches empty vote type | N/A |
| ORM Mapping | N/A | JPA entity maps to correct table |
| Database Constraints | N/A | UNIQUE prevents duplicate votes |

---

## Constraint Types to Test

### 1. Foreign Key Constraints (Referential Integrity)

#### Why Foreign Keys Matter

**Purpose:** Ensure references point to existing records

**Without FK constraints:**
```java
// Orphaned comment (planner deleted, comment remains)
Comment comment = commentRepository.findById(123);
comment.getPlanner(); // Returns null or throws NPE
```

**Production bug this catches:**
```java
// User story: "Delete planner" feature added
// Developer forgets about cascade configuration
plannerRepository.deleteById(plannerId);

// Without FK constraint test:
// - Planner deleted
// - Comments orphaned (planner_id points to nothing)
// - Frontend crashes: "Cannot read property 'title' of null"

// With FK constraint test:
// - Test tries to delete planner with comments
// - Constraint violation thrown (or cascade tested)
// - Developer fixes cascade config BEFORE production
```

**What it prevents:**
- Orphaned records (child exists, parent deleted)
- Invalid references (ID doesn't exist)
- Data inconsistency (referential integrity violations)
- Cascade accidents (unexpected deletions)

#### Example schema:
```sql
CREATE TABLE planner_comment (
    id BIGINT PRIMARY KEY,
    planner_id CHAR(36) NOT NULL,
    user_id BIGINT NOT NULL,
    content TEXT NOT NULL,
    FOREIGN KEY (planner_id) REFERENCES planner(id) ON DELETE CASCADE,
    FOREIGN KEY (user_id) REFERENCES user(id) ON DELETE RESTRICT
);
```

**Configuration matters:**
- `ON DELETE CASCADE`: Delete parent → delete children (e.g., planner deleted → comments deleted)
- `ON DELETE RESTRICT`: Delete parent fails if children exist (e.g., user deleted → fails if comments exist)
- `ON DELETE SET NULL`: Delete parent → set child FK to NULL (rarely used)

#### Test pattern:
```java
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class CommentRepositoryConstraintTest {

    @Autowired private PlannerCommentRepository commentRepository;
    @Autowired private EntityManager entityManager;

    @Nested
    @DisplayName("Foreign Key Constraints")
    class ForeignKeyTests {

        @Test
        @DisplayName("FK violation: non-existent planner throws exception")
        void save_NonExistentPlanner_ThrowsException() {
            // WHY: Prevents orphaned comments that reference deleted planners

            // Arrange: Create comment with invalid planner ID
            UUID invalidPlannerId = UUID.randomUUID();
            PlannerComment comment = PlannerComment.builder()
                    .planner(Planner.builder().id(invalidPlannerId).build())
                    .user(createTestUser())
                    .content("This will fail")
                    .build();

            // Act & Assert: FK constraint violation
            assertThrows(DataIntegrityViolationException.class, () -> {
                commentRepository.save(comment);
                entityManager.flush(); // Force SQL execution
            });

            // LESSON: If this test fails after migration, FK constraint is missing
        }

        @Test
        @DisplayName("FK violation: non-existent user throws exception")
        void save_NonExistentUser_ThrowsException() {
            // WHY: Prevents comments from anonymous/deleted users

            Planner planner = createTestPlanner();

            PlannerComment comment = PlannerComment.builder()
                    .planner(planner)
                    .user(User.builder().id(999999L).build()) // Invalid user
                    .content("This will fail")
                    .build();

            assertThrows(DataIntegrityViolationException.class, () -> {
                commentRepository.save(comment);
                entityManager.flush();
            });
        }

        @Test
        @DisplayName("Cascade delete: deleting planner cascades to comments")
        void delete_PlannerWithComments_CascadesDelete() {
            // WHY: Documents expected cascade behavior
            // BUSINESS RULE: Deleting planner should delete all comments

            // Arrange
            Planner planner = createTestPlanner();
            PlannerComment comment1 = createComment(planner, "Comment 1");
            PlannerComment comment2 = createComment(planner, "Comment 2");
            entityManager.flush();
            entityManager.clear();

            // Act: Delete planner
            plannerRepository.deleteById(planner.getId());
            entityManager.flush();

            // Assert: Comments should be deleted (CASCADE)
            assertFalse(commentRepository.findById(comment1.getId()).isPresent());
            assertFalse(commentRepository.findById(comment2.getId()).isPresent());

            // If this fails, cascade configuration is wrong:
            // - @OneToMany(cascade = CascadeType.ALL) missing on entity
            // - OR: ON DELETE CASCADE missing from schema
        }

        @Test
        @DisplayName("ON DELETE RESTRICT: deleting user with comments fails")
        void delete_UserWithComments_ThrowsException() {
            // WHY: Prevents accidental user deletion when they have content
            // BUSINESS RULE: User deletion should fail if comments exist (soft-delete instead)

            // Arrange
            User user = createTestUser();
            Planner planner = createTestPlanner();
            createComment(planner, user, "Comment by user");
            entityManager.flush();

            // Act & Assert: FK constraint prevents deletion
            assertThrows(DataIntegrityViolationException.class, () -> {
                userRepository.deleteById(user.getId());
                entityManager.flush();
            });

            // LESSON: This forces application code to soft-delete users
            // (set deletedAt timestamp) instead of hard-delete
        }
    }
}
```

**Key techniques:**
- `entityManager.flush()` forces immediate SQL execution (catches violations)
- `entityManager.clear()` clears first-level cache (forces re-fetch)
- Test both directions: child → parent and parent → child
- Test cascade behavior (DELETE CASCADE vs RESTRICT)

**Why flush() is critical:**
```java
// ❌ WITHOUT flush - test passes incorrectly
@Test
void test() {
    comment.setPlanner(invalidPlanner);
    repository.save(comment);
    // @Transactional rolls back
    // SQL never executed, FK constraint never checked!
}

// ✅ WITH flush - constraint checked
@Test
void test() {
    comment.setPlanner(invalidPlanner);
    repository.save(comment);
    entityManager.flush();  // SQL executes NOW, FK checked
    // Exception thrown here
}
```

---

### 2. UNIQUE Constraints (Duplicate Prevention)

#### Why UNIQUE Matters

**Purpose:** Prevent duplicate data where business rules require uniqueness

**Without UNIQUE constraints:**
```java
// User votes twice on same planner
voteService.castVote(userId, plannerId, UP);  // First vote
voteService.castVote(userId, plannerId, UP);  // Duplicate vote (!!)

// Result: Vote count inflated, user can manipulate voting
```

**Production bug this catches:**
```java
// V018 Migration: Changed voting from toggle to immutable
// Old: User can upvote/remove/upvote (no UNIQUE)
// New: User can only upvote once (UNIQUE constraint)

// WITHOUT constraint test:
// - Migration adds UNIQUE constraint
// - Application code still allows re-voting
// - Production: 409 Conflict errors
// - Users confused: "I already voted, why does it fail?"

// WITH constraint test:
// - Test attempts duplicate vote
// - Constraint violation thrown
// - Developer adds duplicate check in service layer
// - Application handles gracefully: "You already voted"
```

**What it prevents:**
- Vote manipulation (voting multiple times)
- Duplicate user accounts (same email)
- Data integrity violations (business rule: one vote per user per planner)
- Race conditions (concurrent inserts creating duplicates)

#### Example schema:
```sql
CREATE TABLE planner_vote (
    user_id BIGINT NOT NULL,
    planner_id CHAR(36) NOT NULL,
    vote_type VARCHAR(10) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    PRIMARY KEY (user_id, planner_id),  -- Composite key
    UNIQUE (user_id, planner_id)        -- Explicit UNIQUE constraint
);
```

**Why composite UNIQUE:**
- One user + one planner = one vote (prevents duplicates)
- Same user can vote on different planners
- Different users can vote on same planner
- Enforces business rule at database level

#### Test pattern:
```java
@Nested
@DisplayName("UNIQUE Constraints")
class UniqueConstraintTests {

    @Test
    @DisplayName("Duplicate vote: same user + planner throws exception")
    void save_DuplicateVote_ThrowsException() {
        // WHY: Enforces business rule - one vote per user per planner
        // PREVENTS: Vote manipulation, inflated vote counts

        // Arrange: First vote succeeds
        User user = createTestUser();
        Planner planner = createTestPlanner();

        PlannerVote vote1 = new PlannerVote(user.getId(), planner.getId(), VoteType.UP);
        voteRepository.save(vote1);
        entityManager.flush(); // Commit first vote
        entityManager.clear();

        // Act & Assert: Duplicate vote fails
        PlannerVote vote2 = new PlannerVote(user.getId(), planner.getId(), VoteType.UP);

        DataIntegrityViolationException exception = assertThrows(
            DataIntegrityViolationException.class,
            () -> {
                voteRepository.save(vote2);
                entityManager.flush();
            }
        );

        // Optional: Verify error message contains constraint name
        assertThat(exception.getMessage())
            .containsIgnoringCase("unique")
            .containsIgnoringCase("planner_vote");

        // LESSON: Application should check existence BEFORE attempting save
        // This test documents what happens if check is bypassed/fails
    }

    @Test
    @DisplayName("Different users can vote on same planner")
    void save_DifferentUsers_Succeeds() {
        // WHY: Documents expected behavior - uniqueness is per user

        // Arrange
        User user1 = createTestUser("user1@example.com");
        User user2 = createTestUser("user2@example.com");
        Planner planner = createTestPlanner();

        // Act: Both votes should succeed
        PlannerVote vote1 = new PlannerVote(user1.getId(), planner.getId(), VoteType.UP);
        PlannerVote vote2 = new PlannerVote(user2.getId(), planner.getId(), VoteType.UP);

        voteRepository.save(vote1);
        voteRepository.save(vote2);
        entityManager.flush();

        // Assert: Both votes persisted
        assertEquals(2, voteRepository.count());

        // LESSON: UNIQUE constraint allows this scenario
        // Business rule: Multiple users can vote on same planner
    }

    @Test
    @DisplayName("Same user can vote on different planners")
    void save_DifferentPlanners_Succeeds() {
        // WHY: Documents composite key behavior

        // Arrange
        User user = createTestUser();
        Planner planner1 = createTestPlanner();
        Planner planner2 = createTestPlanner();

        // Act
        PlannerVote vote1 = new PlannerVote(user.getId(), planner1.getId(), VoteType.UP);
        PlannerVote vote2 = new PlannerVote(user.getId(), planner2.getId(), VoteType.UP);

        voteRepository.save(vote1);
        voteRepository.save(vote2);
        entityManager.flush();

        // Assert
        assertEquals(2, voteRepository.count());

        // LESSON: UNIQUE (user_id, planner_id) means:
        // - Same user + same planner = duplicate (fails)
        // - Same user + different planner = allowed (succeeds)
    }

    @Test
    @DisplayName("Username uniqueness: duplicate username throws exception")
    void save_DuplicateUsername_ThrowsException() {
        // WHY: Prevents username collisions (business requirement)
        // CONTEXT: Username = keyword + suffix (e.g., "Faust-W_CORP-abc12")

        // Arrange: First user with username
        User user1 = User.builder()
                .email("user1@example.com")
                .usernameKeyword("W_CORP")
                .usernameSuffix("test1")
                .build();
        userRepository.save(user1);
        entityManager.flush();

        // Act & Assert: Duplicate full username fails
        User user2 = User.builder()
                .email("user2@example.com")  // Different email
                .usernameKeyword("W_CORP")   // Same keyword
                .usernameSuffix("test1")     // Same suffix
                .build();

        assertThrows(DataIntegrityViolationException.class, () -> {
            userRepository.save(user2);
            entityManager.flush();
        });

        // LESSON: Username generation must check collisions
        // This test validates that RandomUsernameGenerator retries on collision
    }
}
```

**Key insights:**
- **Composite UNIQUE constraints:** Test all parts of the key independently
- **Test boundaries:** Same user different planner, different user same planner
- **Error message inspection:** Verify exception contains constraint name (helps debugging)
- **Clear cache:** Use `entityManager.clear()` to ensure fresh state

**Why composite keys need careful testing:**
```java
// UNIQUE (user_id, planner_id)

// ✅ Allowed: user1 + planner1
// ✅ Allowed: user1 + planner2 (different planner)
// ✅ Allowed: user2 + planner1 (different user)
// ❌ Rejected: user1 + planner1 (duplicate)

// Without tests, developers might assume:
// - UNIQUE means "one vote per user" (wrong - allows multiple planners)
// - Or: "one vote per planner" (wrong - allows multiple users)
```

---

### 3. NOT NULL Constraints (Required Fields)

#### Why NOT NULL Matters

**Purpose:** Enforce required fields at database level

**Without NOT NULL constraints:**
```java
// Planner created without title
Planner planner = new Planner();
planner.setUser(user);
// Forgot to set title!
plannerRepository.save(planner);

// Result: Title is NULL in database
// Frontend crashes: Cannot display planner with null title
```

**Production bug this catches:**
```java
// Developer adds new required field to entity
@Entity
public class Planner {
    private String title;
    private String description;  // New field added
}

// WITHOUT NOT NULL constraint test:
// - Old planners have NULL description
// - Frontend assumes description exists
// - NullPointerException when accessing old planners

// WITH NOT NULL constraint test:
// - Migration adds NOT NULL with default value
// - Test validates all planners have description
// - Migration fails if default missing
```

**What it prevents:**
- Missing required data (null title, null user)
- Application crashes (NPE when accessing null fields)
- Database inconsistency (some records missing critical data)
- Silent data corruption (null values where business logic assumes non-null)

#### Example schema:
```sql
CREATE TABLE planner (
    id CHAR(36) PRIMARY KEY,
    user_id BIGINT NOT NULL,
    title VARCHAR(100) NOT NULL,
    planner_type VARCHAR(50) NOT NULL,
    content JSON NOT NULL,
    published BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL
);
```

**Important distinction:**
- `NOT NULL`: Database rejects NULL values
- `@NotNull`: Bean Validation rejects NULL before reaching database
- **Both needed:** Defense in depth (application + database layers)

#### Test pattern:
```java
@Nested
@DisplayName("NOT NULL Constraints")
class NotNullTests {

    @Test
    @DisplayName("Missing title: throws exception")
    void save_MissingTitle_ThrowsException() {
        // WHY: Title is required business field (cannot display planner without title)
        // LAYER: Database constraint (last line of defense if Bean Validation bypassed)

        // Arrange
        User user = createTestUser();
        Planner planner = Planner.builder()
                .id(UUID.randomUUID())
                .user(user)
                // .title(null) - missing required field
                .plannerType(PlannerType.MIRROR_DUNGEON)
                .content("{}")
                .build();

        // Act & Assert
        assertThrows(DataIntegrityViolationException.class, () -> {
            plannerRepository.save(planner);
            entityManager.flush();
        });

        // LESSON: If @NotNull Bean Validation is removed from entity,
        // this test catches the regression
    }

    @Test
    @DisplayName("Empty string title: allowed (different from NULL)")
    void save_EmptyTitle_Succeeds() {
        // WHY: Documents important distinction - empty ≠ NULL
        // BUSINESS DECISION: Should empty strings be allowed?

        // Arrange
        User user = createTestUser();
        Planner planner = Planner.builder()
                .id(UUID.randomUUID())
                .user(user)
                .title("") // Empty but not NULL
                .plannerType(PlannerType.MIRROR_DUNGEON)
                .content("{}")
                .build();

        // Act & Assert: Database allows empty (NOT NULL only rejects NULL)
        assertDoesNotThrow(() -> {
            plannerRepository.save(planner);
            entityManager.flush();
        });

        // LESSON: Application-level validation should catch empty strings
        // Database NOT NULL constraint only prevents NULL, not empty
        // Add @NotBlank if empty strings should be rejected
    }

    @Test
    @DisplayName("Missing user (foreign key + NOT NULL): throws exception")
    void save_MissingUser_ThrowsException() {
        // WHY: Every planner must have an owner (business rule)
        // ENFORCED BY: FK constraint + NOT NULL (double protection)

        Planner planner = Planner.builder()
                .id(UUID.randomUUID())
                // .user(null) - violates NOT NULL
                .title("Test Planner")
                .plannerType(PlannerType.MIRROR_DUNGEON)
                .content("{}")
                .build();

        assertThrows(DataIntegrityViolationException.class, () -> {
            plannerRepository.save(planner);
            entityManager.flush();
        });

        // LESSON: NOT NULL + FK = strong constraint
        // - NOT NULL: user_id cannot be NULL
        // - FK: user_id must reference existing user
    }

    @Test
    @DisplayName("Default value: published defaults to false")
    void save_MissingPublished_DefaultsToFalse() {
        // WHY: Documents database default value behavior
        // BUSINESS RULE: New planners are unpublished by default

        // Arrange
        User user = createTestUser();
        Planner planner = Planner.builder()
                .id(UUID.randomUUID())
                .user(user)
                .title("Test")
                .plannerType(PlannerType.MIRROR_DUNGEON)
                .content("{}")
                // .published(null) - should use DB default
                .build();

        // Act
        Planner saved = plannerRepository.save(planner);
        entityManager.flush();
        entityManager.clear();

        // Assert: Database default applied
        Planner reloaded = plannerRepository.findById(saved.getId()).orElseThrow();
        assertFalse(reloaded.isPublished());

        // LESSON: Database defaults work even if entity field is null
        // This tests schema definition: published BOOLEAN NOT NULL DEFAULT FALSE
    }
}
```

**Key insights:**
- **NULL vs empty:** Database allows empty strings but not NULL (test both)
- **Default values:** Test that DB defaults are applied when field is null
- **JPA vs DB validation:** Bean Validation (@NotNull) runs before DB constraints
- **Flush timing:** Must flush to trigger constraint check

**Defense in depth - three layers:**
```java
// Layer 1: Bean Validation (application layer)
@Entity
public class Planner {
    @NotNull(message = "Title is required")
    @NotBlank(message = "Title cannot be empty")
    private String title;
}

// Layer 2: JPA column definition (ORM layer)
@Column(nullable = false)
private String title;

// Layer 3: Database constraint (database layer)
title VARCHAR(100) NOT NULL

// Why all three?
// - Bean Validation: Fast feedback, better error messages
// - JPA nullable: Hibernate generates correct DDL
// - DB constraint: Last defense if application bypassed
```

---

### 4. CHECK Constraints (Domain Validation)

#### Why CHECK Matters

**Purpose:** Enforce domain-specific business rules at database level

**Without CHECK constraints:**
```java
// Comment depth set to negative value
comment.setDepth(-5);
commentRepository.save(comment);

// Result: Invalid data persisted
// Frontend logic breaks: depth used in array indexing → crash
```

**Production bug this catches:**
```java
// Business rule: Comment threading max depth is 5
// Application code flattens depth 6+ to depth 5

// WITHOUT CHECK constraint:
// - Bug in flattening logic allows depth = 10
// - Frontend rendering breaks (expects max 5 levels)
// - UI shows comment far off-screen

// WITH CHECK constraint:
// - Buggy code attempts to save depth = 10
// - CHECK (depth <= 5) rejects it
// - Exception thrown, bug caught before deployment
```

**What it prevents:**
- Invalid enum values (vote type = "SIDEWAYS")
- Out-of-range numbers (depth = -1, upvotes = -100)
- Invalid formats (phone without area code)
- Business rule violations (can't have more than X of Y)

#### Example schema:
```sql
CREATE TABLE planner_comment (
    id BIGINT PRIMARY KEY,
    content TEXT NOT NULL,
    depth INT NOT NULL CHECK (depth >= 0 AND depth <= 5),
    upvote_count INT NOT NULL CHECK (upvote_count >= 0),
    created_at TIMESTAMP NOT NULL
);
```

**CHECK vs application validation:**
```java
// Application validation: User-facing errors
@Min(value = 0, message = "Depth cannot be negative")
@Max(value = 5, message = "Maximum depth is 5")
private int depth;

// CHECK constraint: Database integrity (last defense)
CHECK (depth >= 0 AND depth <= 5)

// Why both?
// - Application: Better error messages, earlier validation
// - Database: Protects against bugs, data corruption, admin tools
```

#### Test pattern:
```java
@Nested
@DisplayName("CHECK Constraints")
class CheckConstraintTests {

    @Test
    @DisplayName("Negative depth: violates CHECK constraint")
    void save_NegativeDepth_ThrowsException() {
        // WHY: Threading depth must be non-negative (represents nesting level)
        // BUSINESS RULE: depth 0 = top-level, depth 1-5 = replies

        // Arrange
        PlannerComment comment = PlannerComment.builder()
                .planner(createTestPlanner())
                .user(createTestUser())
                .content("Test comment")
                .depth(-1)  // Invalid: CHECK (depth >= 0)
                .build();

        // Act & Assert
        assertThrows(DataIntegrityViolationException.class, () -> {
            commentRepository.save(comment);
            entityManager.flush();
        });

        // LESSON: This catches bugs in threading logic
        // If flattening code has off-by-one error → caught here
    }

    @Test
    @DisplayName("Depth > 5: violates CHECK constraint")
    void save_DepthOver5_ThrowsException() {
        // WHY: Business rule - max 5 levels of threading
        // PREVENTS: Infinite nesting, frontend rendering issues

        PlannerComment comment = PlannerComment.builder()
                .planner(createTestPlanner())
                .user(createTestUser())
                .content("Test comment")
                .depth(6)  // Invalid: CHECK (depth <= 5)
                .build();

        assertThrows(DataIntegrityViolationException.class, () -> {
            commentRepository.save(comment);
            entityManager.flush();
        });

        // REAL BUG THIS CAUGHT:
        // - Flattening logic had bug: depth 5 reply calculated as depth 6
        // - Without CHECK: Frontend broke (tried to render 6 levels)
        // - With CHECK: Exception thrown, bug fixed before deploy
    }

    @Test
    @DisplayName("Negative upvote count: violates CHECK constraint")
    void save_NegativeUpvotes_ThrowsException() {
        // WHY: Vote count cannot be negative (physical constraint)
        // PREVENTS: Race condition bugs in increment/decrement logic

        PlannerComment comment = PlannerComment.builder()
                .planner(createTestPlanner())
                .user(createTestUser())
                .content("Test comment")
                .depth(0)
                .upvoteCount(-5)  // Invalid
                .build();

        assertThrows(DataIntegrityViolationException.class, () -> {
            commentRepository.save(comment);
            entityManager.flush();
        });

        // LESSON: If vote removal logic has bug (decrement past zero),
        // this constraint prevents data corruption
    }

    @Test
    @DisplayName("Boundary values: depth 0 and 5 are valid")
    void save_BoundaryDepth_Succeeds() {
        // WHY: Documents valid range boundaries
        // PREVENTS: Off-by-one errors in validation

        // Test minimum boundary (top-level comment)
        PlannerComment comment0 = createCommentWithDepth(0);
        assertDoesNotThrow(() -> {
            commentRepository.save(comment0);
            entityManager.flush();
        });

        // Test maximum boundary (deepest reply before flattening)
        PlannerComment comment5 = createCommentWithDepth(5);
        assertDoesNotThrow(() -> {
            commentRepository.save(comment5);
            entityManager.flush();
        });

        // LESSON: CHECK (depth >= 0 AND depth <= 5) means:
        // - 0 is valid (minimum)
        // - 5 is valid (maximum)
        // - -1 and 6 are invalid
        // Test all four boundaries!
    }
}
```

**Key insights:**
- **Boundary testing:** Test min, max, and out-of-range values (all four boundaries)
- **Enum constraints:** VARCHAR fields with CHECK IN ('VALUE1', 'VALUE2')
- **H2 limitation:** H2 supports CHECK but validation behavior differs from MySQL/PostgreSQL
- **Application vs DB:** Application should validate before reaching DB (defensive layers)

**Real-world example - why CHECK constraints matter:**
```java
// Your codebase: Comment threading flattening logic

// Business rule: Max depth 5, replies at depth 5 become siblings
public PlannerComment createReply(Long parentId, String content) {
    PlannerComment parent = commentRepository.findById(parentId);

    int newDepth = parent.getDepth() < 5
        ? parent.getDepth() + 1  // Normal nesting
        : 5;                      // Flatten at depth 5

    // Bug scenario: What if parent.getDepth() returns null or negative?
    // Without CHECK: depth could be Integer.MIN_VALUE or null
    // With CHECK: Database rejects invalid depth, exception thrown

    PlannerComment reply = new PlannerComment();
    reply.setDepth(newDepth);
    reply.setParentCommentId(parent.getId());
    return commentRepository.save(reply);
}
```

---

## Testing Strategy: When to Flush

**Critical pattern:** `entityManager.flush()` forces SQL execution

```java
// ❌ BAD: Constraint never checked
@Test
void testConstraint() {
    entity.setInvalidValue(-1);
    repository.save(entity);
    // Test ends, @Transactional rolls back
    // SQL never executed, constraint not checked!
}

// ✅ GOOD: Constraint checked immediately
@Test
void testConstraint() {
    entity.setInvalidValue(-1);
    repository.save(entity);
    entityManager.flush();  // Forces SQL execution NOW
    // Constraint violation thrown here
}
```

### Why @Transactional Auto-Rollback Hides Bugs

**The problem:**
```java
@Test
@Transactional  // Auto-rollback at end of test
void test() {
    // 1. Insert invalid data
    repository.save(invalidEntity);

    // 2. Test ends
    // 3. @Transactional rolls back
    // 4. SQL never committed to database
    // 5. Constraint never checked!
    // 6. Test passes incorrectly ✅
}
```

**The solution:**
```java
@Test
@Transactional
void test() {
    repository.save(invalidEntity);
    entityManager.flush();  // Force SQL NOW (before rollback)
    // Constraint checked, exception thrown ❌
}
```

### When to flush:
1. **After save:** To trigger INSERT constraints
2. **After update:** To trigger UPDATE constraints
3. **Before delete:** To check FK restrictions
4. **After batch operations:** To commit batch

### When to clear:
```java
entityManager.flush();  // Write to DB
entityManager.clear();  // Clear cache
// Next fetch comes from database, not cache
Entity reloaded = repository.findById(id).orElseThrow();
```

**Why clear() matters:**
```java
// Without clear - reads from cache
Entity entity = repository.save(new Entity());
entityManager.flush();
Entity reloaded = repository.findById(entity.getId());
// reloaded === entity (same object, from cache)

// With clear - reads from database
Entity entity = repository.save(new Entity());
entityManager.flush();
entityManager.clear();  // Clear first-level cache
Entity reloaded = repository.findById(entity.getId());
// reloaded !== entity (different object, from DB)
// Proves round-trip to database worked
```

---

## Real-World Example: Vote System

**Complete test class for planner voting constraints:**

```java
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class VoteConstraintTest {

    @Autowired private PlannerVoteRepository voteRepository;
    @Autowired private PlannerRepository plannerRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private EntityManager entityManager;

    private User testUser;
    private Planner testPlanner;

    @BeforeEach
    void setUp() {
        voteRepository.deleteAll();
        plannerRepository.deleteAll();
        userRepository.deleteAll();

        testUser = createTestUser();
        testPlanner = createTestPlanner(testUser);
        entityManager.flush();
        entityManager.clear();
    }

    @Test
    @DisplayName("UNIQUE: Duplicate vote throws exception")
    void duplicateVote_ThrowsException() {
        // WHY: Enforces one vote per user per planner (business rule)
        // PREVENTS: Vote manipulation, inflated vote counts

        // First vote succeeds
        PlannerVote vote1 = new PlannerVote(testUser.getId(), testPlanner.getId(), VoteType.UP);
        voteRepository.save(vote1);
        entityManager.flush();

        // Duplicate vote fails
        PlannerVote vote2 = new PlannerVote(testUser.getId(), testPlanner.getId(), VoteType.UP);
        assertThrows(DataIntegrityViolationException.class, () -> {
            voteRepository.save(vote2);
            entityManager.flush();
        });
    }

    @Test
    @DisplayName("FK: Invalid planner ID throws exception")
    void invalidPlanner_ThrowsException() {
        // WHY: Prevents votes on non-existent planners
        // PREVENTS: Orphaned votes, data corruption

        UUID invalidId = UUID.randomUUID();
        PlannerVote vote = new PlannerVote(testUser.getId(), invalidId, VoteType.UP);

        assertThrows(DataIntegrityViolationException.class, () -> {
            voteRepository.save(vote);
            entityManager.flush();
        });
    }

    @Test
    @DisplayName("FK: Invalid user ID throws exception")
    void invalidUser_ThrowsException() {
        // WHY: Prevents anonymous votes
        // BUSINESS RULE: Only authenticated users can vote

        Long invalidUserId = 999999L;
        PlannerVote vote = new PlannerVote(invalidUserId, testPlanner.getId(), VoteType.UP);

        assertThrows(DataIntegrityViolationException.class, () -> {
            voteRepository.save(vote);
            entityManager.flush();
        });
    }

    @Test
    @DisplayName("NOT NULL: Missing vote type throws exception")
    void missingVoteType_ThrowsException() {
        // WHY: Vote must have type (UP or DOWN)
        // PREVENTS: Ambiguous votes (what did user vote?)

        PlannerVote vote = new PlannerVote(testUser.getId(), testPlanner.getId(), null);

        assertThrows(DataIntegrityViolationException.class, () -> {
            voteRepository.save(vote);
            entityManager.flush();
        });
    }

    @Test
    @DisplayName("ON DELETE: Deleting planner cascades to votes")
    void deletePlanner_CascadesVotes() {
        // WHY: Documents cascade behavior
        // BUSINESS RULE: Deleting planner deletes associated votes

        // Create vote
        PlannerVote vote = new PlannerVote(testUser.getId(), testPlanner.getId(), VoteType.UP);
        voteRepository.save(vote);
        entityManager.flush();
        entityManager.clear();

        // Delete planner
        plannerRepository.deleteById(testPlanner.getId());
        entityManager.flush();

        // Vote should be deleted (CASCADE)
        assertEquals(0, voteRepository.count());

        // If this fails, cascade configuration is wrong
        // Expected: ON DELETE CASCADE in schema
    }
}
```

---

## Summary Checklist

When testing constraints, verify:

**Foreign Keys:**
- [ ] Non-existent parent reference throws exception
- [ ] Cascade DELETE behavior matches business rules
- [ ] RESTRICT prevents deletion when children exist
- [ ] Both directions: parent → child and child → parent

**UNIQUE Constraints:**
- [ ] Duplicate values throw exceptions
- [ ] Composite keys: Test all combinations
- [ ] Boundary cases: Same X + different Y succeeds
- [ ] Error message contains constraint name

**NOT NULL Constraints:**
- [ ] Missing required fields throw exceptions
- [ ] Empty strings vs NULL distinction tested
- [ ] Default values apply correctly
- [ ] FK + NOT NULL combination enforced

**CHECK Constraints:**
- [ ] Invalid values throw exceptions
- [ ] Boundary values (min/max) succeed
- [ ] Out-of-range values fail
- [ ] Business rule violations caught

**Testing Mechanics:**
- [ ] Use `entityManager.flush()` to force SQL execution
- [ ] Use `clear()` to invalidate cache before re-fetching
- [ ] Test passes throw exceptions (not silent failures)
- [ ] Error messages are inspected where applicable

**Next:** See `database-testing-02-query-correctness.md` for testing JPA query generation and N+1 problems.
