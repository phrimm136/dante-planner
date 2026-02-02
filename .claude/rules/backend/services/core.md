---
paths:
  - "backend/**/service/**/*.java"
  - "backend/**/*Service.java"
---

# Service Core Patterns

## Mandatory Rules

- **`@Transactional` on class** - Default for service classes
- **`readOnly = true` for queries** - Optimize read operations
- **Use `.orElseThrow()`** - Never `.get()` on Optional
- **Constructor injection** - `@RequiredArgsConstructor`

## Forbidden Patterns

| Forbidden | Use Instead |
|-----------|-------------|
| `optional.get()` | `.orElseThrow(() -> new NotFoundException())` |
| `@Transactional` on private | Only on public methods |

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

**Reference:** `PlannerService.java`
