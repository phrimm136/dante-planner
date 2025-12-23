# Architecture Overview - Backend Services

Complete guide to the layered architecture pattern used in **Spring Boot (Java 17)** backend microservices.

## Table of Contents

* [Layered Architecture Pattern](#layered-architecture-pattern)
* [Request Lifecycle](#request-lifecycle)
* [Service Comparison](#service-comparison)
* [Directory Structure Rationale](#directory-structure-rationale)
* [Module Organization](#module-organization)
* [Separation of Concerns](#separation-of-concerns)

---

## Layered Architecture Pattern

### The Four Layers

```
┌─────────────────────────────────────┐
│         HTTP Request                │
└───────────────┬─────────────────────┘
                ↓
┌─────────────────────────────────────┐
│  Layer 1: CONTROLLERS               │
│  - REST endpoint definitions        │
│  - Request mapping (@Get/Post...)   │
│  - Validation trigger               │
│  - Delegate to services             │
│  - NO business logic                │
└───────────────┬─────────────────────┘
                ↓
┌─────────────────────────────────────┐
│  Layer 2: SERVICES                  │
│  - Business logic                   │
│  - Orchestration                    │
│  - Transaction boundaries           │
│  - Call repositories                │
│  - No HTTP knowledge                │
└───────────────┬─────────────────────┘
                ↓
┌─────────────────────────────────────┐
│  Layer 3: REPOSITORIES              │
│  - Data access abstraction          │
│  - JPA / QueryDSL operations        │
│  - Query optimization               │
│  - Optional caching                 │
└───────────────┬─────────────────────┘
                ↓
┌─────────────────────────────────────┐
│         Database (MySQL)            │
└─────────────────────────────────────┘
```

> **Note**: In Spring Boot, route definitions are part of Controllers (`@RestController`). There is no separate Routes layer as in Express.

### Why This Architecture?

**Testability:**

* Each layer can be tested independently
* Easy to mock dependencies using Spring Test / Mockito
* Clear test boundaries

**Maintainability:**

* Changes isolated to specific layers
* Business logic separated from HTTP concerns
* Predictable code locations

**Reusability:**

* Services reusable by controllers, schedulers, listeners
* Repositories hide persistence implementation
* Business logic not tied to web layer

**Scalability:**

* Easy to add new endpoints
* Consistent architectural patterns
* Supports growing teams and codebases

---

## Request Lifecycle

### Complete Flow Example

```
1. HTTP POST /api/users
   ↓
2. Spring DispatcherServlet receives request
   ↓
3. Filter chain executes:
   - SecurityFilterChain (authentication/authorization)
   - Logging / audit filters
   ↓
4. HandlerInterceptor (optional):
   - Context propagation
   - Audit metadata
   ↓
5. Controller method invoked (@PostMapping)
   - @RequestBody deserialization
   - Jakarta Validation (@Valid)
   ↓
6. Controller delegates to service:
   - userService.create(request)
   ↓
7. Service executes business logic:
   - Business rule validation
   - userRepository.save(entity)
   ↓
8. Repository performs DB operation:
   - JPA / QueryDSL query execution
   ↓
9. Response flows back:
   Repository → Service → Controller → Client
```

### Filter & Interceptor Execution Order

**Critical:** Order matters

```
1. Servlet Filters
   - Observability / tracing
   - Authentication / authorization
   - Request logging

2. Spring MVC Interceptors
   - Audit context
   - Permission checks

3. Controller invocation

4. Exception resolution
   - @RestControllerAdvice

5. Response filters
```

**Rule:** Exception handlers must be globally registered and never duplicated per controller.

---

## Service Comparison

### Notification Service (Mature Pattern ✅)

**Strengths:**

* Thin controllers with no business logic
* Clear service boundaries
* Consistent constructor-based dependency injection
* Centralized exception handling
* Strong validation discipline

**Example Structure:**

```
notification/src/main/java/...
├── controller/
│   ├── BaseController.java          ✅ Common response helpers
│   ├── NotificationController.java  ✅ Thin REST layer
│   └── EmailController.java         ✅ Clear patterns
├── service/
│   ├── NotificationService.java     ✅ Business logic
│   └── BatchingService.java         ✅ Single responsibility
├── repository/
│   └── NotificationRepository.java  ✅ Data access
└── config/
    └── WebConfig.java
```

**Use as template** for new services.

### Form Service (Transitioning ⚠️)

**Strengths:**

* Advanced workflow architecture
* Strong observability integration
* Context propagation via interceptors
* Granular permission system

**Weaknesses:**

* Overloaded controllers
* Inconsistent package naming
* Configuration access scattered
* Repository abstraction inconsistently applied

**Learn from:** workflow modules, interceptors
**Avoid:** large controllers, configuration lookups inside services

---

## Directory Structure Rationale

### Controller Package

**Purpose:** Handle HTTP request/response concerns

**Contents:**

* `BaseController.java` – shared response utilities
* `{Feature}Controller.java`

**Naming:** PascalCase + `Controller`

**Responsibilities:**

* Request mapping
* Parameter binding
* Trigger validation
* Delegate to services
* Return HTTP responses

---

### Service Package

**Purpose:** Business logic and orchestration

**Contents:**

* `{Feature}Service.java`

**Responsibilities:**

* Business rules
* Transactions (`@Transactional`)
* Orchestration across repositories
* No web or framework concerns

---

### Repository Package

**Purpose:** Persistence abstraction

**Contents:**

* `{Entity}Repository.java`

**Responsibilities:**

* JPA / QueryDSL queries
* Persistence logic
* Caching where applicable
* No business rules

---

### Config Package

**Purpose:** Centralized configuration

**Contents:**

* `@Configuration`
* `@ConfigurationProperties`

**Pattern:** Typed, validated configuration

---

### DTO & Domain Packages

**DTOs:**

* Request / response payloads
* API boundary objects

**Domain:**

* JPA entities
* Domain models

---

## Module Organization

### Feature-Based Organization

```
workflow/
├── core/              # Core engine
├── service/           # Workflow services
├── action/            # System actions
├── domain/            # Domain models
├── validation/        # Validators
└── util/              # Utilities
```

**Use when:**

* Feature exceeds ~5 classes
* Clear sub-domains exist

---

### Flat Organization

```
controller/UserController.java
service/UserService.java
repository/UserRepository.java
```

**Use when:**

* Feature is small
* No clear sub-domains

---

## Separation of Concerns

### What Goes Where

**Controllers:**

* Request mapping
* Validation trigger
* Delegation
* Response construction

**Services:**

* Business logic
* Rules enforcement
* Transactions

**Repositories:**

* Database access
* Query logic

---

### Example: User Creation

**Controller:**

```java
@PostMapping("/users")
public ResponseEntity<UserResponse> create(@Valid @RequestBody CreateUserRequest request) {
    return ResponseEntity.ok(userService.create(request));
}
```

**Service:**

```java
@Transactional
public UserResponse create(CreateUserRequest request) {
    if (userRepository.existsByEmail(request.getEmail())) {
        throw new ConflictException("Email already exists");
    }
    User user = userRepository.save(User.from(request));
    return UserResponse.from(user);
}
```

**Repository:**

```java
public interface UserRepository extends JpaRepository<User, Long> {
    boolean existsByEmail(String email);
}
```

**Notice:** Each layer has a single, well-defined responsibility.

---

**Related Files:**

* `SKILL.md` – Main backend guide
* `routing-and-controllers.md` – Controller patterns
* `services-and-repositories.md` – Service and repository patterns
