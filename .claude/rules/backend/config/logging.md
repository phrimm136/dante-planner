---
paths:
  - "backend/**/service/**/*.java"
  - "backend/**/controller/**/*.java"
  - "backend/**/*.java"
---

# Logging Patterns

## Mandatory Rules

- **Use SLF4J logging** - Not `System.out.println()`
- **Enable actuator health** - Required for production

## Forbidden Pattern

| Forbidden | Use Instead |
|-----------|-------------|
| `System.out.println()` | `log.info()` |
| No health check | Actuator `/actuator/health` |

## Template

```java
@Service
@Slf4j
public class MyService {

    public void process(String id) {
        log.info("Processing id={}", id);
        try {
            // work
        } catch (Exception e) {
            log.error("Failed to process id={}", id, e);
            throw e;
        }
    }
}
```

## Logging Levels

| Level | When to Use |
|-------|-------------|
| TRACE | Detailed flow, usually disabled |
| DEBUG | Development diagnostics |
| INFO | Production informational (startup, config, major events) |
| WARN | Recoverable issues, deprecated usage |
| ERROR | Errors that need attention |

## Configuration

```properties
logging.level.root=INFO
logging.level.org.danteplanner=DEBUG
```
