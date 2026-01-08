# Backend Anti-Patterns: Why They Matter

This document explains each anti-pattern enforced by our `forbidden-patterns-check` hook, why it's problematic, and how to fix it.

---

## 1. Field Injection (`@Autowired private`)

### The Anti-Pattern
```java
@Service
public class UserService {
    @Autowired
    private UserRepository userRepository;  // ❌ Field injection
}
```

### Why It's Harmful

**Testing Nightmare**
- Cannot inject mocks without reflection or Spring context
- Forces integration tests when unit tests would suffice
- Tests become slow (Spring context startup ~2-5s per test class)

**Hidden Dependencies**
- Class dependencies aren't visible in constructor
- Easy to accumulate 10+ dependencies without noticing
- Violates Dependency Inversion Principle (class controls its dependencies)

**Circular Dependency Blindness**
- Spring silently resolves circular dependencies with field injection
- Masks architectural problems that would fail fast with constructor injection
- Leads to tightly coupled spaghetti code

### The Fix
```java
@Service
@RequiredArgsConstructor  // Lombok generates constructor
public class UserService {
    private final UserRepository userRepository;  // ✅ Constructor injection
}
```

### Real-World Impact
A project with 50 services using field injection typically has:
- 3-5 hidden circular dependencies
- Test suites 10x slower than necessary
- Developers afraid to refactor due to unclear dependencies

---

## 2. Exposing Entities in API (`ResponseEntity<UserEntity>`)

### The Anti-Pattern
```java
@GetMapping("/users/{id}")
public ResponseEntity<User> getUser(@PathVariable Long id) {
    return ResponseEntity.ok(userRepository.findById(id).orElseThrow());  // ❌
}
```

### Why It's Harmful

**Security Exposure**
- Entity may contain: password hashes, internal IDs, soft-delete flags, audit fields
- JPA lazy-loading can trigger N+1 queries during JSON serialization
- Hibernate proxies can cause serialization errors

**API Coupling**
- Database schema changes break API contract
- Cannot version API independently from database
- Adding a column = breaking change for all clients

**Serialization Chaos**
- `@JsonIgnore` scattered across entities
- Bidirectional relationships cause infinite recursion
- Different endpoints need different views of same entity

### The Fix
```java
// DTO - explicit API contract
public record UserResponse(Long id, String name, String email) {
    public static UserResponse from(User entity) {
        return new UserResponse(entity.getId(), entity.getName(), entity.getEmail());
    }
}

@GetMapping("/users/{id}")
public ResponseEntity<UserResponse> getUser(@PathVariable Long id) {
    return ResponseEntity.ok(userService.findById(id));  // ✅ Returns DTO
}
```

### Real-World Impact
A single exposed entity with a `password` field = potential data breach affecting all users.

---

## 3. Using `.get()` on Optional

### The Anti-Pattern
```java
User user = userRepository.findById(id).get();  // ❌ Throws if empty
```

### Why It's Harmful

**NullPointerException in Disguise**
- `NoSuchElementException` is just as bad as NPE
- Crashes production with unhelpful stack trace
- No graceful error handling

**Defeats Optional's Purpose**
- Optional exists to force explicit null handling
- `.get()` bypasses that safety
- Same as `object.method()` without null check

### The Fix
```java
// Option 1: Throw meaningful exception
User user = userRepository.findById(id)
    .orElseThrow(() -> new NotFoundException("User", id));  // ✅

// Option 2: Provide default
User user = userRepository.findById(id)
    .orElse(defaultUser);  // ✅

// Option 3: Handle absence
userRepository.findById(id)
    .ifPresentOrElse(
        user -> processUser(user),
        () -> log.warn("User {} not found", id)
    );  // ✅
```

### Real-World Impact
`.get()` on empty Optional in production = 500 error, unhappy users, 3am pages.

---

## 4. `@Transactional` on Private Methods

### The Anti-Pattern
```java
@Service
public class OrderService {
    @Transactional
    private void processPayment(Order order) {  // ❌ Won't work!
        // Transaction annotation is IGNORED
    }
}
```

### Why It's Harmful

**Silent Failure**
- Spring AOP uses proxies that only intercept public methods
- Annotation is completely ignored on private methods
- No warning, no error - just broken transaction management

**Data Corruption**
- Partial writes without rollback
- Inconsistent database state
- Extremely hard to debug (works in tests, fails in production)

### The Fix
```java
@Service
@Transactional  // ✅ Class-level default
public class OrderService {

    public void processPayment(Order order) {  // ✅ Public method
        // Transaction works correctly
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void auditLog(Order order) {  // ✅ Separate transaction
        // Commits even if parent rolls back
    }
}
```

### Real-World Impact
Payment processed but order not saved = customer charged, no record, support nightmare.

---

## 5. String Concatenation in `@Query` (SQL Injection)

### The Anti-Pattern
```java
@Query("SELECT u FROM User u WHERE u.name = '" + name + "'")  // ❌ SQL INJECTION!
List<User> findByName(String name);
```

### Why It's Harmful

**Security Vulnerability (OWASP #1)**
- Input: `'; DROP TABLE users; --`
- Result: Database destroyed
- Affects: Every user in system

**Data Breach**
- Input: `' OR '1'='1`
- Result: Returns all users
- Exposes: Passwords, PII, everything

### The Fix
```java
@Query("SELECT u FROM User u WHERE u.name = :name")
List<User> findByName(@Param("name") String name);  // ✅ Parameterized
```

### Real-World Impact
SQL injection has caused breaches at Sony, LinkedIn, Yahoo. Single vulnerability = millions of records leaked.

---

## 6. Empty Catch Blocks (Exception Swallowing)

### The Anti-Pattern
```java
try {
    processPayment(order);
} catch (Exception e) {
    // ❌ Silent failure - no one knows something went wrong
}
```

### Why It's Harmful

**Invisible Failures**
- Errors happen but no one knows
- System appears healthy while data corrupts
- Users report issues weeks later

**Debugging Impossible**
- No stack trace
- No error logs
- "It works on my machine"

**Cascading Failures**
- Downstream systems receive garbage data
- Partial state persisted
- Recovery requires manual intervention

### The Fix
```java
try {
    processPayment(order);
} catch (PaymentException e) {
    log.error("Payment failed for order {}: {}", order.getId(), e.getMessage(), e);
    throw new OrderProcessingException("Payment failed", e);  // ✅ Rethrow or handle
}
```

### Real-World Impact
Silent failures in payment processing = revenue loss, accounting discrepancies, audit failures.

---

## 7. Missing `@Valid` on `@RequestBody`

### The Anti-Pattern
```java
@PostMapping("/users")
public ResponseEntity<UserResponse> create(@RequestBody CreateUserRequest request) {  // ❌
    // No validation - anything goes
}
```

### Why It's Harmful

**No Input Validation**
- Null values where not expected
- Empty strings in required fields
- Negative numbers for quantities

**Security Risk**
- Oversized payloads (DoS)
- Malformed data causing crashes
- Injection attempts not blocked

**Data Quality**
- Garbage in, garbage out
- Invalid emails, phones, addresses
- Business logic errors downstream

### The Fix
```java
// DTO with validation
public record CreateUserRequest(
    @NotBlank @Size(max = 100) String name,
    @Email String email,
    @Min(0) @Max(150) Integer age
) {}

@PostMapping("/users")
public ResponseEntity<UserResponse> create(
    @Valid @RequestBody CreateUserRequest request  // ✅ Validates before method runs
) {
    // Only valid data reaches here
}
```

### Real-World Impact
Invalid email format saved = password reset emails never arrive, user locked out of account.

---

## 8. `@Transactional` in Controller

### The Anti-Pattern
```java
@RestController
public class UserController {
    @Transactional  // ❌ Wrong layer!
    @PostMapping("/users")
    public ResponseEntity<UserResponse> create(@RequestBody CreateUserRequest request) {
        // Transaction spans entire HTTP request handling
    }
}
```

### Why It's Harmful

**Violates Layered Architecture**
- Controller = HTTP handling only
- Service = Business logic + transactions
- Mixing responsibilities = untestable code

**Long-Running Transactions**
- Transaction open while: validating, logging, serializing response
- Database connections held unnecessarily
- Connection pool exhaustion under load

**Error Handling Complexity**
- HTTP errors mixed with transaction rollback
- 400 Bad Request shouldn't affect database
- Response serialization errors cause rollback

### The Fix
```java
@RestController
@RequiredArgsConstructor
public class UserController {
    private final UserService userService;

    @PostMapping("/users")
    public ResponseEntity<UserResponse> create(@Valid @RequestBody CreateUserRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(userService.create(request));  // ✅ Transaction in service
    }
}

@Service
@Transactional
public class UserService {
    public UserResponse create(CreateUserRequest request) {
        // Transaction scoped to business logic only
    }
}
```

---

## 9. `@Query` in Service (SQL in Wrong Layer)

### The Anti-Pattern
```java
@Service
public class UserService {
    @PersistenceContext
    private EntityManager em;

    public List<User> findActiveUsers() {
        return em.createQuery("SELECT u FROM User u WHERE u.active = true")  // ❌
            .getResultList();
    }
}
```

### Why It's Harmful

**Violates Repository Pattern**
- Data access logic scattered across services
- Duplicate queries in multiple services
- No single source of truth for data access

**Testing Complexity**
- Cannot mock data layer cleanly
- Service tests require database
- Slow, brittle tests

**Maintenance Burden**
- Schema change requires searching all services
- Query optimization scattered
- No query reuse

### The Fix
```java
// Repository - single home for data access
public interface UserRepository extends JpaRepository<User, Long> {
    @Query("SELECT u FROM User u WHERE u.active = true")
    List<User> findActiveUsers();  // ✅
}

// Service - delegates to repository
@Service
@RequiredArgsConstructor
public class UserService {
    private final UserRepository userRepository;

    public List<User> findActiveUsers() {
        return userRepository.findActiveUsers();  // ✅
    }
}
```

---

## Summary Table

| Anti-Pattern | Risk Level | Primary Harm |
|--------------|------------|--------------|
| Field Injection | Medium | Testability, maintainability |
| Entity Exposure | High | Security, API stability |
| `.get()` on Optional | Medium | Runtime crashes |
| @Transactional on private | High | Data corruption |
| SQL String Concatenation | **Critical** | SQL injection, data breach |
| Empty Catch Block | High | Silent failures |
| Missing @Valid | Medium | Data quality, security |
| @Transactional in Controller | Medium | Performance, architecture |
| @Query in Service | Low | Maintainability |

---

## Hook Enforcement

These patterns are automatically detected by `.claude/hooks/forbidden-patterns-check.sh`:

```
┌─ PATTERN CHECK ─────────────────────────────┐
│ File: service/UserService.java
├────────────────────────────────────────────────┤
│ ❌ Use constructor injection instead of field injection
│ ❌ Use .orElseThrow() instead of .get() on Optional
└────────────────────────────────────────────────┘
```

Configuration: `.claude/hooks/forbidden-patterns.json`
