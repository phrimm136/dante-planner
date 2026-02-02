---
paths:
  - "backend/**/exception/**/*.java"
  - "backend/**/*ExceptionHandler.java"
  - "backend/**/advice/**/*.java"
---

# Exception Handling Patterns

## Mandatory Rules

- **No empty catch blocks** - Log and handle appropriately
- **No stack traces to client** - Generic error messages only

## Forbidden Patterns

| Forbidden | Use Instead |
|-----------|-------------|
| `catch (Exception e) {}` | Log and rethrow/handle |
| `ex.printStackTrace()` in response | Generic error message |
| `Thread.sleep()` in async | Reactive/non-blocking |

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
public class NotFoundException extends RuntimeException {
    public NotFoundException(String entity, Object id) {
        super(entity + " not found with id: " + id);
    }
}
```

**Reference:** `GlobalExceptionHandler.java`
