# Testing Guide – Enterprise Spring Boot Testing

Production-ready guide to testing backend services using **JUnit 5**, **Mockito**, **Testcontainers**, **MockMvc**, and **Spring Boot Test** with comprehensive patterns for unit, integration, and end-to-end testing.

## Table of Contents

* [Testing Architecture](#testing-architecture)
* [Unit Testing Patterns](#unit-testing-patterns)
* [Integration Testing with Testcontainers](#integration-testing-with-testcontainers)
* [Controller Testing with MockMvc](#controller-testing-with-mockmvc)
* [Repository Testing](#repository-testing)
* [Testing Security](#testing-security)
* [Test Data Management](#test-data-management)
* [Testing Async and Events](#testing-async-and-events)
* [Test Configuration](#test-configuration)
* [Coverage and Best Practices](#coverage-and-best-practices)

---

## Testing Architecture

### Test Pyramid

```
           ┌───────────┐
           │    E2E    │  ← Few, slow, cover critical flows
           ├───────────┤
           │Integration│  ← More, medium speed, real dependencies
           ├───────────┤
           │   Unit    │  ← Many, fast, isolated logic
           └───────────┘
```

### Test Types and When to Use

| Test Type | Scope | Speed | When to Use |
|-----------|-------|-------|-------------|
| **Unit** | Single class | < 10ms | Business logic, calculations |
| **Slice** | Layer (web/data) | < 500ms | Controllers, repositories |
| **Integration** | Full context | 1-5s | Service orchestration |
| **E2E** | Deployed app | 5-30s | Critical user flows |

### Naming Convention

```java
// Method name pattern: methodUnderTest_scenario_expectedResult
@Test
void createUser_withValidData_persistsAndReturnsUser() { }

@Test
void createUser_withDuplicateEmail_throwsConflictException() { }

@Test
void getUser_whenNotFound_throwsNotFoundException() { }
```

---

## Unit Testing Patterns

### Service Unit Test

```java
@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private UserService userService;

    @Captor
    private ArgumentCaptor<User> userCaptor;

    @Captor
    private ArgumentCaptor<UserCreatedEvent> eventCaptor;

    @Test
    void createUser_withValidData_persistsAndPublishesEvent() {
        // Given
        CreateUserCommand command = new CreateUserCommand(
            "test@example.com",
            "password123",
            "John Doe"
        );

        when(userRepository.existsByEmail(command.email())).thenReturn(false);
        when(passwordEncoder.encode(command.password())).thenReturn("encoded-password");
        when(userRepository.persist(any(User.class))).thenAnswer(invocation -> {
            User user = invocation.getArgument(0);
            ReflectionTestUtils.setField(user, "id", 1L);
            return user;
        });

        // When
        UserDto result = userService.createUser(command);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.id()).isEqualTo(1L);
        assertThat(result.email()).isEqualTo("test@example.com");

        // Verify persistence
        verify(userRepository).persist(userCaptor.capture());
        User savedUser = userCaptor.getValue();
        assertThat(savedUser.getEmail()).isEqualTo("test@example.com");
        assertThat(savedUser.getPasswordHash()).isEqualTo("encoded-password");

        // Verify event published
        verify(eventPublisher).publishEvent(eventCaptor.capture());
        UserCreatedEvent event = eventCaptor.getValue();
        assertThat(event.userId()).isEqualTo(1L);
    }

    @Test
    void createUser_withDuplicateEmail_throwsConflictException() {
        // Given
        CreateUserCommand command = new CreateUserCommand(
            "existing@example.com",
            "password123",
            "John Doe"
        );

        when(userRepository.existsByEmail(command.email())).thenReturn(true);

        // When & Then
        assertThatThrownBy(() -> userService.createUser(command))
            .isInstanceOf(ConflictException.class)
            .hasMessageContaining("email already exists");

        verify(userRepository, never()).persist(any());
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void getUser_whenExists_returnsUser() {
        // Given
        Long userId = 1L;
        User user = createTestUser(userId, "test@example.com");

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        // When
        UserDto result = userService.getUser(userId);

        // Then
        assertThat(result.id()).isEqualTo(userId);
        assertThat(result.email()).isEqualTo("test@example.com");
    }

    @Test
    void getUser_whenNotFound_throwsNotFoundException() {
        // Given
        Long userId = 999L;
        when(userRepository.findById(userId)).thenReturn(Optional.empty());

        // When & Then
        assertThatThrownBy(() -> userService.getUser(userId))
            .isInstanceOf(NotFoundException.class)
            .hasMessageContaining("User")
            .hasMessageContaining("999");
    }

    private User createTestUser(Long id, String email) {
        User user = new User(email, "encoded-password", "Test User");
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }
}
```

### Domain Object Unit Test

```java
class OrderTest {

    @Test
    void addItem_withValidProduct_updatesTotal() {
        // Given
        Order order = new Order(1L);
        Product product = new Product("SKU-001", "Widget", BigDecimal.valueOf(10.00));

        // When
        order.addItem(product, 3);

        // Then
        assertThat(order.getItems()).hasSize(1);
        assertThat(order.getTotal()).isEqualByComparingTo(BigDecimal.valueOf(30.00));
    }

    @Test
    void addItem_withZeroQuantity_throwsException() {
        Order order = new Order(1L);
        Product product = new Product("SKU-001", "Widget", BigDecimal.valueOf(10.00));

        assertThatThrownBy(() -> order.addItem(product, 0))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Quantity must be positive");
    }

    @Test
    void complete_withEmptyOrder_throwsException() {
        Order order = new Order(1L);

        assertThatThrownBy(() -> order.complete())
            .isInstanceOf(BusinessException.class)
            .hasFieldOrPropertyWithValue("errorCode", "ORDER_EMPTY");
    }

    @Test
    void complete_withItems_setsStatusToCompleted() {
        // Given
        Order order = new Order(1L);
        order.addItem(new Product("SKU-001", "Widget", BigDecimal.TEN), 1);

        // When
        order.complete();

        // Then
        assertThat(order.getStatus()).isEqualTo(OrderStatus.COMPLETED);
        assertThat(order.getCompletedAt()).isNotNull();
    }
}
```

### Parameterized Tests

```java
class ValidationUtilsTest {

    @ParameterizedTest
    @ValueSource(strings = {"test@example.com", "user@domain.org", "name@sub.domain.com"})
    void isValidEmail_withValidEmails_returnsTrue(String email) {
        assertThat(ValidationUtils.isValidEmail(email)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"invalid", "no-at-sign.com", "@nodomain", "spaces in@email.com"})
    void isValidEmail_withInvalidEmails_returnsFalse(String email) {
        assertThat(ValidationUtils.isValidEmail(email)).isFalse();
    }

    @ParameterizedTest
    @CsvSource({
        "100, 10, 10",
        "50, 7, 8",
        "1, 1, 1",
        "0, 10, 0"
    })
    void calculatePages_withVariousInputs_returnsCorrectPageCount(
            int totalItems, int pageSize, int expectedPages) {

        assertThat(PaginationUtils.calculatePages(totalItems, pageSize))
            .isEqualTo(expectedPages);
    }

    @ParameterizedTest
    @MethodSource("provideUserRoleScenarios")
    void hasPermission_withVariousRoles_returnsExpectedResult(
            Set<Role> roles, Permission permission, boolean expected) {

        User user = new User();
        user.setRoles(roles);

        assertThat(user.hasPermission(permission)).isEqualTo(expected);
    }

    private static Stream<Arguments> provideUserRoleScenarios() {
        return Stream.of(
            Arguments.of(Set.of(Role.ADMIN), Permission.DELETE_USER, true),
            Arguments.of(Set.of(Role.USER), Permission.DELETE_USER, false),
            Arguments.of(Set.of(Role.MODERATOR), Permission.DELETE_POST, true),
            Arguments.of(Set.of(), Permission.READ_POST, false)
        );
    }
}
```

---

## Integration Testing with Testcontainers

### Base Test Configuration

```java
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Testcontainers
public @interface IntegrationTest {
}

/**
 * Abstract base class for integration tests.
 * Provides shared Testcontainers setup and utility methods.
 */
@IntegrationTest
public abstract class AbstractIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
        .withDatabaseName("testdb")
        .withUsername("test")
        .withPassword("test")
        .withReuse(true);

    @Container
    @ServiceConnection
    static GenericContainer<?> redis = new GenericContainer<>("redis:7-alpine")
        .withExposedPorts(6379)
        .withReuse(true);

    @Autowired
    protected TestRestTemplate restTemplate;

    @Autowired
    protected JdbcTemplate jdbcTemplate;

    @BeforeEach
    void cleanDatabase() {
        // Clean test data between tests
        jdbcTemplate.execute("TRUNCATE TABLE users, orders, order_items CASCADE");
    }

    protected HttpHeaders createAuthHeaders(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
    }

    protected String createTestUserAndGetToken() {
        // Create test user and return JWT token
        return "test-jwt-token";
    }
}
```

### Service Integration Test

```java
@IntegrationTest
class UserServiceIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private UserService userService;

    @Autowired
    private UserRepository userRepository;

    @Test
    void createUser_withValidData_persistsToDatabase() {
        // Given
        CreateUserCommand command = new CreateUserCommand(
            "integration@test.com",
            "SecurePass123!",
            "Integration Test"
        );

        // When
        UserDto result = userService.createUser(command);

        // Then
        assertThat(result.id()).isNotNull();

        // Verify database state
        User persisted = userRepository.findById(result.id()).orElseThrow();
        assertThat(persisted.getEmail()).isEqualTo("integration@test.com");
        assertThat(persisted.getName()).isEqualTo("Integration Test");
        assertThat(persisted.getPasswordHash()).isNotEqualTo("SecurePass123!"); // Encoded
    }

    @Test
    void createUser_withDuplicateEmail_rollsBackTransaction() {
        // Given
        CreateUserCommand command = new CreateUserCommand(
            "duplicate@test.com",
            "SecurePass123!",
            "First User"
        );
        userService.createUser(command);

        // When
        CreateUserCommand duplicateCommand = new CreateUserCommand(
            "duplicate@test.com",
            "SecurePass123!",
            "Second User"
        );

        // Then
        assertThatThrownBy(() -> userService.createUser(duplicateCommand))
            .isInstanceOf(ConflictException.class);

        // Verify only first user exists
        assertThat(userRepository.countByEmail("duplicate@test.com")).isEqualTo(1);
    }

    @Test
    @Transactional
    void updateUser_withOptimisticLock_handlesConflict() {
        // Given
        User user = userRepository.persist(new User("lock@test.com", "hash", "Test"));
        Long userId = user.getId();

        // Simulate concurrent modification
        jdbcTemplate.update(
            "UPDATE users SET version = version + 1, name = 'Modified' WHERE id = ?",
            userId
        );

        // When & Then
        UpdateUserCommand command = new UpdateUserCommand("New Name", null, null);
        assertThatThrownBy(() -> userService.updateUser(userId, command))
            .isInstanceOf(OptimisticLockingFailureException.class);
    }
}
```

### Full API Integration Test

```java
@IntegrationTest
class UserApiIntegrationTest extends AbstractIntegrationTest {

    @Test
    void fullUserLifecycle() {
        // 1. Create user
        CreateUserRequest createRequest = new CreateUserRequest(
            "lifecycle@test.com",
            "SecurePass123!",
            "Lifecycle Test"
        );

        ResponseEntity<ApiResponse<UserDto>> createResponse = restTemplate.exchange(
            "/api/v1/users",
            HttpMethod.POST,
            new HttpEntity<>(createRequest),
            new ParameterizedTypeReference<>() {}
        );

        assertThat(createResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        Long userId = createResponse.getBody().getData().id();

        // 2. Get user
        String token = loginAndGetToken("lifecycle@test.com", "SecurePass123!");

        ResponseEntity<ApiResponse<UserDto>> getResponse = restTemplate.exchange(
            "/api/v1/users/{id}",
            HttpMethod.GET,
            new HttpEntity<>(createAuthHeaders(token)),
            new ParameterizedTypeReference<>() {},
            userId
        );

        assertThat(getResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(getResponse.getBody().getData().email()).isEqualTo("lifecycle@test.com");

        // 3. Update user
        UpdateUserRequest updateRequest = new UpdateUserRequest("Updated Name", null, null);

        ResponseEntity<ApiResponse<UserDto>> updateResponse = restTemplate.exchange(
            "/api/v1/users/{id}",
            HttpMethod.PUT,
            new HttpEntity<>(updateRequest, createAuthHeaders(token)),
            new ParameterizedTypeReference<>() {},
            userId
        );

        assertThat(updateResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(updateResponse.getBody().getData().name()).isEqualTo("Updated Name");

        // 4. Delete user
        ResponseEntity<Void> deleteResponse = restTemplate.exchange(
            "/api/v1/users/{id}",
            HttpMethod.DELETE,
            new HttpEntity<>(createAuthHeaders(token)),
            Void.class,
            userId
        );

        assertThat(deleteResponse.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        // 5. Verify deletion
        ResponseEntity<ApiResponse<UserDto>> verifyResponse = restTemplate.exchange(
            "/api/v1/users/{id}",
            HttpMethod.GET,
            new HttpEntity<>(createAuthHeaders(token)),
            new ParameterizedTypeReference<>() {},
            userId
        );

        assertThat(verifyResponse.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    private String loginAndGetToken(String email, String password) {
        LoginRequest loginRequest = new LoginRequest(email, password);

        ResponseEntity<AuthResponse> response = restTemplate.postForEntity(
            "/api/v1/auth/login",
            loginRequest,
            AuthResponse.class
        );

        return response.getBody().accessToken();
    }
}
```

---

## Controller Testing with MockMvc

### Controller Unit Test

```java
@WebMvcTest(UserController.class)
@Import({SecurityConfig.class, TestSecurityConfig.class})
class UserControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private UserCommandService commandService;

    @MockBean
    private UserQueryService queryService;

    @Test
    @WithMockUser
    void createUser_withValidRequest_returns201() throws Exception {
        // Given
        CreateUserRequest request = new CreateUserRequest(
            "test@example.com",
            "SecurePass123!",
            "Test User"
        );

        UserDto responseDto = new UserDto(1L, "test@example.com", "Test User");
        when(commandService.createUser(any())).thenReturn(responseDto);

        // When & Then
        mockMvc.perform(post("/api/v1/users")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isCreated())
            .andExpect(header().exists("Location"))
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.id").value(1))
            .andExpect(jsonPath("$.data.email").value("test@example.com"));

        verify(commandService).createUser(any(CreateUserCommand.class));
    }

    @Test
    @WithMockUser
    void createUser_withInvalidEmail_returns400() throws Exception {
        CreateUserRequest request = new CreateUserRequest(
            "invalid-email",
            "SecurePass123!",
            "Test User"
        );

        mockMvc.perform(post("/api/v1/users")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
            .andExpect(jsonPath("$.error.details.fields.email").exists());

        verifyNoInteractions(commandService);
    }

    @Test
    @WithMockUser
    void createUser_withMissingRequiredFields_returns400() throws Exception {
        String json = "{}";

        mockMvc.perform(post("/api/v1/users")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error.details.fields.email").exists())
            .andExpect(jsonPath("$.error.details.fields.password").exists())
            .andExpect(jsonPath("$.error.details.fields.name").exists());
    }

    @Test
    @WithMockUser
    void getUser_whenExists_returns200() throws Exception {
        UserDto user = new UserDto(1L, "test@example.com", "Test User");
        when(queryService.getUser(1L)).thenReturn(user);

        mockMvc.perform(get("/api/v1/users/1"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.id").value(1))
            .andExpect(jsonPath("$.data.email").value("test@example.com"));
    }

    @Test
    @WithMockUser
    void getUser_whenNotFound_returns404() throws Exception {
        when(queryService.getUser(999L))
            .thenThrow(new NotFoundException("User", 999L));

        mockMvc.perform(get("/api/v1/users/999"))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.error.code").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    void getUser_withoutAuth_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/users/1"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(roles = "USER")
    void deleteUser_withoutAdminRole_returns403() throws Exception {
        mockMvc.perform(delete("/api/v1/users/1"))
            .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void deleteUser_withAdminRole_returns204() throws Exception {
        mockMvc.perform(delete("/api/v1/users/1"))
            .andExpect(status().isNoContent());

        verify(commandService).deleteUser(1L);
    }
}
```

---

## Repository Testing

### JPA Repository Test

```java
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
class UserRepositoryTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
        .withReuse(true);

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private UserRepository userRepository;

    @Test
    void findByEmail_whenExists_returnsUser() {
        // Given
        User user = new User("test@example.com", "hash", "Test User");
        entityManager.persistAndFlush(user);

        // When
        Optional<User> found = userRepository.findByEmail("test@example.com");

        // Then
        assertThat(found).isPresent();
        assertThat(found.get().getEmail()).isEqualTo("test@example.com");
    }

    @Test
    void findByEmail_whenNotExists_returnsEmpty() {
        Optional<User> found = userRepository.findByEmail("nonexistent@example.com");
        assertThat(found).isEmpty();
    }

    @Test
    void findWithRolesById_fetchesRolesEagerly() {
        // Given
        User user = new User("test@example.com", "hash", "Test User");
        user.addRole(Role.USER);
        user.addRole(Role.ADMIN);
        entityManager.persistAndFlush(user);
        entityManager.clear(); // Clear persistence context

        // When
        User found = userRepository.findWithRolesById(user.getId()).orElseThrow();

        // Then - roles should be loaded (no lazy loading exception)
        assertThat(found.getRoles()).hasSize(2);
        assertThat(found.getRoles()).contains(Role.USER, Role.ADMIN);
    }

    @Test
    void searchByKeyword_findsMatchingUsers() {
        // Given
        entityManager.persist(new User("john@example.com", "hash", "John Doe"));
        entityManager.persist(new User("jane@example.com", "hash", "Jane Doe"));
        entityManager.persist(new User("bob@example.com", "hash", "Bob Smith"));
        entityManager.flush();

        // When
        Page<User> results = userRepository.searchByKeyword(
            "doe",
            PageRequest.of(0, 10)
        );

        // Then
        assertThat(results.getContent()).hasSize(2);
        assertThat(results.getContent())
            .extracting(User::getName)
            .containsExactlyInAnyOrder("John Doe", "Jane Doe");
    }

    @Test
    void softDelete_setsDeletedFlag() {
        // Given
        User user = new User("delete@example.com", "hash", "To Delete");
        entityManager.persistAndFlush(user);
        Long userId = user.getId();

        // When
        userRepository.softDelete(userId);
        entityManager.flush();
        entityManager.clear();

        // Then - findById should not find soft-deleted user
        assertThat(userRepository.findById(userId)).isEmpty();

        // But direct query should find it
        User deleted = entityManager.find(User.class, userId);
        assertThat(deleted.isDeleted()).isTrue();
    }
}
```

---

## Testing Security

### Security Integration Test

```java
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class SecurityIntegrationTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @BeforeEach
    void setUp() {
        userRepository.deleteAll();
    }

    @Test
    void login_withValidCredentials_returnsToken() {
        // Given
        User user = new User("login@test.com", passwordEncoder.encode("password123"), "Test");
        userRepository.persist(user);

        LoginRequest request = new LoginRequest("login@test.com", "password123");

        // When
        ResponseEntity<AuthResponse> response = restTemplate.postForEntity(
            "/api/v1/auth/login",
            request,
            AuthResponse.class
        );

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().accessToken()).isNotBlank();
        assertThat(response.getBody().tokenType()).isEqualTo("Bearer");
    }

    @Test
    void login_withInvalidPassword_returns401() {
        User user = new User("login@test.com", passwordEncoder.encode("password123"), "Test");
        userRepository.persist(user);

        LoginRequest request = new LoginRequest("login@test.com", "wrongpassword");

        ResponseEntity<ApiError> response = restTemplate.postForEntity(
            "/api/v1/auth/login",
            request,
            ApiError.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void accessProtectedResource_withValidToken_succeeds() {
        // Given
        User user = new User("auth@test.com", passwordEncoder.encode("password"), "Test");
        userRepository.persist(user);

        String token = obtainToken("auth@test.com", "password");

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);

        // When
        ResponseEntity<ApiResponse<UserDto>> response = restTemplate.exchange(
            "/api/v1/users/me",
            HttpMethod.GET,
            new HttpEntity<>(headers),
            new ParameterizedTypeReference<>() {}
        );

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().getData().email()).isEqualTo("auth@test.com");
    }

    @Test
    void accessProtectedResource_withExpiredToken_returns401() {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth("expired-or-invalid-token");

        ResponseEntity<ApiError> response = restTemplate.exchange(
            "/api/v1/users/me",
            HttpMethod.GET,
            new HttpEntity<>(headers),
            ApiError.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    private String obtainToken(String email, String password) {
        LoginRequest request = new LoginRequest(email, password);
        AuthResponse response = restTemplate.postForObject(
            "/api/v1/auth/login",
            request,
            AuthResponse.class
        );
        return response.accessToken();
    }
}
```

### Custom Security Test Annotations

```java
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@WithSecurityContext(factory = WithMockUserSecurityContextFactory.class)
public @interface WithMockAuthUser {
    long id() default 1L;
    String email() default "test@example.com";
    String[] roles() default {"USER"};
}

public class WithMockUserSecurityContextFactory
        implements WithSecurityContextFactory<WithMockAuthUser> {

    @Override
    public SecurityContext createSecurityContext(WithMockAuthUser annotation) {
        SecurityContext context = SecurityContextHolder.createEmptyContext();

        UserPrincipal principal = new UserPrincipal(
            annotation.id(),
            annotation.email(),
            "Test User",
            Arrays.stream(annotation.roles())
                .map(Role::valueOf)
                .collect(Collectors.toSet()),
            true
        );

        Authentication auth = new UsernamePasswordAuthenticationToken(
            principal,
            null,
            principal.getAuthorities()
        );

        context.setAuthentication(auth);
        return context;
    }
}

// Usage
@Test
@WithMockAuthUser(id = 42, email = "admin@test.com", roles = {"ADMIN"})
void adminEndpoint_withAdminUser_succeeds() throws Exception {
    mockMvc.perform(get("/api/v1/admin/dashboard"))
        .andExpect(status().isOk());
}
```

---

## Test Data Management

### Test Fixtures

```java
public class UserFixtures {

    public static User createUser() {
        return createUser("test@example.com");
    }

    public static User createUser(String email) {
        User user = new User(email, "encoded-password", "Test User");
        return user;
    }

    public static User createUserWithId(Long id, String email) {
        User user = createUser(email);
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }

    public static CreateUserCommand createUserCommand() {
        return new CreateUserCommand(
            "test@example.com",
            "SecurePass123!",
            "Test User"
        );
    }

    public static UserDto createUserDto(Long id) {
        return new UserDto(id, "test@example.com", "Test User");
    }
}

// Usage
@Test
void someTest() {
    User user = UserFixtures.createUserWithId(1L, "test@example.com");
    // ...
}
```

### Database Cleaner

```java
@Component
@Profile("test")
@RequiredArgsConstructor
public class DatabaseCleaner {

    private final JdbcTemplate jdbcTemplate;

    @Transactional
    public void clean() {
        jdbcTemplate.execute("SET REFERENTIAL_INTEGRITY FALSE");

        List<String> tables = getTables();
        for (String table : tables) {
            jdbcTemplate.execute("TRUNCATE TABLE " + table);
        }

        jdbcTemplate.execute("SET REFERENTIAL_INTEGRITY TRUE");
    }

    private List<String> getTables() {
        return jdbcTemplate.queryForList(
            "SELECT TABLE_NAME FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_SCHEMA = 'PUBLIC'",
            String.class
        );
    }
}

// Usage in tests
@Autowired
private DatabaseCleaner databaseCleaner;

@BeforeEach
void setUp() {
    databaseCleaner.clean();
}
```

---

## Testing Async and Events

### Async Method Testing

```java
@SpringBootTest
@ActiveProfiles("test")
class AsyncServiceTest {

    @Autowired
    private AsyncService asyncService;

    @MockBean
    private EmailClient emailClient;

    @Test
    void sendNotificationAsync_completesSuccessfully() throws Exception {
        // When
        CompletableFuture<NotificationResult> future =
            asyncService.sendNotificationAsync(1L, new NotificationDto("Test", "Body"));

        // Then - wait for async completion
        NotificationResult result = future.get(5, TimeUnit.SECONDS);
        assertThat(result.isSuccess()).isTrue();

        verify(emailClient).send(any(), any());
    }
}
```

### Event Listener Testing

```java
@SpringBootTest
@ActiveProfiles("test")
class EventListenerTest {

    @Autowired
    private OrderService orderService;

    @MockBean
    private EmailService emailService;

    @Autowired
    private TestEntityManager entityManager;

    @Test
    void completeOrder_triggersEmailEvent() {
        // Given
        Order order = new Order(1L);
        order.addItem(new Product("SKU", "Widget", BigDecimal.TEN), 1);
        entityManager.persist(order);
        entityManager.flush();

        // When
        orderService.completeOrder(order.getId());

        // Then - use Awaitility for async events
        await()
            .atMost(5, TimeUnit.SECONDS)
            .untilAsserted(() ->
                verify(emailService).sendOrderConfirmation(any(), eq(order.getId()))
            );
    }
}
```

---

## Test Configuration

### Test Properties

```yaml
# application-test.yml
spring:
  datasource:
    # Testcontainers will override this
    url: jdbc:tc:postgresql:16-alpine:///testdb

  jpa:
    hibernate:
      ddl-auto: create-drop
    show-sql: false

  cache:
    type: none

app:
  security:
    jwt-secret: test-secret-key-that-is-at-least-32-characters-long
    token-expiration: 1h

logging:
  level:
    org.springframework.test: INFO
    com.example: DEBUG
```

### Test Security Configuration

```java
@TestConfiguration
public class TestSecurityConfig {

    @Bean
    @Primary
    public PasswordEncoder testPasswordEncoder() {
        // Faster encoding for tests
        return new BCryptPasswordEncoder(4);
    }
}
```

---

## Coverage and Best Practices

### Coverage Targets

| Type | Target | Priority |
|------|--------|----------|
| Unit Tests | 80%+ line coverage | High |
| Integration Tests | Critical paths covered | High |
| E2E Tests | Main user flows | Medium |
| Mutation Testing | 60%+ killed mutants | Nice to have |

### Best Practices Summary

| Practice | Description |
|----------|-------------|
| **Arrange-Act-Assert** | Clear test structure |
| **One assertion focus** | Test one behavior per test |
| **Descriptive names** | `method_scenario_expected` pattern |
| **Test independence** | No test should depend on another |
| **Fast feedback** | Unit tests < 10ms each |
| **Real dependencies** | Use Testcontainers for integration |
| **Test data isolation** | Clean data between tests |
| **Avoid test pollution** | Don't modify shared state |

### Running Tests

```bash
# Run all tests
./gradlew test

# Run unit tests only
./gradlew test --tests '*Test'

# Run integration tests only
./gradlew test --tests '*IntegrationTest'

# Run with coverage report
./gradlew test jacocoTestReport

# Run specific test class
./gradlew test --tests UserServiceTest
```

---

**Related Files:**

* [SKILL.md](../SKILL.md) - Main skill guide
* [services-and-repositories.md](services-and-repositories.md) - Service patterns
* [async-and-errors.md](async-and-errors.md) - Async and error handling
