---
paths:
  - "backend/**/sse/**/*.java"
  - "backend/**/*SseService.java"
  - "backend/**/*SseController.java"
---

# Server-Sent Events (SSE) Patterns

## Mandatory Rules

- **Use SSE for server-to-client** - Simple, unidirectional
- **Cache user settings per SSE connection** - ConcurrentHashMap, invalidate on change
- **No blocking in async** - Use reactive patterns

## Template

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

**Reference:** `PlannerSseService.java`
