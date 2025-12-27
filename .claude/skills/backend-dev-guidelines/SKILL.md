# Backend Development Guidelines

**Spring Boot 3.x · Java 17+ · RESTful APIs · WebSocket · Security**

## Purpose

Production-quality backend development guidelines for **Spring Boot 3.x**. Covers architecture, layered design, REST endpoints, authentication, persistence, validation, exception handling, configuration, WebSocket real-time messaging, observability, and testing.

---

## When to Use This Skill

- Creating REST APIs (controllers, endpoints)
- Implementing business logic (services)
- Writing persistence logic (repositories, JPA)
- Setting up validation (Jakarta Validation)
- Configuring exception handling
- Managing application configuration
- Implementing WebSocket notifications
- Writing unit / integration tests
- Setting up observability (logging, metrics, monitoring)

---

## Quick Start

### MANDATORY: Pattern Check Before Writing

**Before creating ANY new file, you MUST:**

1. **Search for similar existing files** using Glob/Grep
2. **Read 1-2 similar files** to understand established patterns
3. **State your pattern reference** before writing:
   ```
   **Pattern Reference:** [filename]
   - Using: [specific patterns from that file]
   ```
4. **If no similar file exists**, state: `**New Pattern:** [reason]`

**Pattern Check by File Type:**
| File Type | Search | Example Reference |
|-----------|--------|-------------------|
| Controller | `*Controller.java` | `UserController.java` |
| Service | `*Service.java` | `UserService.java` |
| Repository | `*Repository.java` | `UserRepository.java` |
| DTO | `*Request.java`, `*Response.java` | `CreateUserRequest.java` |
| Entity | `domain/*.java` | `User.java` |
| Exception | `exception/*.java` | `NotFoundException.java` |
| WebSocket | `websocket/*.java` | `NotificationController.java` |
| Security | `security/*.java` | `JwtTokenProvider.java` |

---

### New Feature Checklist

- [ ] **Controller**: REST endpoints only, no business logic
- [ ] **DTOs**: Separate Request/Response objects with validation
- [ ] **Validation**: Jakarta Validation annotations on DTOs
- [ ] **Service**: Business logic with `@Transactional` where needed
- [ ] **Repository**: Spring Data JPA interface
- [ ] **Exception Handling**: Custom exceptions + global handler
- [ ] **Logging**: SLF4J with `@Slf4j`
- [ ] **Tests**: Unit tests for service, integration tests for repository

---

## Architecture Overview

### Layered Architecture

```
HTTP Request / WebSocket
    ↓
Security Filter (JWT)
    ↓
Controller (@RestController / @MessageMapping)
    ↓
Service (@Service)
    ↓
Repository (@Repository)
    ↓
Database (JPA / SQL)
```

**Key Principle:** Each layer has **one responsibility** and must not leak concerns.

---

## Package Structure

```
com.example.service/
├── config/              # @Configuration classes
├── controller/          # @RestController classes
├── service/             # @Service classes
├── repository/          # @Repository interfaces
├── domain/              # @Entity classes
├── dto/                 # Request/Response DTOs
├── exception/           # Custom exceptions
├── security/            # Security components
├── validation/          # Custom validators
├── websocket/           # WebSocket controllers & config
└── Application.java
```

### Naming Conventions

| Type | Convention | Example |
|------|------------|---------|
| Controller | `*Controller` | `UserController` |
| Service | `*Service` | `UserService` |
| Repository | `*Repository` | `UserRepository` |
| Entity | Singular noun | `User`, `Order` |
| Request DTO | `*Request` | `CreateUserRequest` |
| Response DTO | `*Response` | `UserResponse` |
| Exception | `*Exception` | `NotFoundException` |
| WebSocket Message | `*Message` | `NotificationMessage` |

---

## Core Patterns

### 1. Controller Pattern

Controllers handle HTTP only - no business logic:

```java
@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
@Slf4j
public class UserController {

    private final UserService userService;

    @GetMapping("/{id}")
    public ResponseEntity<UserResponse> getUser(@PathVariable Long id) {
        return ResponseEntity.ok(userService.findById(id));
    }

    @GetMapping
    public ResponseEntity<List<UserResponse>> listUsers() {
        return ResponseEntity.ok(userService.findAll());
    }

    @PostMapping
    public ResponseEntity<UserResponse> createUser(
            @Valid @RequestBody CreateUserRequest request) {
        UserResponse response = userService.create(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PutMapping("/{id}")
    public ResponseEntity<UserResponse> updateUser(
            @PathVariable Long id,
            @Valid @RequestBody UpdateUserRequest request) {
        return ResponseEntity.ok(userService.update(id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteUser(@PathVariable Long id) {
        userService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
```

### 2. Service Pattern

Services own business logic:

```java
@Service
@RequiredArgsConstructor
@Slf4j
public class UserService {

    private final UserRepository userRepository;
    private final UserMapper userMapper;

    @Transactional(readOnly = true)
    public UserResponse findById(Long id) {
        User user = userRepository.findById(id)
            .orElseThrow(() -> new NotFoundException("User", id));
        return userMapper.toResponse(user);
    }

    @Transactional
    public UserResponse create(CreateUserRequest request) {
        if (userRepository.existsByEmail(request.email())) {
            throw new ConflictException("Email already exists");
        }

        User user = userMapper.toEntity(request);
        User saved = userRepository.save(user);
        log.info("Created user with id: {}", saved.getId());
        return userMapper.toResponse(saved);
    }
}
```

### 3. Repository Pattern

Use Spring Data JPA:

```java
@Repository
public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);

    @Query("SELECT u FROM User u WHERE u.status = :status")
    List<User> findByStatus(@Param("status") UserStatus status);
}
```

### 4. DTO Pattern

Separate Request/Response with validation:

```java
// Request DTO
public record CreateUserRequest(
    @NotBlank(message = "Name is required")
    @Size(min = 2, max = 100)
    String name,

    @NotBlank(message = "Email is required")
    @Email(message = "Invalid email format")
    String email,

    @NotNull
    @Min(18)
    Integer age
) {}

// Response DTO
public record UserResponse(
    Long id,
    String name,
    String email,
    LocalDateTime createdAt
) {}
```

### 5. Exception Handling

Global exception handler with `@RestControllerAdvice`:

```java
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler(NotFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(NotFoundException ex) {
        log.warn("Resource not found: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
            .body(new ErrorResponse("NOT_FOUND", ex.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult().getFieldErrors().stream()
            .map(e -> e.getField() + ": " + e.getDefaultMessage())
            .collect(Collectors.joining(", "));
        return ResponseEntity.badRequest()
            .body(new ErrorResponse("VALIDATION_ERROR", message));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpected(Exception ex) {
        log.error("Unexpected error", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(new ErrorResponse("INTERNAL_ERROR", "An unexpected error occurred"));
    }
}

public record ErrorResponse(String code, String message) {}
```

### 6. Configuration Pattern

Use `@ConfigurationProperties` for type-safe config:

```java
@Configuration
@ConfigurationProperties(prefix = "app")
@Validated
public class AppProperties {

    @NotBlank
    private String name;

    @NotNull
    private Integer maxRetries;

    // getters and setters
}
```

```yaml
# application.yml
app:
  name: my-service
  max-retries: 3

# application-dev.yml
spring:
  datasource:
    url: jdbc:h2:mem:testdb

# application-prod.yml
spring:
  datasource:
    url: ${DATABASE_URL}
```

---

## WebSocket / STOMP Pattern

Real-time notifications using WebSocket with STOMP protocol.

### WebSocket Configuration

```java
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        config.enableSimpleBroker("/topic", "/queue");
        config.setApplicationDestinationPrefixes("/app");
        config.setUserDestinationPrefix("/user");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws")
            .setAllowedOrigins("*")
            .withSockJS();
    }

    @Override
    public void configureWebSocketTransport(WebSocketTransportRegistration registration) {
        registration.setMessageSizeLimit(64 * 1024);
        registration.setSendBufferSizeLimit(512 * 1024);
        registration.setSendTimeLimit(20 * 1000);
    }
}
```

### Notification Message DTO

```java
public record NotificationMessage(
    String type,
    String title,
    String body,
    Long targetId,
    LocalDateTime timestamp
) {}
```

### WebSocket Controller

```java
@Controller
@RequiredArgsConstructor
@Slf4j
public class NotificationController {

    private final SimpMessagingTemplate messagingTemplate;

    @MessageMapping("/notification")
    public void handleNotification(NotificationMessage message) {
        log.info("Received notification: {}", message);
        messagingTemplate.convertAndSend("/topic/notifications", message);
    }

    public void sendToUser(String username, NotificationMessage message) {
        messagingTemplate.convertAndSendToUser(
            username,
            "/queue/notifications",
            message
        );
    }
}
```

### Notification Service

```java
@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationService {

    private final SimpMessagingTemplate messagingTemplate;
    private final NotificationRepository notificationRepository;

    public void sendCommentNotification(Long userId, Comment comment) {
        NotificationMessage message = new NotificationMessage(
            "COMMENT",
            "New comment on your post",
            comment.getContent(),
            comment.getPostId(),
            LocalDateTime.now()
        );

        notificationRepository.save(toEntity(userId, message));

        messagingTemplate.convertAndSendToUser(
            userId.toString(),
            "/queue/notifications",
            message
        );

        log.info("Sent comment notification to user: {}", userId);
    }

    public void broadcastSystemMessage(String message) {
        NotificationMessage notification = new NotificationMessage(
            "SYSTEM",
            "System Announcement",
            message,
            null,
            LocalDateTime.now()
        );
        messagingTemplate.convertAndSend("/topic/system", notification);
    }
}
```

### WebSocket Event Handling

```java
@Component
@Slf4j
public class WebSocketEventListener {

    @EventListener
    public void handleConnect(SessionConnectedEvent event) {
        log.info("WebSocket connected: {}", event.getMessage());
    }

    @EventListener
    public void handleDisconnect(SessionDisconnectEvent event) {
        log.info("WebSocket disconnected: {}", event.getSessionId());
    }
}
```

### WebSocket Destinations

| Destination | Type | Usage |
|-------------|------|-------|
| `/topic/*` | Broadcast | All subscribers receive |
| `/queue/*` | Point-to-point | Single recipient |
| `/user/{userId}/queue/*` | User-specific | Target user only |
| `/app/*` | Application | Client → Server messages |

---

## Dependency Injection

**Always use constructor injection with `@RequiredArgsConstructor`:**

```java
// Good - Constructor injection
@Service
@RequiredArgsConstructor
public class UserService {
    private final UserRepository userRepository;
    private final EmailService emailService;
}

// Bad - Field injection
@Service
public class UserService {
    @Autowired  // Don't do this
    private UserRepository userRepository;
}
```

---

## Logging

Use SLF4J with Lombok's `@Slf4j`:

```java
@Service
@Slf4j
public class UserService {

    public void processUser(Long id) {
        log.debug("Processing user with id: {}", id);
        // ...
        log.info("User {} processed successfully", id);
    }

    public void handleError(Exception e) {
        log.error("Failed to process: {}", e.getMessage(), e);
    }
}
```

**Logging Guidelines:**
- DEBUG: Detailed diagnostic information
- INFO: Important business events
- WARN: Potentially harmful situations
- ERROR: Errors that need attention

---

## Observability

### Actuator Configuration

```yaml
management:
  endpoints:
    web:
      exposure:
        include: health,info,metrics,prometheus
  endpoint:
    health:
      show-details: when_authorized
  metrics:
    tags:
      application: ${spring.application.name}
```

---

## HTTP Status Codes

| Code | Usage |
|------|-------|
| 200 | Successful GET/PUT |
| 201 | Resource created (POST) |
| 204 | No content (DELETE) |
| 400 | Validation error |
| 401 | Authentication required |
| 403 | Authorization failure |
| 404 | Resource not found |
| 409 | Conflict (duplicate, concurrent modification) |
| 500 | Unexpected server error |

---

## Testing

### Unit Tests (Services)

```java
@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private UserService userService;

    @Test
    void findById_WhenUserExists_ReturnsUser() {
        User user = new User(1L, "John");
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        UserResponse result = userService.findById(1L);

        assertThat(result.name()).isEqualTo("John");
    }

    @Test
    void findById_WhenUserNotExists_ThrowsException() {
        when(userRepository.findById(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.findById(1L))
            .isInstanceOf(NotFoundException.class);
    }
}
```

### Integration Tests (Repository)

```java
@DataJpaTest
class UserRepositoryTest {

    @Autowired
    private UserRepository userRepository;

    @Test
    void findByEmail_ReturnsUser() {
        User user = userRepository.save(new User("test@example.com"));

        Optional<User> found = userRepository.findByEmail("test@example.com");

        assertThat(found).isPresent();
    }
}
```

### API Tests (Controller)

```java
@WebMvcTest(UserController.class)
class UserControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private UserService userService;

    @Test
    void getUser_ReturnsUser() throws Exception {
        when(userService.findById(1L))
            .thenReturn(new UserResponse(1L, "John", "john@example.com", null));

        mockMvc.perform(get("/api/users/1"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.name").value("John"));
    }
}
```

---

## Anti-Patterns to Avoid

| Don't | Do Instead |
|-------|------------|
| Business logic in controllers | Move to services |
| Expose entities directly | Use DTOs |
| Field injection (`@Autowired`) | Constructor injection |
| Swallow exceptions silently | Log and rethrow/handle |
| `System.out.println()` | Use SLF4J logging |
| Read config via `System.getenv()` | Use `@ConfigurationProperties` |
| Missing `@Valid` on request body | Always validate input |

---

## Resources

Detailed guides for specific topics. **Read the required resources before starting work.**

### When to Read Resources

| Task | Required Reading |
|------|------------------|
| Creating controller | `routing-and-controllers.md` |
| Creating service | `services-and-repositories.md` |
| Setting up validation | `validation-patterns.md` |
| Database work | `database-patterns.md` |
| Exception handling | `async-and-errors.md` |
| Configuration setup | `configuration.md` |
| WebSocket setup | `websocket-guide.md` |
| Authentication/Security | `security-guide.md` |
| Writing tests | `testing-guide.md` |
| Full examples | `complete-examples.md` |

### Resource List

- [routing-and-controllers.md](resources/routing-and-controllers.md) - REST endpoints, response handling
- [services-and-repositories.md](resources/services-and-repositories.md) - Business logic, transactions
- [validation-patterns.md](resources/validation-patterns.md) - Jakarta Validation, custom validators
- [database-patterns.md](resources/database-patterns.md) - JPA entities, repositories, queries
- [configuration.md](resources/configuration.md) - Properties, profiles, externalized config
- [async-and-errors.md](resources/async-and-errors.md) - Exception handling, async patterns
- [websocket-guide.md](resources/websocket-guide.md) - STOMP, real-time notifications
- [security-guide.md](resources/security-guide.md) - JWT auth, authorization, security patterns
- [testing-guide.md](resources/testing-guide.md) - Unit, integration, API tests
- [complete-examples.md](resources/complete-examples.md) - Full working examples
- [sentry-and-monitoring.md](resources/sentry-and-monitoring.md) - Observability setup

---

## Related Skills

- **error-tracking** - Sentry integration patterns

---

**Sources:**
- [Spring Boot Best Practices - GitHub](https://github.com/arsy786/springboot-best-practices)
- [Spring Boot WebSocket STOMP - Toptal](https://www.toptal.com/java/stomp-spring-boot-websocket)
