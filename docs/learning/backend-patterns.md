# Backend Patterns - Educational Reference

This document explains **why** LimbusPlanner uses specific backend patterns. For actionable rules, see the `be-*` skills.

---

## Why Constructor Injection?

Field injection (`@Autowired` on fields) has issues:
- Hidden dependencies (not visible in constructor)
- Hard to test (no way to inject mocks without reflection)
- Allows circular dependencies to hide

Constructor injection with `@RequiredArgsConstructor`:
```java
@RequiredArgsConstructor
public class UserService {
    private final UserRepository userRepository;  // Auto-injected
}
```

---

## Why DTOs vs Entities?

**Never expose JPA entities directly in API responses:**

| Issue | Explanation |
|-------|-------------|
| Coupling | API contract tied to database schema |
| Security | Internal fields (passwords, IDs) exposed |
| Performance | Lazy loading triggers N+1 queries |
| Evolution | Database changes break API |

Use DTOs to decouple:
```java
public record UserResponse(String id, String name) {
    public static UserResponse from(User entity) {
        return new UserResponse(entity.getId().toString(), entity.getName());
    }
}
```

---

## Why /api/ Prefix?

All REST endpoints use `/api/` prefix to:
- Separate from static resources
- Enable consistent proxy/gateway rules
- Allow path-based routing in load balancers

---

## Service Layer Responsibilities

| Layer | Does | Doesn't |
|-------|------|---------|
| Controller | HTTP handling, validation, DTO conversion | Business logic |
| Service | Business logic, transactions, orchestration | HTTP concerns |
| Repository | Data access | Business rules |

---

## Transaction Boundaries

`@Transactional` on service methods ensures:
- All database operations in single transaction
- Automatic rollback on RuntimeException
- Connection release after method completes

**Read-only optimization:**
```java
@Transactional(readOnly = true)
public List<User> findAll() { ... }
```

---

## Jakarta Validation

Validation annotations on DTOs trigger at controller entry when using `@Valid`:

```java
public record CreateUserRequest(
    @NotBlank String name,
    @Email String email,
    @Size(min = 8) String password
) {}

@PostMapping
public ResponseEntity<UserResponse> create(@Valid @RequestBody CreateUserRequest req) {
    // req already validated
}
```

---

## Exception Handling Strategy

| Exception Type | HTTP Status | Use Case |
|---------------|-------------|----------|
| `EntityNotFoundException` | 404 | Resource not found |
| `IllegalArgumentException` | 400 | Invalid input |
| `AccessDeniedException` | 403 | Authorization failed |
| Generic `Exception` | 500 | Unexpected error |

All handled by `GlobalExceptionHandler` for consistent error responses.

---

## SSE vs WebSocket

| Protocol | Use Case |
|----------|----------|
| SSE | Server-to-client only (progress updates, notifications) |
| WebSocket | Bidirectional real-time communication |

SSE is simpler when client-to-server messages aren't needed.

---

## Test Pyramid

| Level | Speed | Scope | Count |
|-------|-------|-------|-------|
| Unit | Fast | Single class | Many |
| Integration | Medium | Multiple components | Some |
| E2E | Slow | Full stack | Few |

Focus on unit tests with mocked dependencies.

---

## Soft Delete Pattern

Use `deleted_at` timestamp instead of hard DELETE:
- Preserves audit trail
- Enables data recovery
- Supports reactivation workflows

Key considerations:
- Add `DeletedAtIsNull` to repository queries
- Use Persistable interface for composite keys
- Add @Version for optimistic locking

**See:** `soft-delete-composite-keys.md` for complete guide.

---

## Further Reading

- [Spring Boot Reference](https://docs.spring.io/spring-boot/docs/current/reference/html/)
- [Jakarta Validation](https://jakarta.ee/specifications/bean-validation/)
- [Testing Spring Boot](https://docs.spring.io/spring-boot/docs/current/reference/html/features.html#features.testing)
- [Soft Delete with Composite Keys](soft-delete-composite-keys.md)
