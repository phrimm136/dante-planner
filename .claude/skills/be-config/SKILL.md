---
name: be-config
description: Spring Boot configuration and monitoring. Properties, profiles, actuator, Sentry.
---

# Backend Configuration Patterns

## Rules

- **Use `@ConfigurationProperties`** - Not `System.getenv()`
- **Externalize all config** - No hardcoded values
- **Use SLF4J logging** - Not `System.out.println()`
- **Enable actuator health** - Required for production

## Forbidden → Use Instead

| Forbidden | Use Instead |
|-----------|-------------|
| `System.getenv("KEY")` | `@ConfigurationProperties` |
| Hardcoded config | `application.properties` |
| `System.out.println()` | `log.info()` |
| No health check | Actuator `/actuator/health` |

## Config Properties Template

```java
@Configuration
@ConfigurationProperties(prefix = "app")
@Validated
@Getter
@Setter
public class AppProperties {
    @NotBlank
    private String name;

    @NotNull
    @Min(1)
    private Integer maxRetries;

    @NotBlank
    private String apiKey;
}
```

## application.properties Template

```properties
# Application
app.name=${APP_NAME:LimbusPlanner}
app.max-retries=${MAX_RETRIES:3}
app.api-key=${API_KEY}

# Active profile
spring.profiles.active=${SPRING_PROFILES_ACTIVE:dev}

# JPA
spring.jpa.show-sql=false
spring.jpa.hibernate.ddl-auto=validate

# Actuator
management.endpoints.web.exposure.include=health,info,metrics
management.endpoint.health.show-details=when_authorized

# Logging
logging.level.root=INFO
logging.level.org.danteplanner=DEBUG
```

## Profile-specific Config

```properties
# application-dev.properties
spring.jpa.show-sql=true
logging.level.org.danteplanner=DEBUG

# application-prod.properties
spring.jpa.show-sql=false
logging.level.org.danteplanner=INFO
```

## Logging Template

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

## Sentry Config (if used)

```properties
sentry.dsn=${SENTRY_DSN}
sentry.environment=${SENTRY_ENVIRONMENT:development}
sentry.traces-sample-rate=0.1
```

## Reference

- Pattern: `application.properties`, `JacksonConfig.java`
- Why: `docs/learning/backend-patterns.md`
