---
name: be-controller
description: Spring Boot REST controller patterns. Endpoints, validation, DTOs, response handling.
---

# Backend Controller Patterns

## Rules

- **Use `/api/` prefix** - All endpoints start with `/api/`
- **Use DTOs, not entities** - Never expose JPA entities in API
- **Always `@Valid`** - Add to all `@RequestBody` parameters
- **Constructor injection** - Use `@RequiredArgsConstructor`, not `@Autowired`
- **No business logic** - Delegate to Service layer

## Forbidden → Use Instead

| Forbidden | Use Instead |
|-----------|-------------|
| `@RequestMapping("/users")` | `@RequestMapping("/api/users")` |
| Return `Entity` | Return `ResponseDTO` |
| `@RequestBody` without `@Valid` | `@Valid @RequestBody` |
| `@Autowired` field | `@RequiredArgsConstructor` + final |
| Business logic in controller | Move to Service |

## Controller Template

```java
@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {
    private final UserService userService;

    @PostMapping
    public ResponseEntity<UserResponse> create(@Valid @RequestBody CreateUserRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(userService.create(request));
    }

    @GetMapping("/{id}")
    public ResponseEntity<UserResponse> get(@PathVariable Long id) {
        return ResponseEntity.ok(userService.findById(id));
    }

    @PutMapping("/{id}")
    public ResponseEntity<UserResponse> update(
            @PathVariable Long id,
            @Valid @RequestBody UpdateUserRequest request) {
        return ResponseEntity.ok(userService.update(id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        userService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
```

## Request DTO Template

```java
public record CreateUserRequest(
    @NotBlank String name,
    @Email String email,
    @Size(min = 8) String password
) {}
```

## Response DTO Template

```java
public record UserResponse(Long id, String name, String email) {
    public static UserResponse from(User entity) {
        return new UserResponse(entity.getId(), entity.getName(), entity.getEmail());
    }
}
```

## Reference

- Pattern: `PlannerController.java`
- Why: `docs/learning/backend-patterns.md`
