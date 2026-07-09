# Task: Backend Testing Infrastructure Improvements

## Description
Improve backend testing architecture across three critical dimensions:

### 1. Complete Controller Test Coverage (Priority 1)
Add comprehensive MockMvc-based controller tests for 6 untested REST endpoints. Currently, only `PlannerController` and `UserController` have full test coverage. These missing tests represent critical gaps in validating HTTP-level integration including authentication, authorization, request binding, validation, rate limiting, and response formatting.

### 2. Extract Test Code Duplication (Priority 2)
Eliminate ~500 lines of duplicate test setup code across 15+ test classes. Every test file recreates users, planners, and JWT tokens with identical boilerplate. Extract to centralized `TestDataFactory` and `TestFixtures` classes to improve maintainability and consistency.

### 3. Expand Security Test Coverage (Priority 3)
Add missing security boundary tests for CORS, CSRF, RBAC, and rate limiting. Current JWT tests are solid but CORS preflight, authorization boundaries, and rate limiting edge cases are undertested.

### 4. Add TestContainers MySQL Tests (Priority 4)
Supplement H2 tests with production-identical MySQL tests for critical integration scenarios. H2 is convenient but doesn't catch dialect-specific bugs (locking, date handling, stored procedures). Use TestContainers for database-critical tests (atomic operations, constraints, race conditions).

### 5. Add Database Constraint Tests (Priority 5)
Explicitly test FK violations, UNIQUE violations, and NOT NULL violations at the repository layer. Current repository tests focus on query correctness but don't validate constraint enforcement.

## Research
**Existing patterns to study:**
- `/backend/src/test/java/org/danteplanner/backend/controller/PlannerControllerTest.java` (1748 lines) - Reference pattern for MockMvc tests
- `/backend/src/test/java/org/danteplanner/backend/integration/VoteNotificationFlowTest.java` (465 lines) - Concurrent integration test pattern
- `/backend/src/test/java/org/danteplanner/backend/repository/PlannerRepositoryTest.java` - Real database test pattern

**Technologies to understand:**
- MockMvc for HTTP simulation
- Bucket4j rate limiting
- Spring Security Test (`@WithMockUser`, `@WithUserDetails`)
- TestContainers for Docker-based MySQL
- JaCoCo for coverage reporting
- PITest for mutation testing (optional)

**Security testing resources:**
- OWASP Top 10 testing practices
- Spring Security Test documentation
- CORS specification (preflight requests)

## Scope
**Read for context:**
- All controller implementations: `/backend/src/main/java/org/danteplanner/backend/controller/*.java`
- Existing test patterns: `/backend/src/test/java/org/danteplanner/backend/**/*Test.java`
- Security config: `/backend/src/main/java/org/danteplanner/backend/config/SecurityConfig.java`
- Rate limit config: `/backend/src/main/java/org/danteplanner/backend/config/RateLimitConfig.java`
- Architecture map: `/docs/architecture-map.md`

## Target Code Area

**New test files (Priority 1 - Controller Tests):**
- `/backend/src/test/java/org/danteplanner/backend/controller/AuthControllerTest.java`
- `/backend/src/test/java/org/danteplanner/backend/controller/CommentControllerTest.java`
- `/backend/src/test/java/org/danteplanner/backend/controller/NotificationControllerTest.java`
- `/backend/src/test/java/org/danteplanner/backend/controller/AdminModerationControllerTest.java`

**New infrastructure (Priority 2 - Test Refactoring):**
- `/backend/src/test/java/org/danteplanner/backend/support/TestDataFactory.java`
- `/backend/src/test/java/org/danteplanner/backend/support/TestUserFixture.java`
- `/backend/src/test/java/org/danteplanner/backend/support/AbstractControllerTest.java` (optional base class)

**New security tests (Priority 3):**
- `/backend/src/test/java/org/danteplanner/backend/security/SecurityIntegrationTest.java`
- Expand: `/backend/src/test/java/org/danteplanner/backend/security/JwtAuthenticationFilterTest.java`
- Add: `/backend/src/test/java/org/danteplanner/backend/config/RateLimitConfigTest.java` (expand existing minimal test)

**New database tests (Priority 4 - TestContainers):**
- `/backend/src/test/java/org/danteplanner/backend/integration/MySQLIntegrationTest.java`
- Tag critical tests with `@Tag("containerized")` for separate CI execution

**New constraint tests (Priority 5):**
- `/backend/src/test/java/org/danteplanner/backend/repository/PlannerRepositoryConstraintTest.java`
- `/backend/src/test/java/org/danteplanner/backend/repository/CommentRepositoryConstraintTest.java`

**Configuration:**
- `/backend/pom.xml` - Add TestContainers, JaCoCo dependencies
- `/backend/src/test/resources/application-it.properties` - TestContainers profile

## System Context (Senior Thinking)
- **Feature domain:** Backend Testing Infrastructure (Cross-cutting concern)
- **Core files affected:**
  - All untested controllers (AuthController, CommentController, NotificationController, AdminModerationController)
  - Test infrastructure: JUnit 5, Mockito, MockMvc, Spring Boot Test, TestContainers
  - Security: JWT authentication, RBAC, rate limiting, CORS
- **Cross-cutting concerns:**
  - Authentication (JWT cookies, token validation)
  - Authorization (user ownership checks, RBAC via `@PreAuthorize`)
  - Rate limiting (Bucket4j)
  - Validation (Bean Validation constraints)
  - Exception handling (GlobalExceptionHandler)
  - Cookie security (HttpOnly, Secure, SameSite)
  - Database constraints (FK, UNIQUE, NOT NULL)

## Impact Analysis

**Testing layers:**
| Layer | Current State | Gap | Risk |
|-------|---------------|-----|------|
| **Unit Tests** | Service layer well-mocked | None | Low |
| **Repository Tests** | H2 database, query validation | Constraint testing, MySQL dialect | Medium |
| **Controller Tests** | 2/8 controllers covered | 6 controllers untested | **HIGH** |
| **Security Tests** | JWT solid, CORS/RBAC gaps | CORS preflight, RBAC boundaries | **HIGH** |
| **Integration Tests** | Race condition test excellent | Coverage limited | Medium |

**Files being modified:** New test files only (no production code changes unless bugs found)

**Test coverage by controller:**
| Controller | Endpoints | Test Coverage | Risk Level | Effort |
|------------|-----------|---------------|------------|--------|
| PlannerController | 15+ | ✅ Full (1748 lines) | Low | Done |
| UserController | 5+ | ✅ Full | Low | Done |
| **AuthController** | 3 | ❌ **0%** | **HIGH** | 6-8h |
| **CommentController** | 5 | ❌ **0%** | **HIGH** | 5-6h |
| **NotificationController** | 5 | ❌ **0%** | MEDIUM | 4-5h |
| **AdminModerationController** | 2 | ❌ **0%** | MEDIUM | 3-4h |

**Test infrastructure dependencies:**
- MockMvc: Simulates HTTP without server startup
- JwtTokenService: Token generation for auth tests
- H2 database: In-memory DB for integration tests
- Spring Security Test: Auth utilities
- TestContainers: Docker-based MySQL (new dependency)

**High-impact considerations:**
- Controller tests complement service/repository tests (different bug classes)
- Security tests validate boundaries that service tests can't reach
- Test code refactoring should not break existing tests
- TestContainers adds ~10-15 seconds to test suite startup

## Risk Assessment

### Critical Gaps (Must Fix)

**1. Security Boundary Gaps (HIGH PRIORITY)**
- ❌ CORS preflight request handling (OPTIONS requests)
- ❌ RBAC enforcement (`@PreAuthorize` annotations)
- ❌ Rate limiting at exact threshold (10th vs 11th request)
- ❌ Cookie security attributes (HttpOnly, Secure, SameSite)
- ❌ CSRF token validation (if enabled for stateful endpoints)
- ✅ JWT token validation (well-tested)

**2. Controller-Specific Risks (HIGH PRIORITY)**
- **AuthController:**
  - OAuth code validation bypass
  - JWT token claims incorrect (missing roles, wrong expiration)
  - Account reactivation flow (deleted user returns)
  - Rate limiting by IP (untrusted proxy bypass)
- **CommentController:**
  - Authorization bypass (edit someone else's comment)
  - Threading depth calculation error (depth 6 not flattened)
  - Notification integration failure (no notification created)
  - Rate limiting per user (10 ops/min not enforced)
- **NotificationController:**
  - User isolation (accessing another user's notifications)
  - Pagination enforcement (page size > 100 not clamped)
  - Soft-delete filter (deleted notifications still appear)
- **AdminModerationController:**
  - RBAC bypass (regular user accesses admin endpoints)
  - Vote preservation (votes deleted on hide)
  - Audit trail (moderator ID not recorded)

**3. Test Code Quality Risks (MEDIUM PRIORITY)**
- **Code Duplication (~500 lines):**
  - Every test class has identical setup: user creation, planner creation, JWT generation
  - Changes to `User` entity require updating 15+ test files
  - Inconsistent test data (different defaults across tests)
- **Solution:** Extract to `TestDataFactory.java`

**4. Database Dialect Risks (MEDIUM PRIORITY)**
- **H2 vs MySQL differences:**
  - Locking behavior (pessimistic vs optimistic)
  - Date/time precision (milliseconds vs microseconds)
  - String collation (case sensitivity)
  - Transaction isolation level defaults
  - UNIQUE constraint error messages
- **Production bugs caught only in MySQL:**
  - Deadlock detection thresholds
  - Foreign key constraint timing (deferred vs immediate)
  - Query optimizer hints (H2 ignores, MySQL uses)

**5. Constraint Validation Gaps (MEDIUM PRIORITY)**
- Repository tests don't explicitly test:
  - FK violation (invalid planner ID in comment)
  - UNIQUE violation (duplicate vote)
  - NOT NULL violation (missing required field)
- Current approach: Constraints tested implicitly via service tests
- Risk: Constraint changes not validated

### Edge Cases Not Yet Defined
- [ ] Concurrent login from same user (multiple devices)
- [ ] OAuth code replay attack (use code twice)
- [ ] Comment threading at exact depth 6 (should become sibling)
- [ ] Notification for deleted content (planner deleted after notification)
- [ ] Rate limit bucket refill timing (edge of time window)
- [ ] Page size = 0 or negative (should clamp or error?)
- [ ] JWT token at exact expiration boundary (millisecond precision)

### Performance Concerns
- **Current test suite:** Unknown (no measurement)
- **Target:** < 2 minutes for full suite
- **TestContainers impact:** +10-15 seconds for MySQL startup (acceptable)
- **Concurrent tests:** VoteNotificationFlowTest uses threads (flakiness risk on slow CI)

### Backward Compatibility
- **Test changes only:** No production code impact
- **Test data migration:** If User entity changes, update TestDataFactory
- **API contract validation:** Tests should validate existing contracts, not change them

### Security Considerations
- **Test credentials:** Use dummy OAuth tokens, never real credentials
- **Test data isolation:** Each test should clean up (via `@Transactional` rollback)
- **Rate limiting in tests:** Reset buckets between tests to avoid interference
- **CORS testing:** Don't accidentally enable `allowedOrigins("*")` in test config

## Testing Guidelines

### Priority 1: Complete Controller Test Coverage (18-23 hours total)

#### AuthControllerTest - 6-8 hours
**Critical endpoints:**
- `POST /api/auth/google/callback` - OAuth flow, JWT generation, cookie security
- `GET /api/auth/me` - Current user lookup with token validation

**Test structure:**
```java
@SpringBootTest(webEnvironment = MOCK)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class AuthControllerTest {
    @Autowired private MockMvc mockMvc;
    @Autowired private JwtTokenService jwtTokenService;

    @Nested
    @DisplayName("POST /api/auth/google/callback - OAuth Callback")
    class GoogleCallbackTests {
        @Test void callback_ValidCode_Returns200WithJWT() { /* ... */ }
        @Test void callback_InvalidCode_Returns401() { /* ... */ }
        @Test void callback_NewUser_CreatesAccountWithRandomUsername() { /* ... */ }
        @Test void callback_DeletedUser_ReactivatesAccount() { /* V020 feature */ }
        @Test void callback_ExceedsRateLimit_Returns429() { /* by IP */ }
        @Test void callback_SetsCookies_WithSecureFlags() { /* HttpOnly, Secure, SameSite */ }
    }

    @Nested
    @DisplayName("GET /api/auth/me - Current User")
    class GetCurrentUserTests {
        @Test void me_ValidToken_Returns200WithUser() { /* ... */ }
        @Test void me_NoToken_Returns401() { /* ... */ }
        @Test void me_ExpiredToken_Returns401() { /* ... */ }
    }
}
```

**Cookie security verification:**
```java
mockMvc.perform(post("/api/auth/google/callback")
        .contentType(APPLICATION_JSON)
        .content(requestJson))
        .andExpect(status().isOk())
        .andExpect(cookie().exists("access_token"))
        .andExpect(cookie().httpOnly("access_token", true))
        .andExpect(cookie().secure("access_token", true))
        .andExpect(cookie().sameSite("access_token", "Lax"));
```

---

#### CommentControllerTest - 5-6 hours
**Critical endpoints:**
- `GET /api/planner/{id}/comments` - List comments (public access)
- `POST /api/planner/{id}/comments` - Create comment (auth required)
- `PUT /api/comments/{id}` - Edit comment (owner only)
- `DELETE /api/comments/{id}` - Delete comment (owner only)
- `POST /api/comments/{id}/upvote` - Toggle upvote (auth required)

**Authorization boundary test pattern:**
```java
@Test
void updateComment_NotOwner_Returns403() throws Exception {
    // testUser creates comment
    Long commentId = createTestComment(testUser, testPlanner);

    // otherUser tries to edit
    String otherUserToken = generateToken(otherUser);

    mockMvc.perform(put("/api/comments/{id}", commentId)
            .cookie(new Cookie("access_token", otherUserToken))
            .contentType(APPLICATION_JSON)
            .content(updateRequest))
            .andExpect(status().isForbidden());
}
```

**Threading depth test:**
```java
@Test
void createComment_Depth5Reply_BecomesDepth5Sibling() throws Exception {
    // Create depth 0-4 comments
    Long depth0 = createComment(null);
    Long depth1 = createReply(depth0);
    Long depth2 = createReply(depth1);
    Long depth3 = createReply(depth2);
    Long depth4 = createReply(depth3);

    // Depth 5 should flatten (become sibling of depth4)
    mockMvc.perform(post("/api/planner/{id}/comments", plannerId)
            .cookie(accessTokenCookie())
            .content(replyRequest(depth4)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.depth").value(5))
            .andExpect(jsonPath("$.parentCommentId").value(depth3)); // Sibling of depth4!
}
```

---

#### NotificationControllerTest - 4-5 hours
**Critical endpoints:**
- `GET /api/notifications/inbox` - Paginated inbox
- `GET /api/notifications/unread-count` - Polling endpoint
- `POST /api/notifications/{id}/mark-read` - Mark single as read
- `POST /api/notifications/mark-all-read` - Bulk mark
- `DELETE /api/notifications/{id}` - Soft-delete

**Pagination enforcement test:**
```java
@Test
void inbox_PageSizeOver100_ClampsTo100() throws Exception {
    mockMvc.perform(get("/api/notifications/inbox")
            .cookie(accessTokenCookie())
            .param("size", "1000"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content.length()").value(lessThanOrEqualTo(100)));
}
```

---

#### AdminModerationControllerTest - 3-4 hours
**Critical endpoints:**
- `POST /api/admin/planner/{id}/hide-from-recommended` - Hide planner (RBAC)
- `POST /api/admin/planner/{id}/unhide-from-recommended` - Unhide planner (RBAC)

**RBAC enforcement test:**
```java
@Test
void hide_RegularUser_Returns403() throws Exception {
    User regularUser = createUser("user@example.com"); // No admin role
    String userToken = generateToken(regularUser);

    mockMvc.perform(post("/api/admin/planner/{id}/hide-from-recommended", plannerId)
            .cookie(new Cookie("access_token", userToken))
            .contentType(APPLICATION_JSON)
            .content(hideRequest))
            .andExpect(status().isForbidden());
}

@Test
void hide_AdminRole_Returns200() throws Exception {
    User adminUser = createAdmin("admin@example.com"); // ROLE_ADMIN
    String adminToken = generateToken(adminUser);

    mockMvc.perform(post("/api/admin/planner/{id}/hide-from-recommended", plannerId)
            .cookie(new Cookie("access_token", adminToken))
            .contentType(APPLICATION_JSON)
            .content(hideRequest))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.hiddenFromRecommended").value(true));
}
```

---

### Priority 2: Extract Test Code Duplication (2-3 hours)

**Create TestDataFactory:**
```java
// /backend/src/test/java/org/danteplanner/backend/support/TestDataFactory.java

public class TestDataFactory {

    public static User createTestUser(UserRepository repo, String email) {
        User user = User.builder()
                .email(email)
                .provider("google")
                .providerId("google-" + UUID.randomUUID())
                .usernameKeyword("W_CORP")
                .usernameSuffix("test1")
                .build();
        return repo.save(user);
    }

    public static User createAdmin(UserRepository repo, String email) {
        User admin = createTestUser(repo, email);
        admin.setRole("ROLE_ADMIN");
        return repo.save(admin);
    }

    public static Planner createTestPlanner(
            PlannerRepository repo,
            User owner,
            boolean published) {

        Planner planner = Planner.builder()
                .id(UUID.randomUUID())
                .user(owner)
                .title("Test Planner")
                .plannerType(PlannerType.MIRROR_DUNGEON)
                .contentVersion("MD6")
                .published(published)
                .content("{\"test\":\"data\"}")
                .build();
        return repo.save(planner);
    }

    public static String generateAccessToken(JwtTokenService service, User user) {
        return service.generateAccessToken(user.getId(), user.getEmail());
    }
}
```

**Usage in tests:**
```java
@BeforeEach
void setUp() {
    // OLD WAY (duplicated everywhere):
    // User user = User.builder()
    //         .email("test@example.com")
    //         .provider("google")
    //         ... 10 more lines ...
    //         .build();
    // user = userRepository.save(user);

    // NEW WAY (DRY):
    testUser = TestDataFactory.createTestUser(userRepository, "test@example.com");
    testPlanner = TestDataFactory.createTestPlanner(plannerRepository, testUser, true);
    accessToken = TestDataFactory.generateAccessToken(jwtTokenService, testUser);
}
```

**Refactoring strategy:**
1. Create `TestDataFactory` with factory methods
2. Update `PlannerControllerTest` to use factory (validate it works)
3. Roll out to other test files incrementally
4. Measure: Should reduce test code by 30-40%

---

### Priority 3: Expand Security Test Coverage (4-5 hours)

**Create SecurityIntegrationTest:**
```java
@SpringBootTest(webEnvironment = MOCK)
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SecurityIntegrationTest {

    @Autowired private MockMvc mockMvc;

    @Nested
    @DisplayName("CORS Security")
    class CorsTests {

        @Test
        void preflight_AllowedOrigin_Returns200() throws Exception {
            mockMvc.perform(options("/api/planner/md")
                    .header("Origin", "https://limbusplanner.com")
                    .header("Access-Control-Request-Method", "POST"))
                    .andExpect(status().isOk())
                    .andExpect(header().string("Access-Control-Allow-Origin",
                            "https://limbusplanner.com"));
        }

        @Test
        void preflight_DisallowedOrigin_NoHeaders() throws Exception {
            mockMvc.perform(options("/api/planner/md")
                    .header("Origin", "https://evil.com")
                    .header("Access-Control-Request-Method", "POST"))
                    .andExpect(status().isForbidden());
        }
    }

    @Nested
    @DisplayName("RBAC Enforcement")
    class RbacTests {

        @Test
        @WithMockUser(roles = "USER")
        void adminEndpoint_RegularUser_Returns403() throws Exception {
            mockMvc.perform(post("/api/admin/planner/{id}/hide-from-recommended", UUID.randomUUID()))
                    .andExpect(status().isForbidden());
        }

        @Test
        @WithMockUser(roles = "ADMIN")
        void adminEndpoint_AdminUser_Returns200OrOther() throws Exception {
            mockMvc.perform(post("/api/admin/planner/{id}/hide-from-recommended", testPlannerId))
                    .andExpect(status().isNot(403)); // Not forbidden
        }
    }

    @Nested
    @DisplayName("Rate Limiting")
    class RateLimitTests {

        @Test
        void authEndpoint_ExceedsLimit_Returns429() throws Exception {
            String clientIp = "192.168.1.100";

            // Exhaust rate limit (20 requests per minute for auth)
            for (int i = 0; i < 21; i++) {
                ResultActions result = mockMvc.perform(
                        post("/api/auth/google/callback")
                                .header("X-Forwarded-For", clientIp)
                                .contentType(APPLICATION_JSON)
                                .content(oauthRequest));

                if (i < 20) {
                    result.andExpect(status().isNot(429));
                } else {
                    result.andExpect(status().isTooManyRequests());
                }
            }
        }
    }
}
```

---

### Priority 4: Add TestContainers MySQL Tests (6-8 hours)

**Add dependencies to pom.xml:**
```xml
<dependency>
    <groupId>org.testcontainers</groupId>
    <artifactId>testcontainers</artifactId>
    <version>1.19.3</version>
    <scope>test</scope>
</dependency>
<dependency>
    <groupId>org.testcontainers</groupId>
    <artifactId>mysql</artifactId>
    <version>1.19.3</version>
    <scope>test</scope>
</dependency>
<dependency>
    <groupId>org.testcontainers</groupId>
    <artifactId>junit-jupiter</artifactId>
    <version>1.19.3</version>
    <scope>test</scope>
</dependency>
```

**Create application-it.properties:**
```properties
# TestContainers will override these dynamically
spring.datasource.url=jdbc:tc:mysql:8.0:///testdb
spring.datasource.driver-class-name=org.testcontainers.jdbc.ContainerDatabaseDriver
spring.jpa.hibernate.ddl-auto=validate
spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.MySQLDialect
```

**Create MySQLIntegrationTest:**
```java
@SpringBootTest
@ActiveProfiles("it") // Use application-it.properties
@Testcontainers
@Tag("containerized") // Separate CI job
class MySQLIntegrationTest {

    @Container
    static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.0")
            .withDatabaseName("testdb")
            .withUsername("test")
            .withPassword("test");

    @DynamicPropertySource
    static void setProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", mysql::getJdbcUrl);
        registry.add("spring.datasource.username", mysql::getUsername);
        registry.add("spring.datasource.password", mysql::getPassword);
    }

    @Autowired private PlannerRepository plannerRepository;
    @Autowired private EntityManager entityManager;

    @Test
    @DisplayName("MySQL: Atomic increment handles concurrent votes correctly")
    void atomicIncrement_ConcurrentVotes_NoLostUpdates() throws Exception {
        // Create planner with 0 upvotes
        Planner planner = createTestPlanner();
        plannerRepository.save(planner);
        entityManager.flush();
        entityManager.clear();

        // Concurrent vote operations
        ExecutorService executor = Executors.newFixedThreadPool(10);
        CountDownLatch latch = new CountDownLatch(1);

        for (int i = 0; i < 10; i++) {
            executor.submit(() -> {
                try {
                    latch.await();
                    plannerRepository.incrementUpvotes(planner.getId());
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            });
        }

        latch.countDown(); // Start all threads
        executor.shutdown();
        executor.awaitTermination(10, TimeUnit.SECONDS);

        // Verify: All 10 votes counted (no lost updates)
        entityManager.clear();
        Planner updated = plannerRepository.findById(planner.getId()).orElseThrow();
        assertEquals(10, updated.getUpvotes(), "MySQL should handle concurrent increments atomically");
    }

    @Test
    @DisplayName("MySQL: UNIQUE constraint on votes prevents duplicates")
    void uniqueConstraint_DuplicateVote_ThrowsException() {
        User user = createTestUser();
        Planner planner = createTestPlanner();

        // First vote succeeds
        PlannerVote vote1 = new PlannerVote(user.getId(), planner.getId(), VoteType.UP);
        voteRepository.save(vote1);

        // Duplicate vote fails
        PlannerVote vote2 = new PlannerVote(user.getId(), planner.getId(), VoteType.UP);
        assertThrows(DataIntegrityViolationException.class, () -> {
            voteRepository.save(vote2);
            entityManager.flush();
        });
    }
}
```

**When to use TestContainers:**
- Atomic operations (concurrent votes, notifications)
- Constraint validation (UNIQUE, FK violations)
- Transaction isolation (race conditions)
- Date/time handling (precision differences)
- NOT for: Simple CRUD, service layer tests, controller tests

---

### Priority 5: Add Database Constraint Tests (3-4 hours)

**Create PlannerRepositoryConstraintTest:**
```java
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class PlannerRepositoryConstraintTest {

    @Autowired private PlannerRepository plannerRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private EntityManager entityManager;

    @Nested
    @DisplayName("Foreign Key Constraints")
    class ForeignKeyTests {

        @Test
        void fkViolation_InvalidUserId_ThrowsException() {
            Planner planner = Planner.builder()
                    .id(UUID.randomUUID())
                    .user(User.builder().id(999999L).build()) // Non-existent user
                    .title("Test")
                    .build();

            assertThrows(DataIntegrityViolationException.class, () -> {
                plannerRepository.save(planner);
                entityManager.flush();
            });
        }
    }

    @Nested
    @DisplayName("NOT NULL Constraints")
    class NotNullTests {

        @Test
        void notNullViolation_MissingTitle_ThrowsException() {
            User user = createTestUser();
            Planner planner = Planner.builder()
                    .id(UUID.randomUUID())
                    .user(user)
                    // .title(null) - missing required field
                    .build();

            assertThrows(DataIntegrityViolationException.class, () -> {
                plannerRepository.save(planner);
                entityManager.flush();
            });
        }
    }

    @Nested
    @DisplayName("UNIQUE Constraints")
    class UniqueConstraintTests {

        @Test
        void uniqueViolation_DuplicateVote_ThrowsException() {
            User user = createTestUser();
            Planner planner = createTestPlanner(user);

            // First vote succeeds
            PlannerVote vote1 = new PlannerVote(user.getId(), planner.getId(), VoteType.UP);
            voteRepository.save(vote1);
            entityManager.flush();

            // Duplicate vote fails (UNIQUE constraint on user_id + planner_id)
            PlannerVote vote2 = new PlannerVote(user.getId(), planner.getId(), VoteType.UP);
            assertThrows(DataIntegrityViolationException.class, () -> {
                voteRepository.save(vote2);
                entityManager.flush();
            });
        }
    }
}
```

---

## Additional Infrastructure Improvements

### Add JaCoCo Coverage Reports (1-2 hours)
**Add to pom.xml:**
```xml
<plugin>
    <groupId>org.jacoco</groupId>
    <artifactId>jacoco-maven-plugin</artifactId>
    <version>0.8.11</version>
    <executions>
        <execution>
            <id>prepare-agent</id>
            <goals><goal>prepare-agent</goal></goals>
        </execution>
        <execution>
            <id>report</id>
            <phase>test</phase>
            <goals><goal>report</goal></goals>
        </execution>
        <execution>
            <id>check</id>
            <goals><goal>check</goal></goals>
            <configuration>
                <rules>
                    <rule>
                        <element>PACKAGE</element>
                        <limits>
                            <limit>
                                <counter>LINE</counter>
                                <value>COVEREDRATIO</value>
                                <minimum>0.80</minimum>
                            </limit>
                        </limits>
                    </rule>
                </rules>
            </configuration>
        </execution>
    </executions>
</plugin>
```

**Usage:**
```bash
mvn clean test jacoco:report
# Open target/site/jacoco/index.html
```

---

## Success Criteria

**Priority 1: Controller Tests**
- [ ] AuthControllerTest: 15+ test cases covering OAuth, JWT, cookies
- [ ] CommentControllerTest: 20+ test cases covering CRUD, threading, voting
- [ ] NotificationControllerTest: 15+ test cases covering inbox, pagination, auth
- [ ] AdminModerationControllerTest: 10+ test cases covering RBAC, hide/unhide
- [ ] All tests follow existing pattern (nested classes, descriptive names)
- [ ] Test suite runs in under 3 minutes

**Priority 2: Test Refactoring**
- [ ] TestDataFactory created with user/planner/token factories
- [ ] At least 3 existing test files refactored to use factory
- [ ] Test code reduced by 30-40% (measured by line count)

**Priority 3: Security Tests**
- [ ] SecurityIntegrationTest created with CORS, RBAC, rate limit tests
- [ ] CORS preflight requests tested (OPTIONS method)
- [ ] RBAC boundaries tested (`@WithMockUser` pattern)
- [ ] Rate limiting tested at exact threshold

**Priority 4: TestContainers**
- [ ] TestContainers MySQL dependency added
- [ ] application-it.properties created
- [ ] MySQLIntegrationTest created with 3+ critical tests (atomic ops, constraints)
- [ ] Tests tagged with `@Tag("containerized")` for CI separation

**Priority 5: Constraint Tests**
- [ ] PlannerRepositoryConstraintTest created
- [ ] FK, UNIQUE, NOT NULL violations tested explicitly
- [ ] Tests use `entityManager.flush()` to force constraint checks

**Optional (Nice-to-Have):**
- [ ] JaCoCo coverage report configured (mvn jacoco:report)
- [ ] Coverage > 80% for all packages
- [ ] PITest mutation testing configured (optional)

---

## Common Patterns Reference

### Test Class Structure
```java
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class ControllerNameTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private JwtTokenService jwtTokenService;

    private User testUser;
    private String accessToken;

    @BeforeEach
    void setUp() {
        testUser = TestDataFactory.createTestUser(userRepository, "test@example.com");
        accessToken = TestDataFactory.generateAccessToken(jwtTokenService, testUser);
    }

    @Nested
    @DisplayName("HTTP_METHOD /api/path - Description")
    class FeatureTests {
        @Test void testCase() { /* ... */ }
    }
}
```

### Rate Limiting Test Pattern
```java
// Exhaust rate limit bucket
for (int i = 0; i < 11; i++) {
    ResultActions result = mockMvc.perform(post("/api/endpoint")
            .cookie(accessTokenCookie()));

    if (i < 10) {
        result.andExpect(status().isNot(429));
    } else {
        result.andExpect(status().isTooManyRequests());
    }
}
```

### Concurrent Test Pattern
```java
ExecutorService executor = Executors.newFixedThreadPool(5);
CountDownLatch latch = new CountDownLatch(1);

for (int i = 0; i < 5; i++) {
    executor.submit(() -> {
        latch.await();
        // Perform operation
    });
}

latch.countDown(); // Synchronize start
executor.awaitTermination(10, TimeUnit.SECONDS);
```

### Authorization Boundary Pattern
```java
@Test
void operation_NotOwner_Returns403() throws Exception {
    Long resourceId = createResourceOwnedBy(testUser);
    String otherUserToken = TestDataFactory.generateAccessToken(jwtTokenService, otherUser);

    mockMvc.perform(put("/api/resource/{id}", resourceId)
            .cookie(new Cookie("access_token", otherUserToken)))
            .andExpect(status().isForbidden());
}
```
