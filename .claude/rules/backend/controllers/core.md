---
paths:
  - "backend/**/controller/**/*.java"
  - "backend/**/*Controller.java"
---

# Controller Core Patterns

## Mandatory Rules

- **Use `/api/` prefix** - All endpoints start with `/api/`
- **Constructor injection** - Use `@RequiredArgsConstructor`, not `@Autowired`
- **No business logic** - Delegate to Service layer

## Forbidden Patterns

| Forbidden | Use Instead |
|-----------|-------------|
| `@RequestMapping("/users")` | `@RequestMapping("/api/users")` |
| `@Autowired` field | `@RequiredArgsConstructor` + final |
| Business logic in controller | Move to Service |

## Template

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

**Reference:** `PlannerController.java`
