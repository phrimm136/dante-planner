---
paths:
  - "backend/**/service/**/*.java"
  - "backend/**/*Service.java"
---

# Transaction Patterns

## Guidelines

| Operation | `@Transactional` | `readOnly` |
|-----------|------------------|------------|
| Query (read) | Yes | `true` |
| Create/Update/Delete | Yes | `false` (default) |
| Mixed (read then write) | Yes | `false` |
| Pure computation | No | N/A |

## Template

```java
@Service
@Transactional  // Class-level default
public class UserService {

    @Transactional(readOnly = true)  // Optimize reads
    public UserResponse findById(Long id) {
        return userRepository.findById(id)
            .map(UserResponse::from)
            .orElseThrow(() -> new NotFoundException("User", id));
    }

    public UserResponse create(CreateUserRequest request) {
        // Write operation - uses class-level @Transactional (readOnly=false)
        User user = new User(request.name(), request.email());
        return UserResponse.from(userRepository.save(user));
    }
}
```

## Forbidden Patterns

| Forbidden | Why | Use Instead |
|-----------|-----|-------------|
| ThreadLocal set before try | May leak if exception thrown | Set INSIDE try block, remove in finally |
| Client timestamp for sync | Client clock unreliable | Server-controlled syncVersion counter |
