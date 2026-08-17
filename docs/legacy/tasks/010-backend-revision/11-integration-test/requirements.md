# Task: Implement Missing Backend Integration and Controller Tests

## Description

Implement comprehensive integration and controller test suites to close critical testing gaps in the backend application. Currently, the backend has strong unit test coverage (89% for services) but lacks HTTP-layer validation, with only 25% of controllers tested and 33% of repositories tested.

The implementation should create test suites for:

1. **CommentController** - Full CRUD operations, voting, authorization, and threading validation
2. **AuthController** - OAuth callback flow, token lifecycle (refresh, logout), cookie security
3. **PlannerSseService** - Real-time update broadcasting, emitter lifecycle, zombie cleanup
4. **NotificationController** - Inbox management, read status, soft-delete behavior
5. **AdminModerationController** - Admin-only planner hiding/unhiding with authorization checks
6. **Repository Custom Queries** - Atomic operations, soft-delete filtering, batch updates

Each test suite must verify:
- **Happy path**: Valid inputs with proper authentication produce expected results
- **Authorization failures**: Unauthenticated (401) and forbidden (403) scenarios
- **Validation failures**: Invalid inputs produce 400 errors with descriptive messages
- **Rate limiting**: Endpoint rate limits return 429 when exceeded
- **Edge cases**: Boundary conditions, concurrent access, idempotency
- **Error responses**: Exceptions map to correct HTTP status codes via GlobalExceptionHandler

### Key Behavioral Requirements

**CommentController Behaviors:**
- Create comment: Authenticated users can post top-level comments or replies on published planners
- Threading: Comments at depth=5 flatten to parent's level (max depth enforcement)
- Update/Delete: Only comment author can modify their comments (403 for non-authors)
- Upvoting: Immutable vote pattern - first vote succeeds, duplicate returns 409 Conflict
- Listing: Unauthenticated users can view comments on published planners, soft-deleted comments excluded
- Notifications: Creating comments triggers COMMENT_RECEIVED notification to planner owner, replies trigger REPLY_RECEIVED

**AuthController Behaviors:**
- OAuth callback: Exchange auth code for tokens, create new user with random username on first login
- Username collision: Retry with different suffix if generated username exists
- Reactivation: Soft-deleted users reactivate on login (deletedAt → null)
- Token refresh: Valid refresh token returns new access/refresh token pair, old tokens invalidated
- Logout: Blacklist both access and refresh tokens, clear cookies with Max-Age=0
- Cookie security: All tokens set with HttpOnly, Secure, SameSite=Lax flags

**PlannerSseService Behaviors:**
- Subscribe: Create SseEmitter for device, store in map, configure timeout and completion handlers
- Broadcasting: Send updates to all subscribed devices except source device
- Broken emitter cleanup: Remove emitters that throw IOException during send
- Unsubscribe: Remove emitter from map and complete it (idempotent)
- Zombie cleanup: Scheduled task sends test events to detect and remove unresponsive emitters
- Concurrent safety: Handle simultaneous subscribe/unsubscribe without map corruption

**Repository Custom Query Behaviors:**
- Atomic increments: upvoteCount increments without race conditions under concurrent access
- Decrement safety: decrementUpvoteCount never allows negative values
- Soft-delete filtering: findByPlannerId excludes comments where deletedAt != null
- Batch reassignment: reassignCommentsToSentinel updates all user's comments to sentinel userId=0

## Research

Before implementation, investigate:

1. **Existing Test Patterns**:
   - Read `CommentServiceTest.java` for @Nested organization, AAA pattern examples
   - Read `UserAccountLifecycleServiceTest.java` for in-order verification pattern
   - Read `VoteNotificationFlowTest.java` for concurrency testing with ExecutorService/CountDownLatch
   - Read `PlannerControllerTest.java` for MockMvc usage, JWT token generation

2. **Spring Test Framework**:
   - @SpringBootTest vs @ExtendWith(MockitoExtension.class) usage
   - @AutoConfigureMockMvc for HTTP testing
   - @ActiveProfiles("test") for H2 database configuration
   - @Transactional for automatic test isolation/rollback

3. **MockMvc Request Patterns**:
   - How to send authenticated requests with JWT in Authorization header
   - How to assert JSON response structure with jsonPath()
   - How to verify HTTP status codes and error messages
   - How to send POST/PUT requests with JSON bodies

4. **Test Data Management**:
   - Current @BeforeEach setup patterns (deleteAll() cleanup)
   - User creation with UserRepository.save()
   - JWT token generation via JwtService.generateAccessToken()
   - Planner creation with published=true for public endpoints

5. **Mocking External Services**:
   - @MockBean usage for GoogleOAuthService in OAuth tests
   - How to stub OAuth token exchange and user info retrieval
   - Preventing real external API calls during tests

6. **Concurrency Testing**:
   - ExecutorService with fixed thread pool for parallel execution
   - CountDownLatch for synchronized start of concurrent operations
   - Proper cleanup with executor.shutdown() and awaitTermination()

## Scope

**Files to READ for context:**

Backend Test Infrastructure:
- `backend/src/test/java/org/danteplanner/backend/config/TestConfig.java` - H2 configuration
- `backend/src/test/resources/application-test.properties` - Test database settings
- `backend/pom.xml` - Test dependencies (JUnit 5, Mockito, Spring Test, H2)

Existing Test Examples:
- `backend/src/test/java/org/danteplanner/backend/service/CommentServiceTest.java` - Service unit test pattern
- `backend/src/test/java/org/danteplanner/backend/service/UserAccountLifecycleServiceTest.java` - Complex lifecycle testing
- `backend/src/test/java/org/danteplanner/backend/integration/VoteNotificationFlowTest.java` - Concurrency integration test
- `backend/src/test/java/org/danteplanner/backend/controller/PlannerControllerTest.java` - Controller test with MockMvc
- `backend/src/test/java/org/danteplanner/backend/controller/UserControllerTest.java` - Another controller example
- `backend/src/test/java/org/danteplanner/backend/repository/PlannerRepositoryTest.java` - Repository test pattern

Controllers to Test:
- `backend/src/main/java/org/danteplanner/backend/controller/CommentController.java`
- `backend/src/main/java/org/danteplanner/backend/controller/AuthController.java`
- `backend/src/main/java/org/danteplanner/backend/controller/NotificationController.java`
- `backend/src/main/java/org/danteplanner/backend/controller/AdminModerationController.java`

Services to Test:
- `backend/src/main/java/org/danteplanner/backend/service/PlannerSseService.java`
- `backend/src/main/java/org/danteplanner/backend/service/GoogleOAuthService.java` (review for mocking)

Repositories to Test:
- `backend/src/main/java/org/danteplanner/backend/repository/PlannerCommentRepository.java`
- `backend/src/main/java/org/danteplanner/backend/repository/PlannerCommentVoteRepository.java`
- `backend/src/main/java/org/danteplanner/backend/repository/NotificationRepository.java`

Supporting Components:
- `backend/src/main/java/org/danteplanner/backend/service/JwtService.java` - Token generation for tests
- `backend/src/main/java/org/danteplanner/backend/exception/GlobalExceptionHandler.java` - Error mapping
- `backend/src/main/java/org/danteplanner/backend/config/RateLimitConfig.java` - Rate limit bucket configuration

## Target Code Area

**NEW test files to CREATE:**

Controllers:
- `backend/src/test/java/org/danteplanner/backend/controller/CommentControllerTest.java`
- `backend/src/test/java/org/danteplanner/backend/controller/AuthControllerTest.java`
- `backend/src/test/java/org/danteplanner/backend/controller/NotificationControllerTest.java`
- `backend/src/test/java/org/danteplanner/backend/controller/AdminModerationControllerTest.java`

Services:
- `backend/src/test/java/org/danteplanner/backend/service/PlannerSseServiceTest.java`

Repositories:
- `backend/src/test/java/org/danteplanner/backend/repository/PlannerCommentRepositoryTest.java`
- `backend/src/test/java/org/danteplanner/backend/repository/NotificationRepositoryTest.java`

Test Utilities:
- `backend/src/test/java/org/danteplanner/backend/test/TestDataFactory.java` - User/Planner/Comment builders
- `backend/src/test/java/org/danteplanner/backend/test/JwtTestUtil.java` - Token generation helpers

Documentation:
- `backend/TESTING.md` - Testing guidelines and conventions

## System Context (Senior Thinking)

**Feature domains from architecture-map:**
- Comment System: `CommentService.java`, `CommentController.java` (core files)
- Authentication: `AuthController.java`, `JwtService.java`, `GoogleOAuthService.java` (core files)
- Notification System: `NotificationService.java`, `NotificationController.java` (core files)
- Moderation System: `ModerationService.java`, `AdminModerationController.java` (core files)
- Planner CRUD: `PlannerSseService.java` (supporting file for real-time updates)

**Cross-cutting concerns touched:**
- Exception Handling: `GlobalExceptionHandler.java` maps exceptions to HTTP status codes (tested indirectly)
- Security: Authentication/authorization checks in controllers
- Rate Limiting: `RateLimitConfig.java` (Bucket4j) enforces endpoint limits
- Validation: Jakarta Validation annotations in DTOs (tested via HTTP layer)
- Database: JPA repositories with custom atomic operations

**Test infrastructure concerns:**
- H2 in-memory database: Fast, isolated, MySQL compatibility mode
- Test profiles: `@ActiveProfiles("test")` loads `application-test.properties`
- Transaction management: `@Transactional` auto-rolls back after each test
- Security context: Tests must manually set authentication for secured endpoints

## Impact Analysis

**Files being modified/created:**

HIGH IMPACT (many dependencies):
- NEW `CommentControllerTest.java` - Validates HTTP layer for comment system (untested critical path)
- NEW `AuthControllerTest.java` - Validates OAuth flow, token lifecycle (untested critical path)
- NEW `PlannerSseServiceTest.java` - Validates real-time sync, prevents memory leaks (untested critical path)

MEDIUM IMPACT (shared utilities):
- NEW `TestDataFactory.java` - Reduces duplication across tests (all test files will use)
- NEW `JwtTestUtil.java` - Token generation helper (all controller tests will use)
- NEW `backend/TESTING.md` - Guidelines for future test development (documentation)

LOW IMPACT (isolated tests):
- NEW `NotificationControllerTest.java` - Tests isolated notification endpoints
- NEW `AdminModerationControllerTest.java` - Tests isolated admin endpoints
- NEW `PlannerCommentRepositoryTest.java` - Tests repository queries
- NEW `NotificationRepositoryTest.java` - Tests repository queries

**What depends on these tests:**
- CI/CD pipeline: Tests must pass before deployment
- Code review: New features require corresponding tests
- Regression prevention: Tests catch breaking changes in refactoring
- Documentation: Tests serve as behavioral documentation

**Potential ripple effects:**
- Controller tests may reveal bugs in existing error handling
- Integration tests may expose race conditions not caught by unit tests
- Repository tests may discover incorrect custom query implementations
- Test factories will standardize test data creation patterns

**High-impact files to watch:**
- `GlobalExceptionHandler.java` - If error mappings are wrong, controller tests will fail
- `SecurityConfig.java` - If auth configuration changes, all controller tests may break
- `RateLimitConfig.java` - If rate limits change, rate limit tests need updates
- `JwtService.java` - If token format changes, token generation in tests must adapt

## Risk Assessment

**Edge cases not yet defined:**
- Comment threading: What happens if parent comment is deleted? (Should replies remain visible?)
- OAuth callback: What if Google returns duplicate email across providers?
- SSE emitters: What if client subscribes multiple times with same deviceId?
- Rate limiting: Should rate limits be per-user or per-IP? (Currently per-IP via ClientIpResolver)
- Notification deduplication: UNIQUE constraint enforces DB-level, but what about race conditions?

**Performance concerns:**
- Concurrency tests: 10-second timeout in VoteNotificationFlowTest may be insufficient on slow CI/CD
- SSE zombie cleanup: Scheduled task sends test events to all emitters - could be slow with many devices
- Repository batch operations: reassignCommentsToSentinel updates all user's comments - test with large datasets
- MockMvc tests: Full Spring context startup takes 5-10 seconds per test class

**Backward compatibility:**
- Vote immutability: Tests must verify 409 on duplicate vote (breaking change from previous toggle behavior)
- Cookie security flags: Tests verify HttpOnly/Secure/SameSite (changes could break existing clients)
- Error response format: Tests lock in error JSON structure (changes would break frontend parsing)

**Security considerations:**
- Authorization testing: CRITICAL - ensures only authorized users can perform actions
- Token blacklist: Tests must verify logged-out tokens are rejected (session hijacking prevention)
- Rate limiting: Tests ensure abuse prevention works at HTTP layer
- Input validation: Tests verify SQL injection, XSS prevention via Jakarta Validation
- CSRF: Currently not tested - may need future coverage if CSRF protection added

**Testing infrastructure risks:**
- H2 vs MySQL differences: Some MySQL-specific behaviors may not match (e.g., AUTO_INCREMENT gaps)
- Concurrent test execution: Tests use `@Transactional` which is single-threaded - parallel execution could break
- Test data cleanup: Manual `deleteAll()` in @BeforeEach - risk of leftover data if test fails before cleanup
- External service mocking: @MockBean for GoogleOAuthService - must stay in sync with real OAuth behavior

## Testing Guidelines

### Manual UI Testing

**Not applicable** - This task focuses on backend integration/controller tests which are executed via Maven and IDE runners, not through UI interaction. Tests validate HTTP endpoints programmatically using MockMvc.

### Automated Functional Verification

**CommentController (Priority 1):**
- [ ] **Create top-level comment**: Authenticated user POSTs to `/api/planner/{id}/comments`, receives 200 with depth=0
- [ ] **Create reply**: Authenticated user POSTs with `parentCommentId`, receives 200 with depth=parent.depth+1
- [ ] **Depth=5 flattening**: Reply to depth=4 comment has depth=5 and parent's parentId (not depth=6)
- [ ] **Unauthorized creation**: Unauthenticated POST returns 401
- [ ] **Update own comment**: Author PUTs `/api/comments/{id}`, content updates, receives 200
- [ ] **Update other's comment**: Non-author PUT returns 403 (CommentForbiddenException)
- [ ] **Delete own comment**: Author DELETEs `/api/comments/{id}`, receives 204, comment soft-deleted
- [ ] **Delete other's comment**: Non-author DELETE returns 403
- [ ] **List comments (public)**: Unauthenticated GET `/api/planner/{id}/comments` returns all non-deleted comments
- [ ] **List comments (unpublished)**: GET on unpublished planner returns 404
- [ ] **Upvote comment (first)**: POST `/api/comments/{id}/upvote` increments upvoteCount, returns 200
- [ ] **Upvote duplicate**: Second upvote returns 409 (VoteAlreadyExistsException)
- [ ] **Rate limit**: 11th comment POST in 1 minute returns 429 (Too Many Requests)
- [ ] **Validation**: Empty content returns 400 with validation error

**AuthController (Priority 2):**
- [ ] **OAuth new user**: POST `/api/auth/callback` with valid code creates user, sets cookies, returns 200
- [ ] **OAuth existing user**: Callback for existing user returns 200, no new user created, lastLoginAt updated
- [ ] **Username collision**: Generated username exists → service retries with different suffix, succeeds
- [ ] **Deleted user reactivation**: Callback for soft-deleted user sets deletedAt=null, succeeds
- [ ] **Invalid auth code**: Callback with invalid code returns 401
- [ ] **Cookie security**: access_token and refresh_token cookies have HttpOnly, Secure, SameSite=Lax flags
- [ ] **Token refresh**: POST `/api/auth/refresh-token` with valid token returns new tokens, old invalidated
- [ ] **Refresh expired**: Expired refresh token returns 401
- [ ] **Refresh blacklisted**: Blacklisted token returns 401 ("Token has been revoked")
- [ ] **Logout**: POST `/api/auth/logout` blacklists tokens, clears cookies (Max-Age=0), returns 204
- [ ] **Use after logout**: Request with blacklisted token returns 401

**PlannerSseService (Priority 3):**
- [ ] **Subscribe creates emitter**: subscribe(plannerId, deviceId) returns SseEmitter, stored in map
- [ ] **Subscribe replaces old**: Second subscribe with same deviceId completes old emitter, stores new one
- [ ] **Multiple devices**: Three different deviceIds → three separate emitters for same planner
- [ ] **Broadcast excludes source**: notifyUpdate(plannerId, sourceDeviceId) sends to all except source
- [ ] **Broadcast with no subscribers**: No errors thrown, no emitters attempted
- [ ] **Broken emitter cleanup**: Emitter throwing IOException removed from map during broadcast
- [ ] **Unsubscribe removes**: unsubscribe(plannerId, deviceId) removes emitter, completes it
- [ ] **Unsubscribe idempotent**: Unsubscribe non-existent device → no errors
- [ ] **Timeout cleanup**: Emitter timeout invokes onTimeout, emitter removed from map
- [ ] **Completion cleanup**: Emitter completion invokes onCompletion, emitter removed
- [ ] **Zombie cleanup scheduler**: cleanupZombieEmitters() sends test events, removes unresponsive emitters

**NotificationController (Priority 4):**
- [ ] **Inbox listing**: GET `/api/notifications/inbox` returns user's notifications, paginated
- [ ] **Inbox excludes deleted**: Soft-deleted notifications (deletedAt != null) not in response
- [ ] **Inbox authorization**: User can only see own notifications (not other users')
- [ ] **Unread count**: GET `/api/unread-count` returns count where read=false
- [ ] **Mark read**: POST `/api/notifications/{id}/mark-read` sets read=true, returns 200
- [ ] **Delete notification**: DELETE `/api/notifications/{id}` soft-deletes (sets deletedAt), returns 204
- [ ] **Unauthorized access**: Unauthenticated requests return 401

**AdminModerationController (Priority 4):**
- [ ] **Hide planner (admin)**: Admin POST `/api/admin/planner/{id}/hide` sets hiddenFromRecommended=true, returns 200
- [ ] **Hide planner (non-admin)**: Non-admin POST returns 403 (Forbidden)
- [ ] **Unhide planner (admin)**: Admin POST `/api/admin/planner/{id}/unhide` sets hiddenFromRecommended=false
- [ ] **Unhide planner (non-admin)**: Non-admin POST returns 403
- [ ] **Hide reason required**: Hide without reason (or <10 chars) returns 400

**Repository Custom Queries (Priority 5):**
- [ ] **Atomic increment**: incrementUpvoteCount() increments by 1, no race conditions
- [ ] **Concurrent increments**: 10 parallel calls → final upvoteCount=10 (no lost updates)
- [ ] **Decrement safety**: decrementUpvoteCount() on upvoteCount=0 → remains 0 (never negative)
- [ ] **Soft-delete filtering**: findByPlannerId() excludes comments where deletedAt != null
- [ ] **Top-level only**: findByPlannerIdAndParentIsNull() returns depth=0 comments only
- [ ] **Batch reassignment**: reassignCommentsToSentinel(userId, 0) updates all user's comments to authorId=0

### Edge Cases

**Comment Threading:**
- [ ] **Max depth boundary**: Reply to depth=4 comment results in depth=5, not depth=6
- [ ] **Flattening parent**: Depth=5 comment uses parent's parentId (becomes sibling of depth=4 comment)
- [ ] **Orphaned replies**: If parent comment soft-deleted, replies remain visible (deletedAt check only)

**OAuth Flow:**
- [ ] **Concurrent user creation**: Two simultaneous callbacks for same new user → one succeeds, one gets existing
- [ ] **Username generation retries**: Up to 5 collision retries before failure (or infinite loop prevention)
- [ ] **Deleted user edge**: User deleted, then immediately tries to login → reactivation succeeds

**SSE Emitters:**
- [ ] **Duplicate deviceId**: Second subscribe with same deviceId completes old emitter before storing new
- [ ] **Broken emitter during broadcast**: IOException on one emitter doesn't prevent others from receiving update
- [ ] **Concurrent subscribe/unsubscribe**: Thread-safe map operations (no ConcurrentModificationException)

**Voting:**
- [ ] **Vote immutability**: Vote exists → POST returns 409, upvoteCount unchanged
- [ ] **Concurrent duplicate votes**: Two parallel votes from same user → one succeeds (200), one conflicts (409)
- [ ] **Vote on deleted comment**: Returns 404 (CommentNotFoundException)

**Rate Limiting:**
- [ ] **Rate limit per-IP**: Requests from different IPs have separate limits
- [ ] **Rate limit reset**: After time window, bucket refills and allows more requests
- [ ] **Rate limit bucket sizes**: Comment creation (10/min), voting (varies per config)

**Error Handling:**
- [ ] **Malformed JSON**: POST with invalid JSON returns 400 (Bad Request)
- [ ] **Missing required fields**: DTO validation fails, returns 400 with field-specific errors
- [ ] **Non-existent resources**: GET/PUT/DELETE non-existent ID returns 404

### Integration Points

**Notification Integration:**
- [ ] **Comment → Notification**: Creating comment triggers COMMENT_RECEIVED notification to planner owner
- [ ] **Reply → Notification**: Creating reply triggers REPLY_RECEIVED notification to parent comment author
- [ ] **Vote threshold → Notification**: Planner net votes crossing 10 triggers PLANNER_RECOMMENDED notification
- [ ] **Notification uniqueness**: UNIQUE constraint (userId, contentId, type) prevents duplicate notifications

**User Deletion Integration:**
- [ ] **Comment reassignment**: User deletion → comments reassigned to sentinel user (id=0)
- [ ] **Vote reassignment**: User deletion → votes reassigned to sentinel user
- [ ] **Notification cleanup**: User deletion → notifications soft-deleted (deletedAt set)

**Authorization Integration:**
- [ ] **JWT filter**: JwtAuthenticationFilter extracts userId from token, sets SecurityContext
- [ ] **Method security**: @PreAuthorize("hasRole('ADMIN')") enforced on admin endpoints
- [ ] **Ownership checks**: Service layer verifies userId matches resource owner (comments, planners)

**Data Consistency:**
- [ ] **Transaction rollback**: Validation failure rolls back entire transaction (no partial saves)
- [ ] **Atomic counters**: upvoteCount increments are atomic (UPDATE ... SET upvoteCount = upvoteCount + 1)
- [ ] **Optimistic locking**: @Version fields prevent lost updates (if used)

---

## Implementation Checklist

**Week 1: Critical Controllers**
- [ ] Create CommentControllerTest with 20+ test scenarios
- [ ] Create AuthControllerTest with 15+ test scenarios
- [ ] Verify all CommentController tests pass
- [ ] Verify all AuthController tests pass

**Week 2: Infrastructure & Services**
- [ ] Create PlannerSseServiceTest with 15+ test scenarios
- [ ] Create NotificationControllerTest with 10+ test scenarios
- [ ] Create TestDataFactory utility class
- [ ] Create JwtTestUtil helper class
- [ ] Write backend/TESTING.md guidelines document

**Week 3: Repositories & Edge Cases**
- [ ] Create PlannerCommentRepositoryTest with custom query tests
- [ ] Create NotificationRepositoryTest with soft-delete filtering tests
- [ ] Create AdminModerationControllerTest with authorization tests
- [ ] Add edge case tests (concurrency, boundaries, error conditions)
- [ ] Run full test suite 3 consecutive times (verify no flaky tests)

**Final Acceptance:**
- [ ] All 100+ new tests pass in < 60 seconds
- [ ] Controller coverage increases from 25% to 80%+
- [ ] Repository coverage increases from 33% to 70%+
- [ ] No flaky tests (3 consecutive runs all green)
- [ ] backend/TESTING.md reviewed and approved
- [ ] Test factories eliminate duplicated setup code
