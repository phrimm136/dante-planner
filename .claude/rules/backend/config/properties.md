---
paths:
  - "backend/**/config/**/*.java"
  - "backend/**/*Config.java"
  - "backend/**/*Properties.java"
  - "backend/**/resources/application*.properties"
  - "backend/**/resources/application*.yml"
---

# Configuration Properties Patterns

## Mandatory Rules

- **Use `@ConfigurationProperties`** - Not `System.getenv()`
- **Externalize all config** - No hardcoded values

## Forbidden Pattern

| Forbidden | Use Instead |
|-----------|-------------|
| `System.getenv("KEY")` | `@ConfigurationProperties` |
| Hardcoded config | `application.properties` |

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
```

## Profile-specific Config

```properties
# application-dev.properties
spring.jpa.show-sql=true

# application-prod.properties
spring.jpa.show-sql=false
```

**Reference:** `application.properties`, `JacksonConfig.java`
