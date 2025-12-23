# Routing and Controllers - Spring Boot Best Practices

Complete guide to clean request mapping and controller patterns in Spring Boot.

## Table of Contents

* Controllers: Mapping Only
* BaseController / Global Handler Pattern
* Good Examples
* Anti-Patterns
* Refactoring Guide
* Error Handling
* HTTP Status Codes

---

## Controllers: Mapping Only

### The Golden Rule

**Controllers should ONLY:**

* Define request mappings (`@GetMapping`, `@PostMapping`)
* Declare validation constraints
* Delegate to services
* Return DTOs / ResponseEntity

**Controllers should NEVER:**

* Contain business logic
* Access repositories directly
* Implement workflow logic
* Contain complex permission checks

---

## Clean Controller Pattern

```java
@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @GetMapping("/{id}")
    public ResponseEntity<UserDto> getUser(@PathVariable Long id) {
        return ResponseEntity.ok(userService.getUser(id));
    }

    @PostMapping
    public ResponseEntity<UserDto> createUser(@Valid @RequestBody CreateUserRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(userService.createUser(request));
    }
}
```

**Key Points:**

* No try/catch
* No database access
* Thin delegation layer

---

## Base Error Handling Pattern

### Why NOT a BaseController?

In Spring Boot, **inheritance-based BaseController is discouraged**.
Use **composition via `@RestControllerAdvice`** instead.

---

## Global Error Handler

```java
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler(NotFoundException.class)
    public ResponseEntity<ApiError> handleNotFound(NotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiError.of(ex.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleValidation(MethodArgumentNotValidException ex) {
        return ResponseEntity.badRequest()
                .body(ApiError.fromValidationErrors(ex));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleUnexpected(Exception ex) {
        log.error("Unexpected error", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiError.of("Internal server error"));
    }
}
```

**Benefits:**

* Centralized error handling
* No duplication
* Clean controllers

---

## Good Examples

### Example: Notification Controller

```java
@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService service;

    @GetMapping
    public List<NotificationDto> getNotifications() {
        return service.getNotifications();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public NotificationDto create(@Valid @RequestBody CreateNotificationRequest request) {
        return service.create(request);
    }
}
```

**Why this is good:**

* No logic in controller
* Declarative validation
* Clear mappings

---

## Anti-Patterns

### ❌ Business Logic in Controller

```java
@PostMapping("/submit")
public ResponseEntity<?> submit(@RequestBody Map<String, Object> body) {
    // ❌ Permission checks
    // ❌ Workflow logic
    // ❌ Repository calls
    // ❌ 200+ lines of logic
}
```

**Why this is bad:**

* Hard to test
* Hard to reuse
* Violates SRP

---

## Refactoring Guide

### Step 1: Move Logic to Service

```java
@Service
@RequiredArgsConstructor
public class PostService {

    public PostResult createPost(CreatePostRequest request, Long userId) {
        checkPermission(userId);
        executeWorkflow(request);
        return PostResult.success();
    }
}
```

### Step 2: Controller Delegates

```java
@PostMapping
public ResponseEntity<PostResult> create(@Valid @RequestBody CreatePostRequest request) {
    return ResponseEntity.ok(service.createPost(request, getCurrentUserId()));
}
```

---

## Validation

```java
public record CreateUserRequest(
        @NotBlank String email,
        @NotBlank String name
) {}
```

* Validation errors → 400 automatically
* Handled by `@RestControllerAdvice`

---

## HTTP Status Codes

| Code | Use Case          |
| ---- | ----------------- |
| 200  | Success           |
| 201  | Created           |
| 204  | No Content        |
| 400  | Bad Request       |
| 401  | Unauthorized      |
| 403  | Forbidden         |
| 404  | Not Found         |
| 409  | Conflict          |
| 422  | Validation Failed |
| 500  | Internal Error    |

---

## Summary Rules

* Controllers are NOT services
* Controllers are NOT workflow engines
* Controllers delegate
* Services contain logic
* Repositories access data
* Errors are handled globally

---

**Related Files:**
- [SKILL.md](SKILL.md) - Main guide
- [services-and-repositories.md](services-and-repositories.md) - Service layer details
- [complete-examples.md](complete-examples.md) - Full refactoring examples
