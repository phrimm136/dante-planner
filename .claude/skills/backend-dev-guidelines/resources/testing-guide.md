# Testing Guide – Backend Testing Strategies (Spring Boot)

Complete guide to testing backend services in **Spring Boot** using **JUnit 5, Mockito, Spring Test, and Testcontainers**.

---

## Table of Contents

* Unit Testing
* Integration Testing
* Mocking Strategies
* Test Data Management
* Testing Authenticated Endpoints
* Coverage Targets

---

## Unit Testing

### Goals

* Verify **business logic in isolation**
* No Spring context unless required
* Fast execution

### Test Structure

```java
// UserServiceTest.java
@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private UserService userService;

    @Test
    void shouldThrowExceptionIfEmailExists() {
        when(userRepository.findByEmail("test@test.com"))
            .thenReturn(Optional.of(new User()));

        assertThrows(ConflictException.class, () ->
            userService.createUser(new CreateUserCommand("test@test.com"))
        );
    }

    @Test
    void shouldCreateUserIfEmailIsUnique() {
        when(userRepository.findByEmail("test@test.com"))
            .thenReturn(Optional.empty());

        when(userRepository.save(any(User.class)))
            .thenReturn(new User("123"));

        User user = userService.createUser(
            new CreateUserCommand("test@test.com")
        );

        assertNotNull(user);
        verify(userRepository).save(any(User.class));
    }
}
```

### Best Practices

* Prefer **constructor injection** → easier to test
* Mock only **direct dependencies**
* One behavior per test

---

## Integration Testing

### Spring Context + Real Database

Use **@SpringBootTest** with **Testcontainers** or an embedded database.

```java
@SpringBootTest
@Testcontainers
class UserServiceIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres =
        new PostgreSQLContainer<>("postgres:15");

    @Autowired
    private UserService userService;

    @Test
    void shouldFindUserByEmail() {
        User user = userService.createUser(
            new CreateUserCommand("test@test.com")
        );

        Optional<User> found = userService.findByEmail("test@test.com");

        assertTrue(found.isPresent());
        assertEquals("test@test.com", found.get().getEmail());
    }
}
```

### When to Use Integration Tests

* Repository queries
* Transaction boundaries
* Database constraints
* Service orchestration

---

## Mocking Strategies

### Mock Repositories

```java
@Mock
private UserRepository userRepository;
```

### Mock External Services

```java
@Mock
private EmailService emailService;

verify(emailService).sendEmail(any());
```

### Spy (Partial Mock)

```java
@Spy
private PermissionService permissionService;
```

Use sparingly.

---

## Test Data Management

### Setup & Teardown

```java
@BeforeEach
void setUp() {
    permissionService.clearCache();
}

@AfterEach
void tearDown() {
    repository.deleteAll();
}
```

### Transactions Rollback

```java
@Transactional
@Rollback
@Test
void testSomething() {
    // auto rollback
}
```

---

## Testing Authenticated Endpoints

### Mock Security Context

```java
@Test
@WithMockUser(username = "test-user", roles = {"USER"})
void shouldAccessSecuredEndpoint() throws Exception {
    mockMvc.perform(get("/api/users/me"))
        .andExpect(status().isOk());
}
```

### Custom JWT / Claims

```java
SecurityContextHolder.getContext().setAuthentication(
    new UsernamePasswordAuthenticationToken(
        "user-id", null, List.of(new SimpleGrantedAuthority("ROLE_USER"))
    )
);
```

---

## Coverage Targets

### Recommended

* Unit Tests: **70–80%**
* Integration Tests: Critical paths
* E2E Tests: Happy paths only

### Run Coverage

```bash
./gradlew test jacocoTestReport
```

---

## Test Pyramid

```
        E2E
     Integration
   Unit (Most)
```

Prefer **many unit tests**, fewer integration tests, minimal E2E.

---

## Related Documents

* Services & Repositories – Spring Boot
* Routing & Controllers – Spring Boot
* Database Patterns – Spring Boot
