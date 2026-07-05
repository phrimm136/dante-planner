---
paths:
  - "backend/**/service/**/*.java"
  - "backend/**/*Service.java"
---

# Service Core Patterns

## Mandatory Rules

- **`@Transactional` on class** — default for service classes
- **`readOnly = true` for queries** — optimize read operations
- **Use `.orElseThrow()`** — never `.get()` on Optional
- **Constructor injection** — `@RequiredArgsConstructor`

## Service Composition

- One Service = one business domain (Single Responsibility)
- Cross-domain operations: inject the other Service, not its Repository
- Complex workflows: use `ApplicationEventPublisher` over deep service chains

## Error Handling

- Throw domain-specific exceptions: `PlannerNotFoundException`, `PlannerForbiddenException`
- Never catch-and-swallow — let `GlobalExceptionHandler` translate to HTTP
- Business validation (ownership check, limit check) belongs in Service, not Controller

## Forbidden Patterns

| Forbidden | Use Instead |
|-----------|-------------|
| `optional.get()` | `.orElseThrow(() -> new NotFoundException())` |
| `@Transactional` on private | Only on public methods |
| External API calls inside `@Transactional` | Move outside transaction |
| Catching and swallowing exceptions | Let GlobalExceptionHandler handle |
| Reaching into another Service's Repository | Inject the other Service |

## Template

```java
@Service
@RequiredArgsConstructor
@Transactional
public class UserService {
    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public UserResponse findById(Long id) {
        return userRepository.findById(id)
            .map(UserResponse::from)
            .orElseThrow(() -> new NotFoundException("User", id));
    }

    @Transactional(readOnly = true)
    public List<UserResponse> findAll() {
        return userRepository.findAll().stream()
            .map(UserResponse::from)
            .toList();
    }

    public UserResponse create(CreateUserRequest request) {
        User user = new User(request.name(), request.email());
        return UserResponse.from(userRepository.save(user));
    }

    public void delete(Long id) {
        if (!userRepository.existsById(id)) {
            throw new NotFoundException("User", id);
        }
        userRepository.deleteById(id);
    }
}
```

**Reference:** `PlannerCommandService.java`
