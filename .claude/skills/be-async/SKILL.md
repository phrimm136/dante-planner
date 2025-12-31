---
name: be-async
description: Async processing, SSE, and error handling.
---

# Backend Async Patterns

## Rules

- **No empty catch blocks** - Log and handle appropriately
- **No stack traces to client** - Generic error messages only
- **No blocking in async** - Use reactive patterns
- **Use SSE for server-to-client** - Simple, unidirectional

## Forbidden → Use Instead

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

## SSE Template

```java
@RestController
@RequestMapping("/api/events")
@RequiredArgsConstructor
public class SseController {
    private final SseService sseService;

    @GetMapping(produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter subscribe() {
        SseEmitter emitter = new SseEmitter(30_000L);
        sseService.register(emitter);
        return emitter;
    }
}

@Service
public class SseService {
    private final List<SseEmitter> emitters = new CopyOnWriteArrayList<>();

    public void register(SseEmitter emitter) {
        emitters.add(emitter);
        emitter.onCompletion(() -> emitters.remove(emitter));
        emitter.onTimeout(() -> emitters.remove(emitter));
    }

    public void broadcast(String event, Object data) {
        emitters.forEach(emitter -> {
            try {
                emitter.send(SseEmitter.event().name(event).data(data));
            } catch (IOException e) {
                emitters.remove(emitter);
            }
        });
    }
}
```

## Reference

- Pattern: `GlobalExceptionHandler.java`, `PlannerSseService.java`
- Why: `docs/learning/backend-patterns.md`
