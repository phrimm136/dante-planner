# Observability Guide – Sentry Error Tracking & Performance Monitoring (Spring Boot)

Complete guide to **error tracking, performance monitoring, and cron supervision** using **Sentry (Java, Spring Boot)**.

> **MANDATORY RULE**
> **All errors MUST be captured in Sentry. No silent failures. No swallowed exceptions.**

---

## Table of Contents

* [Core Principles](#core-principles)
* [Sentry Initialization](#sentry-initialization)
* [Global Error Capture](#global-error-capture)
* [Service & Workflow Error Patterns](#service--workflow-error-patterns)
* [Performance Monitoring](#performance-monitoring)
* [Scheduled / Cron Job Monitoring](#scheduled--cron-job-monitoring)
* [Error Context Best Practices](#error-context-best-practices)
* [Common Mistakes](#common-mistakes)

---

## Core Principles

* **Every exception is captured**
* **Context > stack trace**
* **Errors propagate upward**
* **Sentry is not logging — it is observability**
* **PII must be scrubbed**

---

## Sentry Initialization

### Dependency (Spring Boot)

```xml
<dependency>
  <groupId>io.sentry</groupId>
  <artifactId>sentry-spring-boot-starter-jakarta</artifactId>
  <version>7.x.x</version>
</dependency>
```

---

### application.yml

```yaml
sentry:
  dsn: ${SENTRY_DSN}
  environment: ${SPRING_PROFILES_ACTIVE}
  traces-sample-rate: 0.1
  profiles-sample-rate: 0.1
  send-default-pii: false

  ignored-exceptions-for-type:
    - org.springframework.web.bind.MethodArgumentNotValidException
    - org.springframework.web.server.ResponseStatusException
```

---

### Global Configuration

```java
@Configuration
public class SentryConfig {

    @PostConstruct
    public void init() {
        Sentry.configureScope(scope -> {
            scope.setTag("service", "form");
            scope.setTag("runtime", "spring-boot");
            scope.setContext("java", Map.of(
                "version", System.getProperty("java.version"),
                "vendor", System.getProperty("java.vendor")
            ));
        });
    }
}
```

---

## Global Error Capture

### Global Exception Boundary (MANDATORY)

```java
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorResponse> handleException(
            Exception ex,
            HttpServletRequest request
    ) {
        Sentry.withScope(scope -> {
            scope.setTag("endpoint", request.getRequestURI());
            scope.setTag("method", request.getMethod());
            scope.setExtra("query", request.getQueryString());
            Sentry.captureException(ex);
        });

        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiErrorResponse.generic());
    }
}
```

> **Rule:**
> Controllers **NEVER** catch exceptions unless they rethrow or capture.

---

## Service & Workflow Error Patterns

### Service Layer (REQUIRED)

```java
@Transactional
public User createUser(CreateUserCommand command) {
    try {
        return userRepository.save(mapper.toEntity(command));
    } catch (Exception ex) {
        Sentry.captureException(ex, scope -> {
            scope.setTag("service", "UserService");
            scope.setTag("operation", "createUser");
            scope.setExtra("email", command.email());
        });
        throw ex;
    }
}
```

---

### Workflow / Use Case Boundary

```java
try {
    workflow.execute();
} catch (Exception ex) {
    Sentry.captureException(ex, scope -> {
        scope.setTag("workflow", "POST_CREATION");
        scope.setExtra("entityId", postId);
        scope.setExtra("userId", userId);
    });
    throw ex;
}
```

---

## Performance Monitoring

### Automatic (Out-of-the-box)

Sentry automatically instruments:

* Spring MVC
* RestTemplate / WebClient
* JDBC / Hibernate
* Thread pools

No manual setup required.

---

### Custom Spans (Advanced)

```java
Sentry.startTransaction("user.create", "service", transaction -> {
    transaction.setTag("layer", "service");

    return Sentry.startSpan("db.insert", span -> {
        return userRepository.save(user);
    });
});
```

---

## Scheduled / Cron Job Monitoring

### Scheduled Job Pattern

```java
@Scheduled(cron = "0 0 * * * *")
public void cleanupJob() {
    Sentry.startTransaction("cron.cleanup", "cron", tx -> {
        try {
            cleanupService.run();
        } catch (Exception ex) {
            Sentry.captureException(ex, scope -> {
                scope.setTag("cron.job", "cleanup");
            });
            throw ex;
        }
    });
}
```

> **Rule:**
> Cron jobs must **fail fast** and **report explicitly**.

---

## Error Context Best Practices

### Rich Context Example

```java
Sentry.withScope(scope -> {
    scope.setUser(new User() {{
        setId(user.getId());
        setEmail(maskEmail(user.getEmail()));
    }});

    scope.setTag("endpoint", request.getRequestURI());
    scope.setTag("method", request.getMethod());

    scope.setContext("operation", Map.of(
        "type", "workflow.complete",
        "workflowId", workflowId,
        "step", stepName
    ));

    scope.addBreadcrumb("Workflow step started");

    Sentry.captureException(ex);
});
```

---

## Common Mistakes

```java
// ❌ Swallowed exception
try {
    risky();
} catch (Exception ignored) {}

// ❌ Logging without Sentry
log.error("Something failed", ex);

// ❌ Generic errors
throw new RuntimeException("Error occurred");

// ❌ PII leakage
scope.setExtra("password", user.getPassword());

// ✅ Correct
catch (Exception ex) {
    Sentry.captureException(ex);
    throw ex;
}
```

---

## Naming Decision

**Recommended filename:**
`observability-guide-sentry-spring-boot.md`

**Related Files**

* [SKILL.md](SKILL.md)
* [async-and-errors.md](async-and-errors.md)
* [middleware-guide.md](middleware-guide.md)
