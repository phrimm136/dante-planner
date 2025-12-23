# Async Patterns and Error Handling

Complete guide to **asynchronous execution patterns and error handling** in **Spring Boot (Java 17)** backend services.

## Table of Contents

* [Async Execution Best Practices](#async-execution-best-practices)
* [Future & Parallel Error Handling](#future--parallel-error-handling)
* [Custom Exception Types](#custom-exception-types)
* [Global Exception Handling](#global-exception-handling)
* [Error Propagation](#error-propagation)
* [Common Async Pitfalls](#common-async-pitfalls)

---

## Async Execution Best Practices

### Prefer Synchronous by Default

Spring MVC is **thread-per-request**. Use async execution **only when necessary** (I/O-bound, long-running tasks).

```java
// ✅ Default: synchronous service call
public UserResponse getUser(Long id) {
    return userService.findById(id);
}
```

### Use @Async Explicitly

```java
@Async
public CompletableFuture<Void> sendEmailAsync(String email) {
    emailClient.send(email);
    return CompletableFuture.completedFuture(null);
}
```

Rules:

* Enable with `@EnableAsync`
* Use dedicated `TaskExecutor`
* Never assume async without annotation

---

## Future & Parallel Error Handling

### CompletableFuture (Fail Fast)

```java
try {
    CompletableFuture.allOf(
        userService.loadUsers(),
        profileService.loadProfiles(),
        settingsService.loadSettings()
    ).join();
} catch (CompletionException ex) {
    throw ex.getCause();
}
```

### Handle Errors Individually

```java
List<CompletableFuture<?>> futures = List.of(
    userService.loadUsers(),
    profileService.loadProfiles(),
    settingsService.loadSettings()
);

futures.forEach(future -> future.exceptionally(ex -> {
    // log / track error
    return null;
}));
```

**Guideline:**

* `allOf()` → one failure fails all
* `exceptionally()` / `handle()` → partial success allowed

---

## Custom Exception Types

### Base Application Exception

```java
public abstract class AppException extends RuntimeException {

    private final String code;
    private final int status;

    protected AppException(String message, String code, int status) {
        super(message);
        this.code = code;
        this.status = status;
    }

    public String getCode() {
        return code;
    }

    public int getStatus() {
        return status;
    }
}
```

### Specific Exceptions

```java
public class ValidationException extends AppException {
    public ValidationException(String message) {
        super(message, "VALIDATION_ERROR", 400);
    }
}

public class NotFoundException extends AppException {
    public NotFoundException(String message) {
        super(message, "NOT_FOUND", 404);
    }
}

public class ForbiddenException extends AppException {
    public ForbiddenException(String message) {
        super(message, "FORBIDDEN", 403);
    }
}

public class ConflictException extends AppException {
    public ConflictException(String message) {
        super(message, "CONFLICT", 409);
    }
}
```

### Usage

```java
if (user == null) {
    throw new NotFoundException("User not found");
}

if (user.getAge() < 18) {
    throw new ValidationException("User must be 18+");
}
```

---

## Global Exception Handling

### @RestControllerAdvice

```java
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(AppException.class)
    public ResponseEntity<ErrorResponse> handleAppException(AppException ex) {
        return ResponseEntity.status(ex.getStatus())
            .body(new ErrorResponse(ex.getMessage(), ex.getCode()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnknown(Exception ex) {
        // log / track error
        return ResponseEntity.status(500)
            .body(new ErrorResponse("Internal server error", "INTERNAL_ERROR"));
    }
}
```

**Rules:**

* No try/catch in controllers
* All exceptions handled centrally
* Unknown errors are never leaked

---

## Error Propagation

### Proper Error Flow

```java
@Repository
public class UserRepository {
    public List<User> findAll() {
        try {
            return entityManager.createQuery("...").getResultList();
        } catch (Exception ex) {
            // track error
            throw ex;
        }
    }
}

@Service
public class UserService {
    public List<User> getUsers() {
        try {
            return userRepository.findAll();
        } catch (Exception ex) {
            // optional enrichment
            throw ex;
        }
    }
}
```

**Rule:**

* Catch only to add context
* Always rethrow
* Let global handler convert to HTTP

---

## Common Async Pitfalls

### Fire-and-Forget (Dangerous)

```java
// ❌ NEVER: fire-and-forget without handling
emailService.sendAsync(email);
```

```java
// ✅ Handle errors explicitly
emailService.sendAsync(email)
    .exceptionally(ex -> {
        // log / track
        return null;
    });
```

---

### Blocking Async Threads

```java
// ❌ NEVER: block async thread
@Async
public void process() {
    Thread.sleep(5000);
}
```

```java
// ✅ Use non-blocking I/O or scheduling
```

---

### Unhandled Async Exceptions

```java
@Bean
public AsyncUncaughtExceptionHandler asyncExceptionHandler() {
    return (ex, method, params) -> {
        // log / track async exception
    };
}
```

---

## Summary Rules

* Prefer synchronous execution
* Use async only when justified
* Centralize exception handling
* Never swallow exceptions
* Avoid blocking async threads
* Always propagate errors upward

---

**Related Files:**

* `SKILL.md`
* `sentry-and-monitoring.md`
* `complete-examples.md`
