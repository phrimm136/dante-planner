# Backend Testing Infrastructure: Execution Plan

**Date:** 2026-01-11
**Estimated Effort:** 37-47 hours
**Target:** Complete controller test coverage, refactor test infrastructure, add security/database tests

---

## Planning Gaps

**No gaps found.** Research is complete:
- research.md confirms user decisions: comprehensive coverage, incremental refactoring, full rate limit testing, TestContainers included
- Pattern sources identified and validated (PlannerControllerTest, VoteNotificationFlowTest)
- All dependencies mapped in instructions.md Impact Analysis section
- Risk assessment complete with mitigation strategies

---

## Execution Overview

Implement comprehensive backend test coverage through 5 sequential phases. Foundation-first approach: build test infrastructure (Phase 1), add controller tests using new infrastructure (Phase 2-3), validate with production-identical database tests (Phase 4), and explicit constraint validation (Phase 5). Total 37-47 hours, targeting test suite under 3 minutes.

**Critical Success Factors:**
- Phase 1 TestDataFactory unblocks all subsequent phases
- Each controller test validates HTTP integration (auth, validation, rate limiting)
- Security tests validate boundaries service tests cannot reach
- TestContainers catches MySQL-specific bugs H2 misses
- Constraint tests ensure database integrity enforcement

---

## Dependency Analysis (Senior Thinking)

### Files Being Modified

| File | Impact Level | Depends On | Used By |
|------|--------------|------------|---------|
| TestDataFactory.java (new) | Medium | UserRepository, PlannerRepository, JwtTokenService | All new controller tests |
| AuthControllerTest.java (new) | Low (test only) | TestDataFactory, PlannerControllerTest pattern | None |
| CommentControllerTest.java (new) | Low (test only) | TestDataFactory, VoteNotificationFlowTest pattern | None |
| NotificationControllerTest.java (new) | Low (test only) | TestDataFactory, PlannerControllerTest pagination pattern | None |
| AdminModerationControllerTest.java (new) | Low (test only) | TestDataFactory, Spring Security Test | None |
| SecurityIntegrationTest.java (new) | Low (test only) | JwtAuthenticationFilterTest pattern | None |
| pom.xml (modified) | Medium | TestContainers dependencies | MySQLIntegrationTest |
| application-it.properties (new) | Low | None | MySQLIntegrationTest |
| MySQLIntegrationTest.java (new) | Low (test only) | TestContainers, VoteNotificationFlowTest concurrency pattern | None |
| PlannerRepositoryConstraintTest.java (new) | Low (test only) | PlannerRepositoryTest pattern, TestDataFactory | None |

### Ripple Effect Map

**Phase 1 → All Subsequent Phases:**
- TestDataFactory creation enables consistent test data across all controller/integration tests
- Eliminates 30-40% code duplication in new tests
- Changes to User/Planner entities require single factory update vs 15+ test files

**Phase 2-3 Controller Tests → Integration validation:**
- AuthControllerTest validates OAuth flow → enables authenticated test patterns for other controllers
- CommentControllerTest notification integration → validates event-driven architecture
- Rate limit tests → validates RateLimitConfig bucket isolation

**Phase 4 TestContainers → Production confidence:**
- MySQL dialect tests → validates H2 compatibility assumptions
- Concurrent tests → validates atomic operation correctness under load
- Constraint tests → validates database integrity layer

**No backward ripple:** Test files are isolated, no production code changes

### High-Risk Modifications

| Risk | Affected Step | Mitigation |
|------|---------------|------------|
| Rate limit bucket state pollution | SecurityIntegrationTest Step 22-23 | Reset buckets in @BeforeEach via RateLimitConfig injection |
| Concurrent test flakiness | MySQLIntegrationTest Step 26-27 | Use CountDownLatch synchronization, 10s timeout, run 20+ times locally |
| TestContainers Docker unavailable | Phase 4 Steps 24-27 | Tag with @Tag("containerized"), document Docker requirement |
| TestDataFactory breaks existing tests | Step 1 | Incremental adoption, validate PlannerControllerTest still passes |

---

## Execution Order

### Phase 1: Test Infrastructure (2-3 hours)

**Step 1: Create TestDataFactory.java**
- Depends on: None
- Enables: All controller tests (Steps 2-21), integration tests (Steps 26-27), constraint tests (Steps 28-30)
- Pattern source: PlannerControllerTest.java lines 82-107 (setUp method)
- File: /backend/src/test/java/org/danteplanner/backend/support/TestDataFactory.java
- Methods: createTestUser, createAdmin, createModerator, createTestPlanner, generateAccessToken
- Validates: Run PlannerControllerTest after creation to ensure no interference

---

### Phase 2: Controller Tests - Authentication & Comments (11-14 hours)

**Steps 2-7: Create AuthControllerTest.java (6-8 hours)**
- Depends on: Step 1 (TestDataFactory)
- Enables: F1 (OAuth flow validation, JWT generation patterns for other tests)
- Pattern source: PlannerControllerTest.java lines 48-200
- File: /backend/src/test/java/org/danteplanner/backend/controller/AuthControllerTest.java

Step 2: Create test class structure with @SpringBootTest, @AutoConfigureMockMvc, @Transactional
Step 3: GoogleCallbackTests nested class - Valid OAuth code test case (200 OK, JWT in cookies)
Step 4: GoogleCallbackTests - Invalid OAuth code test case (401 Unauthorized)
Step 5: GoogleCallbackTests - New user creation with random username generation test
Step 6: GoogleCallbackTests - Deleted user account reactivation test (V020 feature)
Step 7: GoogleCallbackTests + GetCurrentUserTests - Rate limiting (429), cookie security (HttpOnly, Secure, SameSite), valid token (200), missing token (401), expired token (401)

**Steps 8-13: Create CommentControllerTest.java (5-6 hours)**
- Depends on: Step 1 (TestDataFactory)
- Enables: F2 (Comment system validation, notification integration patterns)
- Pattern source: PlannerControllerTest.java (authorization), VoteNotificationFlowTest.java (notifications)
- File: /backend/src/test/java/org/danteplanner/backend/controller/CommentControllerTest.java

Step 8: Create test class structure with nested classes: GetCommentsTests, CreateCommentTests, UpdateCommentTests, DeleteCommentTests, UpvoteTests
Step 9: GetCommentsTests - Public access test, unpublished planner 403, vote status included
Step 10: CreateCommentTests - Valid top-level comment (201), reply with parent (depth calculation), depth 6 flattening test
Step 11: CreateCommentTests - Unauthenticated 401, unpublished planner 403, validation (empty content 400)
Step 12: UpdateCommentTests + DeleteCommentTests - Owner can edit/delete (200/204), non-owner gets 403, soft-delete preserves thread
Step 13: UpvoteTests + rate limiting - Upvote toggle (create/remove), atomic counter, rate limiting 429

---

### Phase 3: Controller Tests - Notifications & Moderation (7-9 hours)

**Steps 14-17: Create NotificationControllerTest.java (4-5 hours)**
- Depends on: Step 1 (TestDataFactory)
- Enables: F3 (Notification system validation, pagination pattern verification)
- Pattern source: PlannerControllerTest.java lines 800+ (pagination)
- File: /backend/src/test/java/org/danteplanner/backend/controller/NotificationControllerTest.java

Step 14: Create test class structure with nested classes: GetInboxTests, GetUnreadCountTests, MarkAsReadTests, MarkAllAsReadTests, DeleteNotificationTests
Step 15: GetInboxTests - Pagination structure, empty inbox, page size enforcement (max 100), default size (20), ordering (DESC)
Step 16: GetUnreadCountTests + MarkAsReadTests - Count accuracy, mark single read (200), already read idempotent, not owner 403
Step 17: MarkAllAsReadTests + DeleteNotificationTests - Bulk mark read count, soft-delete 204, user isolation 403, excludes from inbox after delete

**Steps 18-21: Create AdminModerationControllerTest.java (3-4 hours)**
- Depends on: Step 1 (TestDataFactory)
- Enables: F4 (RBAC validation, admin-only operation patterns)
- Pattern source: PlannerControllerTest.java (403 Forbidden patterns), Spring Security Test docs
- File: /backend/src/test/java/org/danteplanner/backend/controller/AdminModerationControllerTest.java

Step 18: Create test class structure with nested classes: HideFromRecommendedTests, UnhideFromRecommendedTests, use @WithMockUser for role testing
Step 19: HideFromRecommendedTests - Admin role 200, Moderator role 200, regular user 403, unauthenticated 401
Step 20: HideFromRecommendedTests - Valid reason sets metadata (moderator ID, reason, timestamp), short reason 400 (min 10), long reason 400 (max 500)
Step 21: HideFromRecommendedTests + UnhideFromRecommendedTests - Vote preservation test, removes from recommended query, unhide clears metadata, idempotent operations

---

### Phase 4: Security & Database Integration (10-13 hours)

**Steps 22-23: Create SecurityIntegrationTest.java (4-5 hours)**
- Depends on: Step 1 (TestDataFactory), JwtAuthenticationFilterTest pattern
- Enables: F5, F6, F7 (Security boundary validation - CORS, RBAC, rate limiting)
- Pattern source: JwtAuthenticationFilterTest.java, instructions.md security examples
- File: /backend/src/test/java/org/danteplanner/backend/security/SecurityIntegrationTest.java

Step 22: CorsTests nested class - OPTIONS preflight with allowed origin (200, Access-Control headers), denied origin (403 or no headers), actual request after preflight
Step 23: RbacTests + RateLimitTests nested classes - Admin endpoint with USER role 403, with ADMIN role 2xx, exhaust rate limit bucket (11th request 429), bucket reset validation

**Step 24: Add TestContainers dependencies to pom.xml (0.5 hours)**
- Depends on: None
- Enables: Steps 25-27 (MySQL integration tests)
- File: /backend/pom.xml
- Dependencies: org.testcontainers:testcontainers:1.19.3, org.testcontainers:mysql:1.19.3, org.testcontainers:junit-jupiter:1.19.3

**Step 25: Create application-it.properties (0.5 hours)**
- Depends on: Step 24
- Enables: TestContainers MySQL configuration
- File: /backend/src/test/resources/application-it.properties
- Properties: jdbc:tc:mysql:8.0:///testdb, MySQLDialect, hibernate.ddl-auto=validate

**Steps 26-27: Create MySQLIntegrationTest.java (6-8 hours)**
- Depends on: Steps 24-25 (TestContainers setup), Step 1 (TestDataFactory)
- Enables: F8 (Production-identical database validation)
- Pattern source: VoteNotificationFlowTest.java lines 120-300 (concurrency)
- File: /backend/src/test/java/org/danteplanner/backend/integration/MySQLIntegrationTest.java

Step 26: Create base class with @Testcontainers, MySQLContainer setup, @DynamicPropertySource configuration, @Tag("containerized")
Step 27: Write test cases - Concurrent atomic votes (50 threads, no lost updates), UNIQUE constraint error message format validation, timestamp microsecond precision, transaction isolation test

---

### Phase 5: Database Constraint Validation (3-4 hours)

**Steps 28-30: Create PlannerRepositoryConstraintTest.java (3-4 hours)**
- Depends on: Step 1 (TestDataFactory)
- Enables: F9 (Explicit constraint enforcement validation)
- Pattern source: PlannerRepositoryTest.java (repository structure)
- File: /backend/src/test/java/org/danteplanner/backend/repository/PlannerRepositoryConstraintTest.java

Step 28: ForeignKeyTests nested class - Invalid planner ID FK violation, invalid user ID FK violation, cascade delete behavior
Step 29: UniqueConstraintTests nested class - Duplicate vote (user_id, planner_id) violation, different user same planner allowed, same user different planner allowed
Step 30: NotNullTests nested class - Missing title violation, missing user violation, empty string vs null distinction, default value application (published = false)

---

## Verification Checkpoints

**After Step 1 (TestDataFactory):**
- Run existing PlannerControllerTest: `mvn test -Dtest=PlannerControllerTest`
- Expected: All tests pass (no regression from factory creation)

**After Steps 2-7 (AuthControllerTest):**
- Run: `mvn test -Dtest=AuthControllerTest`
- Expected: 15+ tests pass, covers OAuth/JWT/cookies, executes under 30 seconds

**After Steps 8-13 (CommentControllerTest):**
- Run: `mvn test -Dtest=CommentControllerTest`
- Expected: 20+ tests pass, threading depth test validates flattening at level 6

**After Steps 14-21 (All Controller Tests):**
- Run full controller suite: `mvn test -Dtest=*ControllerTest`
- Expected: 60+ total controller tests pass under 2 minutes

**After Steps 22-23 (SecurityIntegrationTest):**
- Run: `mvn test -Dtest=SecurityIntegrationTest`
- Expected: 15-20 tests pass, rate limit tests confirm 429 on 11th request
- Manual: Reset buckets between test runs to confirm isolation

**After Steps 24-27 (TestContainers):**
- Run with Docker: `mvn test -Dtest=MySQLIntegrationTest`
- Expected: MySQL container starts, 5-10 tests pass, concurrent tests show no lost updates
- Manual: Run 20 times to validate no flakiness
- Timing: Container startup adds 10-15 seconds

**After Steps 28-30 (Constraint Tests):**
- Run: `mvn test -Dtest=*RepositoryConstraintTest`
- Expected: 20+ constraint tests pass, FK/UNIQUE/NOT NULL violations properly caught

**Final Validation:**
- Full H2 suite: `mvn test -Dgroups=!containerized`
- Expected: Under 3 minutes
- Full suite with TestContainers: `mvn test`
- Expected: Under 4 minutes (includes Docker startup)

---

## Risk Mitigation (from instructions.md Risk Assessment)

| Risk | Step Affected | Mitigation |
|------|---------------|------------|
| TestDataFactory breaks existing tests | Step 1 | Create factory but don't refactor PlannerControllerTest yet. Validate with test run. New tests adopt factory, old tests unchanged. |
| Rate limit bucket state pollution | Steps 22-23 | Reset buckets in @BeforeEach via RateLimitConfig injection. Alternative: unique bucket names per test method. |
| Concurrent test flakiness | Steps 26-27 | Use CountDownLatch to synchronize thread start. Set 10s timeout. Run 20+ times locally before PR. |
| TestContainers Docker unavailable | Steps 24-27 | Tag with @Tag("containerized"). Document Docker requirement in README. Separate CI job for containerized tests. |
| Threading depth calculation bug | Steps 8-13 | Explicit test case: create depth 0-5 comments, verify depth 6 becomes sibling of parent. |
| CORS config test pollution | Steps 22-23 | Use separate test profile with explicit CORS config, avoid modifying global SecurityConfig. |
| MySQL dialect incompatibility | Steps 26-27 | Start with simple atomic test, validate container works, then add complex concurrent tests. |

---

## Pre-Implementation Validation Gate (ALL pattern copy tasks)

**Verify research completed Pattern Copy Deep Analysis:**
- [x] PlannerControllerTest.java read completely (1748 lines) - research.md confirms line 48-200 pattern extraction
- [x] VoteNotificationFlowTest.java concurrent patterns documented (465 lines) - research.md confirms lines 120-300
- [x] Test structure patterns mapped (nested classes, MockMvc, assertions) - instructions.md provides examples
- [x] PlannerRepositoryTest.java repository structure reviewed - research.md confirms pattern source
- [x] JwtAuthenticationFilterTest.java security patterns identified - research.md confirms pattern source

---

## Dependency Verification Steps

**After Step 1 (TestDataFactory):**
- Verify: AuthControllerTest can import TestDataFactory (compile check)
- Verify: No circular dependencies introduced (Maven dependency:tree)
- Verify: Existing PlannerControllerTest still passes without modification

**After Step 24 (pom.xml update):**
- Verify: `mvn dependency:tree | grep testcontainers` shows 3 dependencies
- Verify: `mvn clean compile` succeeds with new dependencies

**After Step 25 (application-it.properties):**
- Verify: File contains jdbc:tc:mysql URL and MySQLDialect configuration
- Verify: @ActiveProfiles("it") loads correct properties

---

## Rollback Strategy

**Per-phase rollback points:**
- Phase 1: Delete TestDataFactory.java (no impact on existing tests)
- Phase 2-3: Delete individual controller test files (independent, no production impact)
- Phase 4 Security: Delete SecurityIntegrationTest.java (no dependencies)
- Phase 4 TestContainers: Remove 3 dependencies from pom.xml, delete application-it.properties and MySQLIntegrationTest.java
- Phase 5: Delete constraint test files (independent)

**Safe stopping points:**
- After any completed phase (tests are additive)
- After any individual controller test (files are independent)

**Validation after rollback:**
- Run full test suite to confirm no breakage
- Verify pom.xml compiles without errors
