# Services and Repositories – Business Logic Layer (Spring Boot)

Spring Boot–native guide to organizing business logic with services and data access with repositories.

## Table of Contents

* Service Layer Overview
* Dependency Injection (Spring)
* Singleton Scope in Spring
* Repository Pattern (Spring Data JPA)
* Service Design Principles
* Caching Strategies
* Testing Services

---

## Service Layer Overview

### Purpose of Services

**Services contain business logic** – the *what* and *why* of your application:

```
Controller: "Can I do this?"
Service:    "Yes/No, here are the rules and orchestration"
Repository: "Here is the data"
```

**Services are responsible for:**

* Business rules
* Orchestrating repositories
* Transaction boundaries
* Complex calculations
* External system coordination

**Services should NOT:**

* Know about HTTP or controllers
* Access `EntityManager` directly
* Format API responses

---

## Dependency Injection (Spring)

Spring **already provides DI** – constructor injection is mandatory.

### Recommended Pattern

```java
@Service
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final BatchingService batchingService;
    private final EmailComposer emailComposer;

    private final Map<String, CachedPreference> preferenceCache = new ConcurrentHashMap<>();
    private static final long CACHE_TTL_MS = 5 * 60 * 1000;

    @Transactional
    public Notification createNotification(CreateNotificationCommand command) {
        Notification notification = Notification.create(command);
        notificationRepository.save(notification);

        routeNotification(notification, command);
        return notification;
    }
}
```

### Why Constructor Injection

* Immutable dependencies
* Easy testing
* No hidden coupling
* Enforced at startup

❌ Field injection is forbidden.

---

## Singleton Scope in Spring

In Spring:

> **`@Service` and `@Repository` beans are singletons by default**

### When Singleton Services Are Appropriate

* Permission checks
* Configuration access
* Caching logic
* Coordination services

### Permission Service Example

```java
@Service
@RequiredArgsConstructor
public class PermissionService {

    private final PostRepository postRepository;
    private final Map<String, Boolean> cache = new ConcurrentHashMap<>();

    public boolean canEditPost(String userId, Long postId) {
        return cache.computeIfAbsent(userId + ":" + postId, key -> {
            return postRepository.existsEditableBy(userId, postId);
        });
    }

    public void clearCache() {
        cache.clear();
    }
}
```

---

## Repository Pattern (Spring Data JPA)

### Purpose of Repositories

Repositories handle **only data access**.

```
Service:    What data do I need?
Repository: How to fetch it
```

### Repository Interface

```java
@Repository
public interface UserRepository extends JpaRepository<User, String> {

    Optional<User> findByEmail(String email);

    List<User> findByIsActiveTrueOrderByNameAsc();

    boolean existsByEmail(String email);
}
```

### Custom Queries

```java
@Query("""
    select u from User u
    join fetch u.roles
    where u.id = :id
""")
Optional<User> findWithRoles(@Param("id") String id);
```

### Rules

Repositories:

* Contain **no business logic**
* Throw persistence exceptions
* Return domain entities

---

## Using Repository in Service

```java
@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;

    @Transactional
    public User createUser(CreateUserCommand command) {
        if (userRepository.existsByEmail(command.email())) {
            throw new ConflictException("Email already exists");
        }

        User user = User.create(command);
        return userRepository.save(user);
    }

    @Transactional(readOnly = true)
    public User getUser(String userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("User not found"));
    }
}
```

---

## Service Design Principles

### 1. Single Responsibility

```java
// GOOD
UserService
EmailService
AuditService

// BAD
MegaService // does everything
```

### 2. Clear Method Names

```java
createUser()
findActiveUsers()
canEditPost()
```

❌ Avoid `process()`, `execute()`, `handle()`

### 3. Explicit Transactions

```java
@Transactional
public void updateUser() {}

@Transactional(readOnly = true)
public User getUser() {}
```

### 4. Meaningful Errors

```java
throw new NotFoundException("User not found");
throw new ForbiddenException("No permission");
```

---

## Caching Strategies

### In-Memory Cache (Simple)

```java
private final Map<String, CachedUser> cache = new ConcurrentHashMap<>();

public User getUser(String id) {
    CachedUser cached = cache.get(id);
    if (cached != null && cached.isValid()) {
        return cached.user();
    }

    User user = userRepository.findById(id)
        .orElseThrow();

    cache.put(id, new CachedUser(user));
    return user;
}
```

### Cache Invalidation

```java
@Transactional
public User updateUser(String id, UpdateUserCommand cmd) {
    User user = userRepository.findById(id).orElseThrow();
    user.update(cmd);
    cache.remove(id);
    return user;
}
```

---

## Testing Services

### Unit Test Example

```java
@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    UserRepository userRepository;

    @InjectMocks
    UserService userService;

    @Test
    void createUser_whenEmailExists_throwsException() {
        when(userRepository.existsByEmail("test@test.com")).thenReturn(true);

        assertThrows(ConflictException.class, () ->
            userService.createUser(new CreateUserCommand("test@test.com"))
        );
    }
}
```

### Testing Rules

* Mock repositories
* Test business rules
* No Spring context for unit tests

---

## Summary Rules

| Layer      | Responsibility    |
| ---------- | ----------------- |
| Controller | HTTP + delegation |
| Service    | Business logic    |
| Repository | Data access       |

**If business logic is not in a service — it’s in the wrong place.**

---

**Related Files:**

* SKILL.md
* routing-and-controllers.md
* database-patterns.md
* complete-examples.md
