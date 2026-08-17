# Backend Testing Infrastructure: Research Findings

**Date:** 2026-01-11
**Task:** Complete controller test coverage, refactor test infrastructure, add security/database tests

---

## Clarifications Resolved (User Decisions)

**1. TestContainers Scope:** Include full implementation (Maven deps + MySQL tests)
**2. Refactoring Strategy:** Incremental (new tests use factory, existing tests unchanged)
**3. Test Coverage:** Comprehensive (test all endpoints in 4 controllers)
**4. Rate Limiting:** Full enforcement validation (test exact thresholds with bucket reset)

---

## Spec-to-Code Mapping

### Priority 1: Controller Tests (18-23 hours total)

**AuthController** (3 endpoints, 6-8 hours)
- POST `/api/auth/google/callback` - OAuth flow, JWT generation, cookie security
- GET `/api/auth/me` - Current user lookup with token validation
- POST `/api/auth/apple/callback` - Apple OAuth stub
- MUST READ: `PlannerControllerTest.java` lines 48-200 (setup pattern, MockMvc structure)
- Pattern: JWT token generation in setUp, cookie verification, rate limiting by IP

**CommentController** (5 endpoints, 5-6 hours)
- GET `/api/planner/{id}/comments` - Public access, user vote status included
- POST `/api/planner/{id}/comments` - Create comment, threading depth calculation
- PUT `/api/comments/{id}` - Edit comment, owner-only authorization
- DELETE `/api/comments/{id}` - Soft-delete, preserve thread structure
- POST `/api/comments/{id}/upvote` - Toggle upvote, atomic counter
- MUST READ: `PlannerControllerTest.java` (authorization patterns), `VoteNotificationFlowTest.java` (notification side effects)
- Pattern: Ownership validation (testUser vs otherUser), threading depth edge cases (flatten at level 6)

**NotificationController** (5 endpoints, 4-5 hours)
- GET `/api/notifications/inbox` - Pagination, soft-delete filter
- GET `/api/notifications/unread-count` - Simple count query
- POST `/api/notifications/{id}/mark-read` - Single notification update
- POST `/api/notifications/mark-all-read` - Bulk update
- DELETE `/api/notifications/{id}` - Soft-delete
- MUST READ: `PlannerControllerTest.java` (pagination testing lines 800+)
- Pattern: Pagination enforcement (max 100), user isolation checks, soft-delete behavior

**AdminModerationController** (2 endpoints, 3-4 hours)
- POST `/api/admin/planner/{id}/hide-from-recommended` - Hide with reason, moderator audit
- POST `/api/admin/planner/{id}/unhide-from-recommended` - Restore to recommended
- MUST READ: Spring Security Test docs for `@WithMockUser`, `PlannerControllerTest.java` (403 Forbidden patterns)
- Pattern: RBAC enforcement (`@PreAuthorize` validation), admin vs regular user, reason validation (10-500 chars)

---

### Priority 2: Test Infrastructure Refactoring (2-3 hours)

**TestDataFactory.java** (NEW, 100-150 lines)
- Extract from: `PlannerControllerTest.java` setUp (lines 82-107)
- Methods: `createTestUser(repo, email)`, `createAdmin(repo, email)`, `createTestPlanner(repo, owner, published)`, `generateAccessToken(service, user)`
- Usage: All new tests import and use factory methods
- Incremental approach: PlannerControllerTest remains unchanged (do NOT refactor existing working test)

**TestUserFixture.java** (OPTIONAL, ~50 lines)
- Role management: `createUserWithRole(email, role)`, `createModerator()`, `createAdmin()`
- Only if AdminModerationController needs complex role setups

---

### Priority 3: Security Tests (4-5 hours)

**SecurityIntegrationTest.java** (NEW, 200-300 lines)
- CORS preflight tests: OPTIONS method, allowed origins, denied origins
- RBAC tests: `@WithMockUser(roles="ADMIN")` vs `@WithMockUser(roles="USER")` for admin endpoints
- Rate limiting: Exhaust bucket (10 auth requests, 11th returns 429), reset bucket between tests via `RateLimitConfig` manipulation
- MUST READ: `JwtAuthenticationFilterTest.java` (security context setup), spec examples for CORS/RBAC patterns

---

### Priority 4: TestContainers MySQL (6-8 hours)

**Maven Setup (pom.xml)** - Add 3 dependencies
- org.testcontainers:testcontainers:1.19.3
- org.testcontainers:mysql:1.19.3
- org.testcontainers:junit-jupiter:1.19.3

**application-it.properties** (NEW)
- jdbc:tc:mysql:8.0:///testdb (TestContainers JDBC URL)
- spring.jpa.hibernate.ddl-auto=validate
- spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.MySQLDialect

**MySQLIntegrationTest.java** (NEW, 150-200 lines)
- Concurrent atomic operations: 50 threads incrementing upvotes simultaneously, verify all counted
- UNIQUE constraint error messages: Validate MySQL format ("Duplicate entry ... for key 'unique_username'")
- Timestamp precision: Rapid creation with microsecond ordering
- MUST READ: `VoteNotificationFlowTest.java` (concurrent patterns with ExecutorService, CountDownLatch)
- Tag with `@Tag("containerized")` for separate CI job

---

### Priority 5: Constraint Tests (3-4 hours)

**PlannerRepositoryConstraintTest.java** (NEW, 150-200 lines)
- FK violations: Invalid planner ID, invalid user ID
- UNIQUE violations: Duplicate vote (user_id, planner_id)
- NOT NULL violations: Missing title, missing user
- CHECK violations: Negative upvote count (if CHECK constraint exists)
- MUST READ: `PlannerRepositoryTest.java` (repository test setup), database-testing-01 learning guide

**CommentRepositoryConstraintTest.java** (OPTIONAL, ~100 lines)
- FK violations: Invalid planner ID in comment
- CHECK violations: Comment depth > 5, negative depth

---

## Pattern Enforcement

### MANDATORY Read-Before-Write

| New Test File | MUST Read First | Lines to Study | Pattern to Extract |
|---------------|-----------------|----------------|-------------------|
| AuthControllerTest | PlannerControllerTest.java | 48-200 | `@SpringBootTest(MOCK)`, `@BeforeEach` setup, MockMvc assertions |
| CommentControllerTest | PlannerControllerTest.java + VoteNotificationFlowTest.java | All setUp + concurrent patterns | Authorization checks, notification integration |
| NotificationControllerTest | PlannerControllerTest.java | 800+ (pagination tests) | Page size enforcement, jsonPath assertions for pages |
| AdminModerationControllerTest | PlannerControllerTest.java | Authorization 403 tests | Ownership validation pattern adapted for RBAC |
| SecurityIntegrationTest | JwtAuthenticationFilterTest.java | Security context setup | Token validation, authentication simulation |
| MySQLIntegrationTest | VoteNotificationFlowTest.java | 120-300 (concurrent tests) | ExecutorService, CountDownLatch, awaitTermination |
| TestDataFactory | PlannerControllerTest.java | 82-107 (setUp) | User creation, planner creation, token generation |

---

## Existing Utilities (Reuse Before Creating)

**Test Infrastructure (existing):**
- `TestConfig.java` - ObjectMapper bean for Jackson 2.x compatibility
- `application-test.properties` - H2 database, JWT secrets, CORS origins, rate limits (60/60s for CRUD)
- `PlannerControllerTest.java` setUp - User/planner/token creation (EXTRACT to factory)
- `VoteNotificationFlowTest.java` - Concurrent test patterns (REUSE for comment voting)
- `PlannerRepositoryTest.java` - Repository test structure (REUSE for constraint tests)

**Supporting Classes:**
- `JwtTokenService` - Token generation (autowired in controller tests)
- `RateLimitConfig` - Bucket4j rate limiting (inject to reset buckets in tests)
- `UserRepository`, `PlannerRepository`, `CommentRepository` - Data access (autowired)

**Do NOT Create:**
- Additional Jackson config (TestConfig provides ObjectMapper)
- Custom JWT utilities (JwtTokenService handles generation)
- Alternative rate limit implementations (use existing RateLimitConfig)

---

## Gap Analysis

**Currently Missing (Must Create):**
- 4 controller test files (Auth, Comment, Notification, AdminModeration)
- TestDataFactory.java (eliminates ~500 lines of duplication)
- SecurityIntegrationTest.java (CORS, RBAC, rate limiting)
- TestContainers dependencies in pom.xml
- application-it.properties for MySQL integration tests
- MySQLIntegrationTest.java (dialect-specific tests)
- 2 constraint test files (Planner, Comment)

**Currently Exists (Can Reuse):**
- MockMvc test pattern in PlannerControllerTest
- Concurrent test pattern in VoteNotificationFlowTest
- Repository test pattern in PlannerRepositoryTest
- JWT token generation via JwtTokenService
- Test properties with all configurations

---

## Testing Requirements

### Manual Verification (Developer)
- Run individual test classes: `mvn test -Dtest=AuthControllerTest`
- Run full suite: `mvn test` (should complete under 3 minutes)
- Run TestContainers only: `mvn test -Dgroups=containerized`
- Check Hibernate Statistics: Enable logging.level.org.hibernate.stat=DEBUG for N+1 detection
- Verify rate limiting: Use JMeter or curl scripts to manually test bucket exhaustion

### Automated Test Categories

**Controller Tests** - HTTP-level integration (90+ test cases total):
- Request binding validation (path variables, request bodies)
- Authentication boundaries (no token → 401, invalid token → 401)
- Authorization boundaries (wrong user → 403, non-admin → 403)
- Rate limiting (11th request → 429, after wait → 200)
- Status codes (200 OK, 201 Created, 400 Bad Request, 403 Forbidden, 404 Not Found, 429 Too Many Requests)
- Response body validation (jsonPath assertions for DTO structure)

**Security Tests** - CORS/RBAC/Rate limits (15-20 test cases):
- CORS preflight: OPTIONS with allowed origin → 200 with headers
- CORS blocked: OPTIONS with evil.com origin → 403 or no headers
- RBAC: Admin endpoint with USER role → 403, with ADMIN role → 2xx
- Rate limiting: Exhaust bucket, verify 429, wait for refill, verify 200

**Repository Tests** - Constraint enforcement (20+ test cases):
- FK violations: Non-existent parent → DataIntegrityViolationException
- UNIQUE violations: Duplicate composite key → exception
- NOT NULL violations: Missing required field → exception
- Cascade behavior: Delete parent → children deleted (verify with count)

**Integration Tests** - MySQL-specific (5-10 test cases):
- Concurrent atomic operations: 50 threads vote simultaneously → all counted
- Timestamp precision: Rapid creation → microsecond ordering preserved
- UNIQUE error messages: Duplicate vote → "Duplicate entry 'key' for key 'constraint_name'"

---

## Technical Constraints

**Test Execution Budget:**
- Target: Full suite under 3 minutes
- H2 tests: ~1-2 minutes (fast, in-memory)
- TestContainers tests: +10-15 seconds (Docker startup)
- Strategy: Run H2 on every commit, TestContainers on PR only

**Rate Limiting in Tests:**
- Bucket4j state persists across tests in same JVM
- Solution: Inject RateLimitConfig, call internal reset method in @BeforeEach
- Alternative: Use separate buckets per test method (test-auth-1, test-auth-2, etc.)

**Concurrency Testing:**
- ExecutorService threads share transaction state (potential conflicts)
- Solution: Each thread uses separate transaction via `TransactionTemplate`
- Timeout: 10 seconds max for concurrent tests (fail fast if deadlock)

**TestContainers Docker:**
- Requires Docker daemon running on developer machine and CI
- Container startup adds 10-15 seconds per test class
- Tag with `@Tag("containerized")` for optional local execution

**Pattern Compliance:**
- All controller tests MUST follow PlannerControllerTest structure (nested classes, MockMvc)
- All concurrent tests MUST use ExecutorService + CountDownLatch pattern from VoteNotificationFlowTest
- All repository tests MUST use `entityManager.flush()` to trigger constraints

---

## Implementation Order (Recommended)

1. **TestDataFactory** (2h) - Unblocks all new tests
2. **AuthControllerTest** (6-8h) - Simplest OAuth flow, establishes controller test pattern for team
3. **CommentControllerTest** (5-6h) - CRUD + threading + notifications
4. **NotificationControllerTest** (4-5h) - Pagination patterns
5. **AdminModerationControllerTest** (3-4h) - RBAC patterns
6. **SecurityIntegrationTest** (4-5h) - CORS + rate limit edge cases
7. **TestContainers setup** (2h) - Maven + properties
8. **MySQLIntegrationTest** (6-8h) - Concurrent constraint testing
9. **PlannerRepositoryConstraintTest** (3-4h) - Explicit constraint validation
10. **CommentRepositoryConstraintTest** (optional, 2-3h)

**Total Effort:** 37-47 hours (comprehensive implementation)

---

## Success Criteria (from spec)

- All 4 priority controllers have comprehensive test files
- TestDataFactory eliminates ~500 lines of duplication (30-40% reduction)
- SecurityIntegrationTest covers CORS, RBAC, rate limiting
- TestContainers MySQL configured with @Tag("containerized")
- MySQLIntegrationTest validates atomic operations with 50+ threads
- Constraint tests explicitly validate FK, UNIQUE, NOT NULL violations
- Full suite runs under 3 minutes (H2 tests only)
- H2 + TestContainers suite runs under 4 minutes (with Docker startup)
- No flaky tests (all tests pass consistently with 10+ runs)

---

## Risk Mitigation

**HIGH RISK: Test Refactoring**
- Mitigation: Incremental approach (do NOT refactor PlannerControllerTest yet)
- Validation: Run existing PlannerControllerTest after creating factory to ensure no breakage

**MEDIUM RISK: Concurrent Test Flakiness**
- Mitigation: Use CountDownLatch to synchronize thread start, set 10s timeout
- Validation: Run concurrent tests 20+ times locally before PR

**MEDIUM RISK: TestContainers Docker Dependency**
- Mitigation: Tag with `@Tag("containerized")`, separate CI job
- Validation: Document Docker requirement in README, provide CI workflow example

**LOW RISK: Rate Limit State Isolation**
- Mitigation: Reset buckets in @BeforeEach or use unique bucket names per test
- Validation: Run SecurityIntegrationTest sequentially (no parallel execution)

---

**Files Referenced:**
- PlannerControllerTest.java (1748 lines) - Primary pattern source
- VoteNotificationFlowTest.java (465 lines) - Concurrent test patterns
- PlannerRepositoryTest.java (230 lines) - Repository test structure
- TestConfig.java - Jackson ObjectMapper configuration
- application-test.properties - H2 test configuration
- JwtAuthenticationFilterTest.java - Security context patterns

**Domain:** Backend Testing (be-testing skill applicable)
**Architecture Context:** Cross-cutting concern (affects all backend layers)
