---
paths:
  - "backend/**/controller/**/*.java"
  - "backend/**/*Controller.java"
---

# Controller Core Patterns

## Mandatory Rules

- **Use `/api/` prefix** — all endpoints start with `/api/`
- **Constructor injection** — use `@RequiredArgsConstructor`, not `@Autowired`
- **No business logic** — delegate to Service layer
- **Always return `ResponseEntity<T>`** — not raw entity or bare DTO

## Endpoint Naming

- Nouns, not verbs: `/api/planners`, not `/api/getPlanner`
- Lowercase with hyphens: `/api/planner-configs`
- Collections plural: `/api/planners`
- Sub-resources: `/api/planners/{id}/comments`
- Non-CRUD actions as noun suffix on POST: `/api/planners/{id}/publish`

## Versioning

- URL path versioning when needed: `/api/v1/`, `/api/v2/`
- Add versioning when a breaking change is imminent, not speculatively

## Forbidden Patterns

| Forbidden | Use Instead |
|-----------|-------------|
| `@RequestMapping("/users")` | `@RequestMapping("/api/users")` |
| `@Autowired` field | `@RequiredArgsConstructor` + final |
| Business logic in controller | Move to Service |
| `.get()` on Optional in controller | Throw in Service, let GlobalExceptionHandler map |
| Reading `HttpServletRequest` for business data | Only for infrastructure concerns (IP, User-Agent) |
| Mixing SSE and REST in same controller | Separate controllers |

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
