# Research: Backend Integration & Controller Tests

## Clarifications Resolved

**1. Orphaned Comment Replies:**
- **Decision**: Keep replies visible when parent is soft-deleted
- **Implication**: Test must verify `findByPlannerId()` returns replies even when parent has `deletedAt != null`
- **UI Impact**: Replies show but parent appears as [deleted] placeholder

**2. Username Generation Retry Limit:**
- **Decision**: Throw exception after max retries (5 attempts)
- **Exception Type**: `UsernameGenerationException` (500 Internal Server Error)
- **Test**: AuthControllerTest must verify 500 response when collision retries exhausted

**3. SSE Duplicate Device Handling:**
- **Decision**: Complete old emitter before storing new one
- **Implementation**: `subscribe(deviceId)` must find existing emitter in list, call `complete()`, remove it, then add new
- **Test**: PlannerSseServiceTest must verify only one active emitter per deviceId after duplicate subscribe

---

## Spec-to-Code Mapping

### Controllers (HTTP Layer - CRITICAL GAPS)

**CommentController** → `backend/.../controller/CommentController.java`
- **Status**: Implementation exists, NO tests
- **Tests Needed**: CommentControllerTest.java (20+ scenarios)
- **Key Endpoints**: POST create, PUT update, DELETE, GET list, POST upvote
- **Auth**: All except GET list require JWT authentication
- **Edge Cases**: Threading depth=5 flattening, vote immutability (409), orphaned replies

**AuthController** → `backend/.../controller/AuthController.java`
- **Status**: Implementation exists, NO tests
- **Tests Needed**: AuthControllerTest.java (15+ scenarios)
- **Key Endpoints**: POST /callback, POST /refresh-token, POST /logout
- **Mocking**: @MockBean for GoogleOAuthService (prevent real OAuth calls)
- **Edge Cases**: Username collision retries → 500, soft-deleted user reactivation

**NotificationController** → `backend/.../controller/NotificationController.java`
- **Status**: Implementation exists, NO tests
- **Tests Needed**: NotificationControllerTest.java (10+ scenarios)
- **Key Endpoints**: GET /inbox, GET /unread-count, POST /mark-read, DELETE
- **Auth**: All require JWT, user can only access own notifications

**AdminModerationController** → `backend/.../controller/AdminModerationController.java`
- **Status**: Implementation exists, NO tests
- **Tests Needed**: AdminModerationControllerTest.java (5+ scenarios)
- **Key Endpoints**: POST /hide, POST /unhide
- **Auth**: @PreAuthorize("hasRole('ADMIN')") - must test 403 for non-admin

### Services (Concurrency & Lifecycle - MISSING)

**PlannerSseService** → `backend/.../service/PlannerSseService.java`
- **Status**: Implementation exists, NO tests
- **Tests Needed**: PlannerSseServiceTest.java (15+ scenarios)
- **Key Methods**: subscribe(), unsubscribe(), notifyUpdate(), cleanupZombieEmitters()
- **Concurrency**: Must test thread-safe map operations (subscribe while broadcasting)
- **Edge Case**: Duplicate deviceId → complete old emitter before adding new

### Repositories (Custom Queries - GAPS)

**PlannerCommentRepository** → `backend/.../repository/PlannerCommentRepository.java`
- **Status**: Implementation exists, PARTIAL tests (only in service layer with mocks)
- **Tests Needed**: PlannerCommentRepositoryTest.java with @DataJpaTest
- **Custom Methods**: incrementUpvoteCount(), decrementUpvoteCount(), findByPlannerId(), reassignCommentsToSentinel()
- **Atomicity**: Test concurrent increments (10 threads → upvoteCount=10)

**NotificationRepository** → `backend/.../repository/NotificationRepository.java`
- **Status**: Implementation exists, NO tests
- **Tests Needed**: NotificationRepositoryTest.java with @DataJpaTest
- **Custom Methods**: findByUserIdAndDeletedAtIsNull(), soft-delete queries
- **Constraints**: UNIQUE(userId, contentId, notificationType) prevents duplicates

---

## Pattern Enforcement

### Must Read Before Writing Tests

| New Test File | Reference Pattern | Key Patterns to Copy |
|---------------|------------------|---------------------|
| CommentControllerTest | PlannerControllerTest (lines 48-300) | @SpringBootTest, @AutoConfigureMockMvc, @Transactional, MockMvc setup, JWT token generation |
| CommentControllerTest | CommentServiceTest (lines 37-91) | @Nested organization, @DisplayName, AAA pattern |
| AuthControllerTest | PlannerControllerTest + UserControllerTest | MockMvc, cookie assertions (Set-Cookie headers), @MockBean for OAuth |
| PlannerSseServiceTest | VoteNotificationFlowTest (lines 24-298) | ExecutorService for concurrency, CountDownLatch synchronization, entityManager.flush() |
| NotificationControllerTest | PlannerControllerTest | MockMvc, authorization checks, pagination assertions |
| AdminModerationControllerTest | PlannerControllerTest | @PreAuthorize testing, 403 Forbidden verification |
| PlannerCommentRepositoryTest | PlannerRepositoryTest (lines 1-150) | @DataJpaTest, TestEntityManager, atomic operation verification |
| NotificationRepositoryTest | PlannerRepositoryTest | @DataJpaTest, soft-delete filtering assertions |

### Test Utility Pattern Sources

**TestDataFactory.java** (NEW - centralize test data builders):
- Pattern from: CommentServiceTest.java (lines 72-100) user/planner builders
- Pattern from: PlannerControllerTest.java (lines 83-113) builder pattern with Lombok
- Purpose: Eliminate duplicated setup code across all test classes

**JwtTestUtil.java** (NEW - token generation helper):
- Pattern from: PlannerControllerTest.java (line 62) token generation
- Methods needed: generateTokenForUser(userId), generateExpiredToken(userId), generateAdminToken(userId)
- Purpose: Consistent JWT creation for authenticated endpoint testing

---

## Pattern Copy Deep Analysis

### Reference: PlannerControllerTest.java

**Total Lines**: ~300
**Dependencies**:
- @Autowired MockMvc, PlannerRepository, UserRepository, JwtService
- @ActiveProfiles("test") for H2 database
- @Transactional for automatic rollback

**Test Structure**:
- @BeforeEach: deleteAll() cleanup, create test users/planners, generate JWT tokens
- Test methods: Use `mockMvc.perform(get/post/put/delete)` with `.header("Authorization", "Bearer " + token)`
- Assertions: `.andExpect(status().isOk())`, `.andExpect(jsonPath("$.field").value(expected))`

**Key Patterns**:
```
// Authentication pattern
.header("Authorization", "Bearer " + testUserToken)

// JSON body pattern
.contentType(APPLICATION_JSON)
.content("{\"field\": \"value\"}")

// Response assertions
.andExpect(status().isOk())
.andExpect(jsonPath("$.id").value(123))
.andExpect(jsonPath("$.content").value("expected"))

// Error assertions
.andExpect(status().isForbidden())
.andExpect(jsonPath("$.message").exists())
```

**What to Replicate**:
- Exact @BeforeEach cleanup order: deleteAll() for all affected repositories
- JWT token generation via JwtService.generateAccessToken(userId)
- MockMvc request builder pattern with headers and JSON content
- Status code + JSON path assertions for success and error cases

---

### Reference: VoteNotificationFlowTest.java

**Total Lines**: ~298
**Dependencies**:
- @SpringBootTest (full context), @Transactional, @Autowired EntityManager
- ExecutorService, CountDownLatch for concurrency testing

**Concurrency Pattern**:
```
ExecutorService executor = Executors.newFixedThreadPool(5);
CountDownLatch latch = new CountDownLatch(1);
// Submit tasks that wait on latch
latch.countDown(); // Start all threads simultaneously
executor.shutdown();
executor.awaitTermination(10, TimeUnit.SECONDS);
```

**Transaction Boundary Testing**:
```
service.performAction();
entityManager.flush();  // Force write to DB
entityManager.clear();  // Clear L1 cache
// Now query reflects actual DB state
```

**What to Replicate**:
- ExecutorService with fixed thread pool for concurrent operations
- CountDownLatch to synchronize thread start (test race conditions)
- entityManager.flush()/clear() to verify persistence beyond cache

---

### Reference: CommentServiceTest.java

**Total Lines**: ~400
**Dependencies**:
- @ExtendWith(MockitoExtension.class) for unit testing
- @Mock for all repository dependencies
- Constructor injection in @BeforeEach

**Nested Test Organization**:
```
@Nested
@DisplayName("createComment Tests")
class CreateCommentTests {
    @Test
    @DisplayName("Creates top-level comment successfully")
    void createComment_WhenTopLevel_Succeeds() { ... }
}
```

**AAA Pattern**:
```
// Arrange
when(repository.findById(id)).thenReturn(Optional.of(entity));

// Act
Result result = service.method(params);

// Assert
assertEquals(expected, result);
verify(repository).save(any());
```

**What NOT to Replicate in Controller Tests**:
- @ExtendWith(MockitoExtension.class) - use @SpringBootTest instead
- @Mock repositories - use real repositories with H2
- Constructor injection - use @Autowired field injection

**What TO Replicate**:
- @Nested organization for logical test grouping
- @DisplayName for readable test names
- AAA pattern with clear section comments
- verify() for interaction assertions (in service tests, not controller tests)

---

## Existing Utilities Analysis

### Test Configuration (Already Exists)

**application-test.properties** (lines 1-55):
- H2 in-memory database: `jdbc:h2:mem:testdb`
- MySQL compatibility mode: `MODE=MySQL`
- Hibernate DDL: `spring.jpa.hibernate.ddl-auto=create-drop`
- SQL logging: `spring.jpa.show-sql=true`

**TestConfig.java**:
- Provides Jackson ObjectMapper bean override
- Custom JSON serialization for tests

### Test Data Patterns (Scattered - Need Centralization)

**Current User Creation** (duplicated across tests):
- CommentServiceTest: Lines 72-78 (User.builder pattern)
- PlannerControllerTest: Lines 83-90 (User.builder pattern)
- VoteNotificationFlowTest: Lines 120-135 (User.builder pattern)

**Solution**: Create TestDataFactory.java with static methods:
- `createTestUser(email, keyword)` → User
- `createTestPlanner(owner, category, published)` → Planner
- `createTestComment(planner, author, parent)` → PlannerComment
- `createTestNotification(user, type, contentId)` → Notification

**Current JWT Generation** (duplicated):
- PlannerControllerTest: Line 62 `jwtService.generateAccessToken(userId)`
- Need to replicate in every controller test

**Solution**: Create JwtTestUtil.java with:
- `generateTokenForUser(userId)` → String
- `generateExpiredToken(userId)` → String (for testing 401)
- `generateAdminToken(userId)` → String (for testing @PreAuthorize)

---

## Gap Analysis

### Currently Missing (NEW FILES NEEDED)

**Controller Tests** (0% coverage → target 80%):
- CommentControllerTest.java (20+ scenarios)
- AuthControllerTest.java (15+ scenarios)
- NotificationControllerTest.java (10+ scenarios)
- AdminModerationControllerTest.java (5+ scenarios)

**Service Tests** (SSE not tested):
- PlannerSseServiceTest.java (15+ scenarios)

**Repository Tests** (custom queries not tested):
- PlannerCommentRepositoryTest.java (atomic ops, soft-delete)
- NotificationRepositoryTest.java (UNIQUE constraint, soft-delete)

**Test Utilities** (reduce duplication):
- TestDataFactory.java (builders for User, Planner, Comment, Notification)
- JwtTestUtil.java (token generation for authenticated tests)

**Documentation**:
- backend/TESTING.md (conventions, when to use @SpringBootTest vs @DataJpaTest)

### Can Reuse (EXISTING INFRASTRUCTURE)

**Test Frameworks**:
- JUnit 5 (via spring-boot-starter-test)
- Mockito (via MockitoExtension)
- Spring Test (MockMvc, @SpringBootTest, @DataJpaTest)
- H2 database (test scope)

**Test Patterns**:
- PlannerControllerTest: MockMvc + authentication + JSON assertions
- CommentServiceTest: @Nested + AAA + verification
- VoteNotificationFlowTest: Concurrency + transaction boundaries
- PlannerRepositoryTest: @DataJpaTest + custom query testing

**Test Configuration**:
- application-test.properties (H2 + MySQL mode)
- TestConfig.java (ObjectMapper override)
- @ActiveProfiles("test") annotation

---

## Testing Requirements Summary

### Integration Tests (Full Spring Context + Real H2 DB)

**Controllers** (HTTP boundary validation):
- MockMvc: Simulate HTTP requests with headers, JSON bodies, cookies
- Authentication: JWT tokens in Authorization header
- Authorization: Verify 403 for non-owners, non-admins
- Validation: Verify 400 for invalid DTOs (empty fields, wrong types)
- Rate Limiting: Verify 429 after bucket exhausted
- Error Mapping: Verify GlobalExceptionHandler maps exceptions to correct status codes

**Services** (Concurrency & lifecycle):
- SSE: Emitter subscription, broadcasting, timeout cleanup, zombie removal
- Concurrency: ExecutorService to test race conditions (vote threshold, duplicate subscribes)
- Transaction boundaries: entityManager.flush() to verify DB persistence

### Unit Tests (Isolated, Already Exist)

**Services** (business logic, already tested):
- CommentService: CRUD logic, threading, notifications
- UserAccountLifecycleService: Deletion, reactivation, hard delete
- NotificationService: Creation, deduplication, cleanup

**Repositories** (Custom queries, MISSING):
- Atomic operations: incrementUpvoteCount, decrementUpvoteCount (verify no race conditions)
- Soft-delete filtering: findByPlannerId excludes deletedAt != null
- Batch updates: reassignCommentsToSentinel updates all user's comments
- UNIQUE constraints: Notification deduplication on (userId, contentId, type)

---

## Technical Constraints

### H2 vs MySQL Differences (LOW RISK)
- AUTO_INCREMENT gaps: H2 may differ from MySQL sequence behavior
- SET type: MySQL SET('A','B') not directly supported in H2, uses VARCHAR converter
- Impact: Tests pass on H2 but edge cases may differ in production MySQL

### Concurrency Testing (MEDIUM RISK)
- @Transactional: Single-threaded isolation, parallel test execution may break
- ExecutorService timeout: 10-second timeout in VoteNotificationFlowTest may be insufficient on slow CI
- Map operations: PlannerSseService uses ConcurrentHashMap + CopyOnWriteArrayList - test thread safety

### External Service Mocking (REQUIRED)
- GoogleOAuthService: Must use @MockBean to prevent real API calls during AuthController tests
- OAuth responses: Mock token exchange, user info retrieval with stubbed data
- Risk: Mocks must stay in sync with real OAuth API contract

### Test Data Cleanup (LOW RISK)
- Manual deleteAll() in @BeforeEach: Works but verbose
- @Transactional auto-rollback: Cleaner, but requires @Transactional on test class
- Risk: If test fails before cleanup, next test may see leftover data (mitigated by @Transactional)

### Rate Limiting State (LOW RISK)
- Bucket4j buckets persist across tests in same Spring context
- Must reset rate limit state in @BeforeEach if testing multiple rate limit scenarios
- Alternative: Use separate test class for rate limit tests (fresh Spring context)

---

## Implementation Priority

**Week 1 (CRITICAL PATH)**:
1. TestDataFactory.java + JwtTestUtil.java (foundations)
2. CommentControllerTest.java (20+ scenarios, highest risk)
3. AuthControllerTest.java (15+ scenarios, auth bypass prevention)

**Week 2 (HIGH PRIORITY)**:
4. PlannerSseServiceTest.java (15+ scenarios, memory leak prevention)
5. NotificationControllerTest.java (10+ scenarios)
6. backend/TESTING.md (document conventions)

**Week 3 (MEDIUM PRIORITY)**:
7. AdminModerationControllerTest.java (5+ scenarios)
8. PlannerCommentRepositoryTest.java (atomic ops, soft-delete)
9. NotificationRepositoryTest.java (UNIQUE constraint)

**Acceptance Criteria**:
- All 100+ new tests pass in < 60 seconds
- Controller coverage: 25% → 80%+
- Repository coverage: 33% → 70%+
- No flaky tests (3 consecutive full suite runs)
