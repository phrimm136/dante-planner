# Middleware Guide - Spring Boot Filter & Interceptor Patterns

Complete guide to creating and using middleware equivalents in Spring Boot (Java 17) backend microservices.

## Table of Contents

* [Authentication Filter](#authentication-filter)
* [Audit Context with ThreadLocal / MDC](#audit-context-with-threadlocal--mdc)
* [Global Error Handling](#global-error-handling)
* [Validation Handling](#validation-handling)
* [Composable Filters and Interceptors](#composable-filters-and-interceptors)
* [Execution Order](#execution-order)

---

## Authentication Filter

### SSO Authentication Filter Pattern

**Equivalent Concept:**

* Express Middleware → `OncePerRequestFilter`

**File:** `security/SsoAuthenticationFilter.java`

```java
@Component
public class SsoAuthenticationFilter extends OncePerRequestFilter {

    private final JwtTokenProvider jwtTokenProvider;

    public SsoAuthenticationFilter(JwtTokenProvider jwtTokenProvider) {
        this.jwtTokenProvider = jwtTokenProvider;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {

        String token = WebUtils.getCookie(request, "refresh_token") != null
                ? WebUtils.getCookie(request, "refresh_token").getValue()
                : null;

        if (token == null) {
            response.sendError(HttpStatus.UNAUTHORIZED.value(), "Not authenticated");
            return;
        }

        try {
            JwtClaims claims = jwtTokenProvider.validate(token);
            request.setAttribute("claims", claims);
            request.setAttribute("effectiveUserId", claims.getSubject());
            filterChain.doFilter(request, response);
        } catch (JwtException ex) {
            response.sendError(HttpStatus.UNAUTHORIZED.value(), "Invalid token");
        }
    }
}
```

---

## Audit Context with ThreadLocal / MDC

### Equivalent to AsyncLocalStorage

**Recommended Pattern:**

* `ThreadLocal` for programmatic access
* `MDC` for logging correlation

**File:** `audit/AuditContext.java`

```java
public record AuditContext(
        String userId,
        String userName,
        String impersonatedBy,
        String sessionId,
        Instant timestamp,
        String requestId
) {}
```

**Context Holder:**

```java
public final class AuditContextHolder {

    private static final ThreadLocal<AuditContext> CONTEXT = new ThreadLocal<>();

    public static void set(AuditContext context) {
        CONTEXT.set(context);
    }

    public static AuditContext get() {
        return CONTEXT.get();
    }

    public static void clear() {
        CONTEXT.remove();
    }
}
```

**Interceptor:**

```java
@Component
public class AuditInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String requestId = Optional.ofNullable(request.getHeader("X-Request-ID"))
                .orElse(UUID.randomUUID().toString());

        AuditContext context = new AuditContext(
                (String) request.getAttribute("effectiveUserId"),
                null,
                null,
                request.getSession(false) != null ? request.getSession().getId() : null,
                Instant.now(),
                requestId
        );

        AuditContextHolder.set(context);
        MDC.put("requestId", requestId);
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        AuditContextHolder.clear();
        MDC.clear();
    }
}
```

**Usage in Services:**

```java
AuditContext context = AuditContextHolder.get();
log.info("Operation executed by {}", context.userId());
```

---

## Global Error Handling

### Centralized Exception Handling

**Equivalent Concept:**

* Express error middleware → `@RestControllerAdvice`

**File:** `error/GlobalExceptionHandler.java`

```java
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(AppException.class)
    public ResponseEntity<ErrorResponse> handleAppException(AppException ex) {
        return ResponseEntity
                .status(ex.getStatus())
                .body(ErrorResponse.from(ex));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnknown(Exception ex) {
        Sentry.captureException(ex);
        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ErrorResponse("Internal server error", "INTERNAL_ERROR"));
    }
}
```

---

## Validation Handling

### Bean Validation Integration

**Pattern:**

* Validation occurs automatically before controller execution

```java
@PostMapping("/users")
public ResponseEntity<UserResponse> create(@Valid @RequestBody CreateUserRequest request) {
    return ResponseEntity.status(HttpStatus.CREATED).body(userService.create(request));
}
```

**Validation Errors:**

```java
@ExceptionHandler(MethodArgumentNotValidException.class)
public ResponseEntity<ValidationErrorResponse> handleValidation(MethodArgumentNotValidException ex) {
    return ResponseEntity.badRequest().body(ValidationErrorResponse.from(ex));
}
```

---

## Composable Filters and Interceptors

### Configuration-Based Composition

**File:** `config/WebConfig.java`

```java
@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final AuditInterceptor auditInterceptor;

    public WebConfig(AuditInterceptor auditInterceptor) {
        this.auditInterceptor = auditInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(auditInterceptor)
                .addPathPatterns("/api/**");
    }
}
```

**Security Filter Chain:**

```java
@Bean
SecurityFilterChain filterChain(HttpSecurity http, SsoAuthenticationFilter ssoFilter) throws Exception {
    return http
            .csrf(AbstractHttpConfigurer::disable)
            .addFilterBefore(ssoFilter, UsernamePasswordAuthenticationFilter.class)
            .authorizeHttpRequests(auth -> auth.anyRequest().authenticated())
            .build();
}
```

---

## Execution Order

### Request Lifecycle Order

```
Incoming HTTP Request
  ↓
Servlet Filters (Security, SSO)  ← FIRST
  ↓
Spring MVC Interceptors (Audit)
  ↓
Controller
  ↓
Service
  ↓
Repository
  ↓
@ResponseBody serialization
  ↓
Exception Handlers (@RestControllerAdvice)
  ↓
Response Sent
```

### Rules

* Filters execute **before** Spring MVC
* Interceptors wrap controller execution
* Exception handlers replace error middleware
* Context cleanup **must happen in `afterCompletion`**

---

**Related Files:**

* `SKILL.md`
* `routing-and-controllers.md`
* `async-and-errors.md`
