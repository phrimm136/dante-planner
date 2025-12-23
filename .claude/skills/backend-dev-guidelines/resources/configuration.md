# Configuration Management – Spring Boot Unified Configuration Pattern

Complete guide to managing configuration in Spring Boot (Java 17) backend microservices.

---

## Table of Contents

* Unified Configuration Overview
* NEVER Use `System.getenv()` or `@Value` Scattered
* Configuration Structure
* Environment-Specific Configuration
* Secrets Management
* Migration Guide

---

## Unified Configuration Overview

### Why a Unified Configuration Layer?

**Problems with ad-hoc configuration access:**

* ❌ No type safety when using `System.getenv()`
* ❌ Scattered `@Value` annotations
* ❌ Hard to validate at startup
* ❌ Difficult to test
* ❌ Runtime failures due to missing values

**Benefits of Spring Boot Unified Configuration:**

* ✅ Type-safe configuration via `@ConfigurationProperties`
* ✅ Single source of truth
* ✅ Automatic validation at startup
* ✅ Easy mocking in tests
* ✅ Clear structure per domain
* ✅ Native support for environment overrides

---

## NEVER Use `System.getenv()` or Scattered `@Value`

### The Rule

```java
// ❌ NEVER
String timeout = System.getenv("TIMEOUT_MS");

@Value("${db.host}")
private String dbHost;
```

```java
// ✅ ALWAYS
@ConfigurationProperties(prefix = "app")
public class AppConfig {
    private ServerConfig server;
    private DatabaseConfig database;
}
```

### Why This Matters

**Problems:**

* No compile-time safety
* Missing values cause runtime crashes
* Difficult to track usage

**With Unified Config:**

```java
int port = appConfig.getServer().getPort();
String host = appConfig.getDatabase().getHost();
```

---

## Configuration Structure

### Root Configuration Class

```java
@Configuration
@EnableConfigurationProperties(AppConfig.class)
public class ConfigModule {}
```

```java
@ConfigurationProperties(prefix = "app")
@Validated
public class AppConfig {
    @NotNull
    private DatabaseConfig database;

    @NotNull
    private ServerConfig server;

    private TokenConfig tokens;
    private SentryConfig sentry;
}
```

### Sub-Configuration Example

```java
public class DatabaseConfig {
    @NotBlank
    private String host;

    @Min(1)
    private int port;

    private String username;
    private String password;
    private String name;
}
```

```java
public class ServerConfig {
    @Min(1)
    private int port;

    @NotBlank
    private String sessionSecret;
}
```

---

## Environment-Specific Configuration

### application.yml

```yaml
app:
  database:
    host: localhost
    port: 5432
    username: postgres
    password: postgres
    name: blog_dev

  server:
    port: 8080
    session-secret: dev-secret

  tokens:
    jwt: dev-jwt-secret

  sentry:
    dsn: https://example
    environment: development
    traces-sample-rate: 0.1
```

### application-prod.yml

```yaml
app:
  database:
    host: prod-db.internal
    password: ${DB_PASSWORD}

  sentry:
    environment: production
```

### Precedence Order

1. `application-{profile}.yml`
2. Environment variables
3. `application.yml`
4. Default values in code

---

## Secrets Management

### DO NOT Commit Secrets

```gitignore
application-*.yml
.env
*.pem
*.key
```

### Use Environment Variables in Production

```yaml
app:
  database:
    password: ${DB_PASSWORD}

  tokens:
    jwt: ${JWT_SECRET}
```

### Recommended

* Kubernetes Secrets
* AWS Parameter Store / Secrets Manager
* Vault

---

## Validation at Startup

```java
@Component
@RequiredArgsConstructor
public class ConfigValidator {

    private final AppConfig appConfig;

    @PostConstruct
    void validate() {
        if (appConfig.getTokens().getJwt() == null) {
            throw new IllegalStateException("JWT secret not configured");
        }
    }
}
```

Startup will fail fast if configuration is invalid.

---

## Migration Guide

### Step 1: Find Direct Access

```bash
grep -r "System.getenv\|@Value" src/main/java
```

### Step 2: Define Config Classes

```java
@ConfigurationProperties(prefix = "app.keycloak")
public class KeycloakConfig {
    private String realm;
    private String client;
    private String baseUrl;
    private Duration timeout;
}
```

### Step 3: Replace Usage

**Before:**

```java
String baseUrl = System.getenv("KEYCLOAK_BASE_URL");
```

**After:**

```java
String baseUrl = appConfig.getKeycloak().getBaseUrl();
```

---

## Testing Configuration

```java
@SpringBootTest
@TestPropertySource(properties = {
    "app.database.host=test-db",
    "app.database.port=1234"
})
class ConfigTest {

    @Autowired
    AppConfig config;

    @Test
    void shouldLoadConfig() {
        assertEquals("test-db", config.getDatabase().getHost());
    }
}
```

---

## Key Rules Summary

* One root config object (`AppConfig`)
* No `System.getenv()` in business code
* No scattered `@Value`
* Validate at startup
* Use profiles for environments
* Secrets via environment variables

---

**Related Files:**

* SKILL.md
* testing-guide.md
