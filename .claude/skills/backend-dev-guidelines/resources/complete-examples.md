# Complete Examples – Full Working Code (Spring Boot)

Real‑world, **end‑to‑end Spring Boot (Java 17)** examples showing clean architecture, DI, validation, error handling, and repository patterns.

---

## Table of Contents

* [Complete Controller Example](#complete-controller-example)
* [Complete Service with DI](#complete-service-with-di)
* [Complete Route Mapping](#complete-route-mapping)
* [Complete Repository](#complete-repository)
* [Refactoring Example: Bad to Good](#refactoring-example-bad-to-good)
* [End-to-End Feature Example](#end-to-end-feature-example)

---

## Complete Controller Example

### UserController (Spring MVC Best Practices)

```java
@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @GetMapping("/{id}")
    public ResponseEntity<UserResponse> getUser(@PathVariable Long id) {
        User user = userService.findById(id);
        return ResponseEntity.ok(UserResponse.from(user));
    }

    @GetMapping
    public ResponseEntity<List<UserResponse>> listUsers() {
        return ResponseEntity.ok(
            userService.findAll().stream()
                .map(UserResponse::from)
                .toList()
        );
    }

    @PostMapping
    public ResponseEntity<UserResponse> createUser(
            @Valid @RequestBody CreateUserRequest request
    ) {
        User user = userService.create(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(UserResponse.from(user));
    }

    @PutMapping("/{id}")
    public ResponseEntity<UserResponse> updateUser(
            @PathVariable Long id,
            @Valid @RequestBody UpdateUserRequest request
    ) {
        User user = userService.update(id, request);
        return ResponseEntity.ok(UserResponse.from(user));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteUser(@PathVariable Long id) {
        userService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
```

---

## Complete Service with DI

### UserService

```java
@Service
@RequiredArgsConstructor
@Transactional
public class UserService {

    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public User findById(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("User not found"));
    }

    @Transactional(readOnly = true)
    public List<User> findAll() {
        return userRepository.findByIsActiveTrueOrderByCreatedAtDesc();
    }

    public User create(CreateUserRequest request) {
        if (request.age() < 18) {
            throw new ValidationException("User must be 18 or older");
        }

        if (userRepository.existsByEmail(request.email())) {
            throw new ConflictException("Email already in use");
        }

        User user = User.create(request);
        return userRepository.save(user);
    }

    public User update(Long id, UpdateUserRequest request) {
        User user = findById(id);

        if (request.email() != null &&
            !request.email().equals(user.getEmail()) &&
            userRepository.existsByEmail(request.email())) {
            throw new ConflictException("Email already in use");
        }

        user.update(request);
        return user;
    }

    public void delete(Long id) {
        User user = findById(id);
        user.softDelete();
    }
}
```

---

## Complete Route Mapping

Spring Boot routes are defined via annotations, not separate route files.

```java
@RequestMapping("/api/users")
@RestController
public class UserController { /* ... */ }
```

### Middleware Mapping

| Express          | Spring Boot                           |
| ---------------- | ------------------------------------- |
| Auth middleware  | `OncePerRequestFilter`                |
| Audit middleware | `HandlerInterceptor`                  |
| res.locals       | `SecurityContext`, Request Attributes |

---

## Complete Repository

### UserRepository (JPA)

```java
public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);

    List<User> findByIsActiveTrueOrderByCreatedAtDesc();
}
```

---

## Refactoring Example: Bad to Good

### ❌ BEFORE: Business Logic in Controller

```java
@PostMapping("/posts")
public ResponseEntity<?> createPost(@RequestBody PostRequest request) {
    if (!permissionService.canComplete(request)) {
        return ResponseEntity.status(403).build();
    }

    Post post = postRepository.save(...);
    auditService.audit(...);
    impersonationService.handle(...);

    return ResponseEntity.ok(post);
}
```

### ✅ AFTER: Clean Separation

#### Controller

```java
@PostMapping
public ResponseEntity<PostResponse> createPost(
        @Valid @RequestBody CreatePostRequest request
) {
    return ResponseEntity.ok(
        PostResponse.from(postService.create(request))
    );
}
```

#### Service

```java
@Service
public class PostService {

    public Post create(CreatePostRequest request) {
        permissionService.checkCanCreate(request);
        WorkflowResult result = workflowEngine.execute(request);
        impersonationService.handle(result);
        return result.post();
    }
}
```

**Result:**

* Controller: HTTP only
* Service: business rules
* Repository: persistence only

---

## End-to-End Feature Example

### 1. DTOs

```java
public record CreateUserRequest(
        @Email String email,
        @NotBlank String firstName,
        @NotBlank String lastName,
        @Min(18) int age
) {}

public record UpdateUserRequest(
        @Email String email,
        String firstName,
        String lastName
) {}
```

---

### 2. Entity

```java
@Entity
@Getter
public class User {

    @Id @GeneratedValue
    private Long id;

    private String email;
    private boolean isActive = true;

    @Embedded
    private UserProfile profile;

    public static User create(CreateUserRequest r) {
        User u = new User();
        u.email = r.email();
        u.profile = new UserProfile(r.firstName(), r.lastName(), r.age());
        return u;
    }

    public void softDelete() {
        this.isActive = false;
    }
}
```

---

### 3. Repository → Service → Controller

Flow is identical for all features:

```
HTTP Request
  ↓
Controller (@RestController)
  ↓
Service (@Transactional)
  ↓
Repository (JPA)
  ↓
Database
```

---

## Complete Request Flow

```
POST /api/users
  ↓
Spring Security Filter (Auth)
  ↓
Controller
  ↓
Jakarta Validation
  ↓
Service (business rules)
  ↓
Repository
  ↓
JPA / DB
  ↓
Controller
  ↓
201 CREATED
```

---

### Related Docs

* `architecture-overview-springboot.md`
* `routing-and-controllers.md`
* `services-and-repositories.md`
* `validation-patterns.md`
