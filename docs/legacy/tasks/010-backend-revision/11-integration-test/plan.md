# Execution Plan: Backend Integration & Controller Tests

## Planning Gaps

**NONE** - Research has clarified:
- Orphaned replies remain visible when parent soft-deleted
- Username generation retries max 5 times, then throws 500
- SSE duplicate deviceId completes old emitter before adding new
- All test patterns identified in existing code

Proceeding with plan.

---

## Execution Overview

This plan implements comprehensive backend integration and controller tests to close critical testing gaps. The strategy prioritizes high-risk critical paths (authentication, comments) first, then infrastructure (SSE, utilities), and finally isolated components (repositories, admin endpoints).

The execution follows a **foundation-first** approach: create reusable test utilities before controller tests to eliminate duplication and establish consistent patterns. Tests validate HTTP layer contracts, concurrency safety, and security boundaries that unit tests cannot cover.

**Key milestones**:
1. Week 1: Foundation + Critical Controllers (CommentController, AuthController)
2. Week 2: Infrastructure + Services (SSE, Notifications, Test Utilities)
3. Week 3: Repositories + Edge Cases + Documentation

---

## Dependency Analysis (Senior Thinking)

### Files Being Modified

| File | Impact Level | Depends On | Used By |
|------|--------------|------------|---------|
| **NEW** TestDataFactory.java | High | UserRepository, PlannerRepository, PlannerCommentRepository, NotificationRepository | All test files (eliminates duplication) |
| **NEW** JwtTestUtil.java | High | JwtTokenService | All controller tests (authenticated requests) |
| **NEW** CommentControllerTest.java | High | TestDataFactory, JwtTestUtil, MockMvc, CommentService, NotificationService | CI/CD pipeline, regression prevention |
| **NEW** AuthControllerTest.java | High | TestDataFactory, JwtTestUtil, MockMvc, GoogleOAuthService (mocked), AuthenticationFacade | CI/CD pipeline, auth bypass prevention |
| **NEW** PlannerSseServiceTest.java | Medium | TestDataFactory, PlannerSseService | SSE memory leak prevention, concurrency validation |
| **NEW** NotificationControllerTest.java | Low | TestDataFactory, JwtTestUtil, MockMvc, NotificationService | Notification feature validation |
| **NEW** AdminModerationControllerTest.java | Low | TestDataFactory, JwtTestUtil, MockMvc, ModerationService | Admin authorization enforcement |
| **NEW** PlannerCommentRepositoryTest.java | Medium | TestEntityManager, PlannerCommentRepository | Atomic operation verification |
| **NEW** NotificationRepositoryTest.java | Low | TestEntityManager, NotificationRepository | Soft-delete filtering, UNIQUE constraint |
| **NEW** backend/TESTING.md | Low | N/A | Future test development (guidelines) |

### Ripple Effect Map

**If TestDataFactory changes** → All test files need updates (HIGH IMPACT)
- Mitigation: Stabilize API early, use builder pattern for optional fields

**If JwtTestUtil changes** → All controller tests break (HIGH IMPACT)
- Mitigation: Match JwtTokenService signature exactly, version tokens in constant

**If GlobalExceptionHandler changes** → Controller tests may need new assertions (MEDIUM IMPACT)
- Mitigation: Test error response structure generically ($.message exists)

**If RateLimitConfig changes** → Rate limit tests need bucket size updates (MEDIUM IMPACT)
- Mitigation: Inject @Value(rate-limit-config) in tests, not hardcode

**If SecurityConfig changes** → All authenticated tests may fail (HIGH IMPACT)
- Mitigation: Use @SpringBootTest (full context) to match production config

### High-Risk Modifications

**CommentControllerTest** - CRITICAL PATH
- **Risk**: Comment system is core feature; bugs affect all users
- **Mitigation**: 20+ scenarios cover CRUD, threading, voting, authorization, notifications

**AuthControllerTest** - SECURITY CRITICAL
- **Risk**: Auth bypass allows unauthorized access; token leakage exposes user data
- **Mitigation**: 15+ scenarios test OAuth flow, token lifecycle, cookie security, blacklist

**PlannerSseServiceTest** - MEMORY LEAK RISK
- **Risk**: Uncleaned emitters cause memory leaks in long-running servers
- **Mitigation**: 15+ scenarios test emitter cleanup on timeout, completion, error, zombie removal

---

## Execution Order

### Phase 1: Test Foundations (Steps 1-2)

1. **TestDataFactory.java**: Create test data builders
   - Depends on: None
   - Enables: All test files (eliminates 200+ lines of duplicated setup)
   - Methods: createTestUser(), createTestPlanner(), createTestComment(), createTestNotification()
   - Pattern: Lombok @Builder-style fluent API with sensible defaults

2. **JwtTestUtil.java**: Create JWT token generation helpers
   - Depends on: None (autowires JwtTokenService in test context)
   - Enables: All controller tests (authenticated HTTP requests)
   - Methods: generateTokenForUser(), generateExpiredToken(), generateAdminToken()
   - Pattern: Static utility methods wrapping JwtTokenService

### Phase 2: Critical Controllers (Steps 3-4)

3. **CommentControllerTest.java**: 20+ HTTP layer test scenarios
   - Depends on: Step 1, Step 2
   - Enables: Comment CRUD validation, vote immutability enforcement, authorization checks
   - Scenarios:
     - Create top-level comment (200), depth=0
     - Create reply (200), depth=parent+1
     - Depth=5 flattening (depth=5, uses parent's parentId)
     - Unauthorized creation (401)
     - Update own comment (200), content updated
     - Update other's comment (403)
     - Delete own comment (204), soft-deleted
     - Delete other's comment (403)
     - List comments public (200), excludes deleted
     - List unpublished (404)
     - Upvote first (200), upvoteCount++
     - Upvote duplicate (409)
     - Rate limit (429 on 11th request)
     - Validation (400 for empty content)
   - Pattern: @SpringBootTest + @AutoConfigureMockMvc + @Transactional + @Nested

4. **AuthControllerTest.java**: 15+ OAuth and token lifecycle scenarios
   - Depends on: Step 1, Step 2
   - Enables: OAuth security validation, token blacklist enforcement, cookie security
   - Scenarios:
     - OAuth new user (200), user created, cookies set
     - OAuth existing user (200), no new user, lastLoginAt updated
     - Username collision (200), retries with different suffix
     - Deleted user reactivation (200), deletedAt=null
     - Invalid auth code (401)
     - Cookie security (HttpOnly, Secure, SameSite=Lax flags present)
     - Token refresh (200), new tokens returned
     - Refresh expired (401)
     - Refresh blacklisted (401, "Token has been revoked")
     - Logout (204), tokens blacklisted, cookies cleared (Max-Age=0)
     - Use after logout (401)
   - Mocking: @MockBean GoogleOAuthService (prevent real OAuth API calls)
   - Pattern: @SpringBootTest + MockMvc + cookie assertions (response.getCookie())

### Phase 3: Infrastructure Services (Steps 5-6)

5. **PlannerSseServiceTest.java**: 15+ SSE lifecycle and concurrency scenarios
   - Depends on: Step 1
   - Enables: SSE memory leak prevention, concurrent access validation
   - Scenarios:
     - Subscribe creates emitter (returns SseEmitter, stored in map)
     - Subscribe replaces old (second subscribe completes old emitter first)
     - Multiple devices (3 deviceIds → 3 emitters for same user)
     - Broadcast excludes source (all except sourceDeviceId receive update)
     - Broadcast no subscribers (no errors)
     - Broken emitter cleanup (IOException → removed from map)
     - Unsubscribe removes (emitter removed, completed)
     - Unsubscribe idempotent (no error on missing device)
     - Timeout cleanup (onTimeout → removed)
     - Completion cleanup (onCompletion → removed)
     - Zombie cleanup (scheduler sends test events, removes unresponsive)
   - Pattern: @SpringBootTest + ExecutorService for concurrency + verify map state

6. **NotificationControllerTest.java**: 10+ notification management scenarios
   - Depends on: Step 1, Step 2
   - Enables: Notification inbox validation, read status management
   - Scenarios:
     - Inbox listing (200), paginated
     - Inbox excludes deleted (deletedAt != null not returned)
     - Inbox authorization (user sees only own)
     - Unread count (200), count where read=false
     - Mark read (200), read=true
     - Delete notification (204), deletedAt set
     - Unauthorized access (401)
   - Pattern: @SpringBootTest + MockMvc + pagination assertions

### Phase 4: Documentation (Step 7)

7. **backend/TESTING.md**: Testing guidelines and conventions
   - Depends on: None (documentation)
   - Enables: Consistent test development by future contributors
   - Sections:
     - When to use @SpringBootTest vs @DataJpaTest vs @ExtendWith(MockitoExtension)
     - MockMvc patterns (authenticated requests, JSON assertions, error responses)
     - Test data management (TestDataFactory, cleanup strategies)
     - Concurrency testing (ExecutorService + CountDownLatch)
     - Mocking external services (@MockBean for OAuth)
     - Transaction boundaries (entityManager.flush/clear)

### Phase 5: Repositories & Edge Cases (Steps 8-10)

8. **PlannerCommentRepositoryTest.java**: Custom query and atomic operation tests
   - Depends on: None (@DataJpaTest isolated)
   - Enables: Atomic increment verification, soft-delete filtering validation
   - Scenarios:
     - Atomic increment (incrementUpvoteCount by 1)
     - Concurrent increments (10 threads → upvoteCount=10, no lost updates)
     - Decrement safety (upvoteCount=0 → remains 0 after decrement)
     - Soft-delete filtering (findByPlannerId excludes deletedAt != null)
     - Top-level only (findByPlannerIdAndParentIsNull returns depth=0)
     - Batch reassignment (reassignCommentsToSentinel updates all user's comments to authorId=0)
   - Pattern: @DataJpaTest + TestEntityManager + flush/clear for DB verification

9. **NotificationRepositoryTest.java**: Soft-delete and UNIQUE constraint tests
   - Depends on: None (@DataJpaTest isolated)
   - Enables: Deduplication validation, soft-delete query correctness
   - Scenarios:
     - UNIQUE constraint (duplicate userId+contentId+type throws exception)
     - Soft-delete filtering (findByUserIdAndDeletedAtIsNull excludes deleted)
     - Count unread (count where read=false and deletedAt=null)
   - Pattern: @DataJpaTest + TestEntityManager

10. **AdminModerationControllerTest.java**: Admin authorization and moderation tests
    - Depends on: Step 1, Step 2
    - Enables: Admin-only endpoint enforcement, moderation flow validation
    - Scenarios:
      - Hide planner admin (200), hiddenFromRecommended=true
      - Hide planner non-admin (403)
      - Unhide planner admin (200), hiddenFromRecommended=false
      - Unhide planner non-admin (403)
      - Hide reason required (400 if <10 chars)
    - Pattern: @SpringBootTest + MockMvc + admin token generation

---

## Verification Checkpoints

**After Step 2 (Foundations Complete)**:
- Verify: TestDataFactory creates valid User/Planner/Comment entities
- Method: Run unit test on factory methods (createTestUser() → User with all required fields)

**After Step 4 (Critical Controllers Complete)**:
- Verify: CommentController all CRUD operations return correct HTTP status codes
- Method: Run CommentControllerTest → 20+ tests pass
- Verify: AuthController OAuth flow sets cookies, blacklist works
- Method: Run AuthControllerTest → 15+ tests pass

**After Step 6 (Infrastructure Complete)**:
- Verify: SSE emitters cleaned up on timeout/completion/error
- Method: Run PlannerSseServiceTest → verify emitter map empty after cleanup
- Verify: Notification inbox excludes soft-deleted notifications
- Method: Run NotificationControllerTest → verify deleted notifications not in response

**After Step 10 (All Tests Complete)**:
- Verify: All 74+ tests pass in <60 seconds
- Method: Run `mvn test` → 100% pass rate
- Verify: No flaky tests
- Method: Run full suite 3 consecutive times → all green each time

---

## Risk Mitigation (from requirements.md Risk Assessment)

| Risk | Step Affected | Mitigation |
|------|---------------|------------|
| **Edge Case: Orphaned replies visibility** | Step 3 (CommentControllerTest) | Test scenario "List comments with soft-deleted parent" → verify replies still returned |
| **Edge Case: Username collision retries** | Step 4 (AuthControllerTest) | Mock UserRepository.findByUsernameKeywordAndSuffix() to return existing user 5 times → verify 500 on exhaustion |
| **Edge Case: SSE duplicate deviceId** | Step 5 (PlannerSseServiceTest) | Test scenario "Subscribe twice with same deviceId" → verify only one active emitter in map |
| **Performance: Concurrency test timeout** | Step 8 (PlannerCommentRepositoryTest) | Use 10-second timeout + CountDownLatch + EntityManager.flush() to verify DB state |
| **Performance: SSE zombie cleanup** | Step 5 (PlannerSseServiceTest) | Test cleanup scheduler with 3 emitters → verify unresponsive ones removed in <5 seconds |
| **Backward Compat: Vote immutability** | Step 3 (CommentControllerTest) | Test scenario "Upvote duplicate" → verify 409 response (breaking change enforcement) |
| **Security: Token blacklist** | Step 4 (AuthControllerTest) | Test scenario "Use after logout" → verify 401 with blacklisted token |
| **Security: Authorization** | Steps 3, 6, 10 | Test scenarios for 401 (unauthenticated), 403 (non-owner), verify @PreAuthorize enforced |
| **Infrastructure: H2 vs MySQL** | All steps | Use H2 MySQL compatibility mode + verify AUTO_INCREMENT, SET conversion work |
| **Infrastructure: External service mocking** | Step 4 (AuthControllerTest) | @MockBean GoogleOAuthService → stub token exchange, user info retrieval |

---

## Pre-Implementation Validation Gate (ALL pattern copy tasks)

**BEFORE Step 3 execution (CommentControllerTest), verify:**

| Validation Category | Check | Blocker if Missing |
|---------------------|-------|-------------------|
| **Reference Completeness** | Read all 300 lines of PlannerControllerTest.java? | YES |
| **Contract Alignment** | CommentController endpoints match PlannerController pattern (MockMvc + @Valid)? | YES |
| **Dependency Resolution** | MockMvc, JwtTokenService, ObjectMapper, repositories available? | YES |
| **Structure Documentation** | @Nested organization, AAA pattern, JWT generation pattern documented? | YES |
| **Difference Justification** | Comment-specific DTOs (CreateCommentRequest vs CreatePlannerRequest) justified? | NO (expected difference) |

**BEFORE Step 4 execution (AuthControllerTest), verify:**

| Validation Category | Check | Blocker if Missing |
|---------------------|-------|-------------------|
| **Reference Completeness** | Read UserControllerTest.java + PlannerControllerTest.java patterns? | YES |
| **Contract Alignment** | AuthController cookie handling matches pattern (response.getCookie())? | YES |
| **Dependency Resolution** | @MockBean GoogleOAuthService configured for stub responses? | YES |
| **Structure Documentation** | Cookie assertion pattern (Set-Cookie header + flags) documented? | YES |
| **Difference Justification** | OAuth mocking (not in PlannerControllerTest) justified? | NO (expected addition) |

**BEFORE Step 5 execution (PlannerSseServiceTest), verify:**

| Validation Category | Check | Blocker if Missing |
|---------------------|-------|-------------------|
| **Reference Completeness** | Read all 298 lines of VoteNotificationFlowTest.java concurrency pattern? | YES |
| **Contract Alignment** | PlannerSseService map operations thread-safe (ConcurrentHashMap + CopyOnWriteArrayList)? | YES |
| **Dependency Resolution** | ExecutorService, CountDownLatch available for concurrency testing? | YES |
| **Structure Documentation** | Thread synchronization pattern (latch.countDown) documented? | YES |
| **Difference Justification** | SSE emitter lifecycle (not in VoteNotificationFlowTest) justified? | NO (expected addition) |

**Execution Rule**: Do NOT proceed if critical blockers unresolved (marked YES).

---

## Dependency Verification Steps

**After Step 1 (TestDataFactory created)**:
- Test: Verify Step 3 can use TestDataFactory.createTestUser() without errors
- Method: Compile CommentControllerTest stub with factory import

**After Step 2 (JwtTestUtil created)**:
- Test: Verify Step 3 can generate JWT tokens via JwtTestUtil.generateTokenForUser()
- Method: Compile CommentControllerTest stub with token generation

**After Step 3 (CommentControllerTest complete)**:
- Test: Verify GlobalExceptionHandler maps CommentNotFoundException → 404
- Method: Run test scenario "Delete non-existent comment" → assert status 404

**After Step 4 (AuthControllerTest complete)**:
- Test: Verify JwtAuthenticationFilter rejects blacklisted tokens
- Method: Run test scenario "Use after logout" → assert 401 response

**After Step 5 (PlannerSseServiceTest complete)**:
- Test: Verify emitter map empty after all tests complete (no memory leak)
- Method: Inspect emitters field via reflection → assert size 0

---

## Rollback Strategy

**If Step 3 fails (CommentControllerTest compilation errors)**:
- Rollback: Delete CommentControllerTest.java
- Debug: Verify TestDataFactory, JwtTestUtil compile independently
- Safe stop: Step 2 complete, foundations stable

**If Step 4 fails (AuthControllerTest @MockBean not working)**:
- Rollback: Delete AuthControllerTest.java
- Debug: Verify @MockBean GoogleOAuthService in isolated test
- Safe stop: Step 3 complete, CommentController tested

**If Step 5 fails (PlannerSseServiceTest concurrency issues)**:
- Rollback: Delete PlannerSseServiceTest.java
- Debug: Run VoteNotificationFlowTest to verify ExecutorService setup works
- Safe stop: Step 4 complete, critical controllers tested

**If Step 8 fails (PlannerCommentRepositoryTest @DataJpaTest errors)**:
- Rollback: Delete PlannerCommentRepositoryTest.java
- Debug: Verify PlannerRepositoryTest pattern works (@DataJpaTest + H2)
- Safe stop: Step 7 complete, documentation ready, controllers/services tested

**General rollback rule**: Each step creates ONE new file. If step fails, delete that file and debug in isolation before retrying.
