---
paths:
  - "backend/**/exception/**/*.java"
  - "backend/**/*ExceptionHandler.java"
  - "backend/**/advice/**/*.java"
---

# Exception Handling Patterns

## Mandatory Rules

- **No empty catch blocks** — log and handle appropriately
- **No stack traces to client** — generic error messages only
- **One exception class per distinct business error**

## Exception Hierarchy

```
RuntimeException
  ├── PlannerNotFoundException     → 404
  ├── PlannerForbiddenException    → 403
  ├── PlannerConflictException     → 409  (carries serverVersion)
  ├── PlannerLimitExceededException→ 409
  ├── RateLimitExceededException   → 429
  ├── InvalidTokenException        → 401
  ├── UserBannedException          → 403  (carries userId, bannedAt)
  └── UserTimedOutException        → 403  (carries userId, timeoutUntil)
```

- Custom exceptions extend `RuntimeException` (unchecked)
- Add structured fields with `@Getter` — the handler reads them

## Logging Levels by Severity

| Scenario | Level | Sentry |
|---|---|---|
| Expected user error (not found, forbidden) | `log.warn` | No |
| Business conflict (duplicate vote, sync conflict) | `log.warn` | No |
| Security events (token revoked, account deleted) | `log.warn` | Yes |
| Unexpected application errors | `log.error` | Yes |
| SSE disconnect / async timeout | `log.debug` | No |

## Information Disclosure Rule

- Do not expose internal error codes or field names that reveal API schema
- Map structural validation errors to generic `VALIDATION_ERROR`
- Expose only user-fixable codes (`EMPTY_CONTENT`, `SIZE_EXCEEDED`)

## Forbidden Patterns

| Forbidden | Use Instead |
|-----------|-------------|
| `catch (Exception e) {}` | Log and rethrow/handle |
| `ex.printStackTrace()` in response | Generic error message |
| `@ExceptionHandler(Exception.class)` without Sentry | Always capture unexpected errors |
| Logging full stack at WARN for expected errors | Reserve stack traces for ERROR |

## Exception Handler Template

```java
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler(NotFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(NotFoundException ex) {
        log.warn("Not found: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
            .body(new ErrorResponse("NOT_FOUND", ex.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult().getFieldErrors().stream()
            .map(e -> e.getField() + ": " + e.getDefaultMessage())
            .collect(Collectors.joining(", "));
        return ResponseEntity.badRequest()
            .body(new ErrorResponse("VALIDATION_ERROR", message));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGeneric(Exception ex) {
        log.error("Unexpected error", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(new ErrorResponse("INTERNAL_ERROR", "An unexpected error occurred"));
    }
}

public record ErrorResponse(String code, String message) {}
```

## Custom Exception Template

```java
@Getter
public class PlannerConflictException extends RuntimeException {
    private final Long serverVersion;

    public PlannerConflictException(String message, Long serverVersion) {
        super(message);
        this.serverVersion = serverVersion;
    }
}
```

**Reference:** `GlobalExceptionHandler.java`
