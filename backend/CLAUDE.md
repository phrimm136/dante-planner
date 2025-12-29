# BACKEND DEVELOPMENT GUIDELINES

**Tech Stack:** Spring Boot + Java + JPA/Hibernate + Bean Validation (JSR-380) + PostgreSQL

**CRITICAL: Use Skill tool `backend-dev-guidelines` BEFORE writing any backend code.**

---

## Backend Resource Map (MUST READ before coding)

**Before writing ANY backend code, read the relevant resource:**

| Task Type | MUST Read | Purpose |
|-----------|-----------|---------|
| **Controller** | `routing-and-controllers.md` | REST API patterns, validation |
| **Service** | `services-and-repositories.md` | Business logic, transactions |
| **Repository** | `database-patterns.md` | JPA queries, custom queries |
| **DTO/Validation** | `validation-patterns.md` | Bean Validation, DTO patterns |
| **Entity** | `database-patterns.md` | JPA annotations, relationships |
| **Exception Handling** | `async-and-errors.md` | Global handlers, custom exceptions |
| **Configuration** | `configuration.md` | Properties, beans, profiles |
| **Security** | `security-guide.md` | Authentication, authorization |
| **WebSocket** | `websocket-guide.md` | Real-time communication |
| **Testing** | `testing-guide.md` | Unit tests, integration tests |

**Location**: `.claude/skills/backend-dev-guidelines/resources/`

---

## Core Principles (Priority Order)

**Follow these principles in order of importance:**

1. **SOLID Principles (All Five Apply)**
   - **S (Single Responsibility)**: One Service = One business domain
   - **O (Open/Closed)**: Extend via interfaces, not modification
   - **L (Liskov Substitution)**: Interface implementations are substitutable
   - **I (Interface Segregation)**: Small, focused interfaces
   - **D (Dependency Inversion)**: Depend on abstractions (interfaces), not concretions

2. **Layered Architecture**
   - Controller → Service → Repository (NEVER skip layers)
   - Each layer has distinct responsibility
   - Use DTOs to cross layer boundaries

3. **DRY (Don't Repeat Yourself)**
   - Repeated business logic → Extract to Service method
   - Repeated validation → Custom Validator annotation
   - Repeated config → `application.properties`

4. **Separation of Concerns**
   - Controller: HTTP request/response only
   - Service: Business logic only
   - Repository: Data access only

5. **Fail Fast**
   - Validate at API boundary with Bean Validation
   - Use `@Valid` + constraints for immediate failure
   - Don't wait to discover invalid data deep in business logic

**Apply These Patterns:**
- **DTO Pattern**: Separate API contract from domain model (MANDATORY)
- **Repository Pattern**: JPA provides this (extend `JpaRepository`)
- **Service Layer Pattern**: Encapsulate business logic with `@Service`
- **Dependency Injection**: Constructor injection (NOT field injection)
- **Strategy Pattern**: Multiple implementations via interface
- **Template Method**: Common CRUD via `JpaRepository` inheritance

**Spring Boot Specific Rules:**
- **Constructor Injection > Field Injection**: Testable, immutable, avoids circular deps
- **Interface for Service Layer**: Loose coupling, easier testing/mocking
- **DTO for API Boundaries**: NEVER expose Entity directly in REST API
- **@Transactional at Service Layer**: Transaction management in business logic layer only

---

## FORBIDDEN PATTERNS (Will be Blocked)

| Pattern | Why Forbidden | Use Instead |
|---------|---------------|-------------|
| Business logic in Controller | Violates SRP, not testable | Move to Service layer |
| SQL queries in Service | Violates layering | Move to Repository |
| Direct entity exposure in API | Tight coupling, security risk | Use DTOs |
| `@Autowired` field injection | Hard to test, circular deps | Constructor injection |
| Hardcoded configuration values | Not environment-specific | `application.properties` / `@Value` |
| Ignoring exceptions (`catch {}`) | Silent failures | Log + handle appropriately |
| Transactions in Controller | Wrong layer | @Transactional in Service |
| `Optional.get()` without check | NullPointerException risk | `.orElseThrow()` or `.orElse()` |
| Magic numbers/strings | Not maintainable | Constants class or `application.properties` |

---

## Properties/Constants Workflow (MANDATORY)

Before using ANY hardcoded value (URLs, numbers, strings):

1. Check if it's environment-specific:
   - **Environment-specific** (varies by dev/prod): DB URL, API keys, external URLs → `application.properties` + `@Value` or `@ConfigurationProperties`
   - **Business constant** (same everywhere): MAX_RETRY_COUNT, TIMEOUT_SECONDS, REGEX_PATTERNS → Constants class (e.g., `AppConstants.java`)
2. State "**Properties Check:** Using [PROPERTY_NAME] from application.properties" or "**Constants Check:** Using [CONSTANT_NAME] from AppConstants"

**Examples:**
- `spring.datasource.url` = Environment-specific (different DB per environment)
- `MAX_LOGIN_ATTEMPTS = 3` = Business constant (same in all environments)

---

## Execution Protocol (STOP GATES)

**State flags:** `skillLoaded`, `resourceRead`, `propertiesChecked`, `patternChecked`, `intentStated`

### STOP if:
- ❌ `skillLoaded = false` → Use Skill tool: `backend-dev-guidelines`
- ❌ `resourceRead = false` → Read relevant resource from map above
- ❌ `propertiesChecked = false` and using hardcoded values → Check application.properties or Constants
- ❌ `patternChecked = false` → Search and read similar files
- ❌ `intentStated = false` → State intent (WHAT, WHY, HOW)

---

## Import Order (Enforced)

1. Java standard library
2. Spring Framework
3. Spring Boot
4. Spring Data JPA
5. Bean Validation (jakarta.validation)
6. Persistence API (jakarta.persistence)
7. Third-party libraries (Lombok, etc.)
8. Project packages (domain → application → infrastructure)

**See `backend-dev-guidelines` skill for detailed examples.**

---

## Quick Reference: Where to Find Patterns

**See `backend-dev-guidelines` skill for references.**

---

## Layered Architecture (MANDATORY)

```
Controller Layer (REST API)
    ↓ (DTOs only)
Service Layer (Business Logic)
    ↓ (Entities)
Repository Layer (Data Access)
    ↓ (SQL/JPA)
Database
```

**Rules:**
- Controller → Service: Pass DTOs, return DTOs
- Service → Repository: Work with Entities
- Service: Contains @Transactional business logic
- Repository: JPA queries only, no business logic
- Never skip layers (Controller → Repository ❌)

---

## Bean Validation Quick Reference

```java
// Common annotations
@NotNull                    // Cannot be null
@NotEmpty                   // Cannot be null or empty (String, Collection)
@NotBlank                   // Cannot be null, empty, or whitespace (String only)
@Size(min = 1, max = 100)   // String length or Collection size
@Min(0) @Max(100)           // Number range
@Email                      // Valid email format
@Pattern(regexp = "...")    // Regex validation
@Valid                      // Cascade validation to nested objects
```

**Usage:**
```java
// In DTO
public class CreateUserRequest {
    @NotBlank(message = "Username is required")
    @Size(min = 3, max = 20, message = "Username must be 3-20 characters")
    private String username;
}

// In Controller
@PostMapping("/users")
public ResponseEntity<UserResponse> createUser(@Valid @RequestBody CreateUserRequest request) {
    // Validation happens automatically before method execution
}
```

---

## Database & Persistence (MANDATORY)

**Schema Migration (Flyway):**
- ALL schema changes via Flyway migrations - NEVER alter database manually
- Migration files: `V{version}__{description}.sql` (e.g., `V001__create_user_table.sql`)
- Migrations are immutable - NEVER edit existing migrations
- Rollback strategy: Create new migration to revert changes

**N+1 Query Prevention:**
- Use `@EntityGraph` or `JOIN FETCH` for eager loading collections
- Example: `@EntityGraph(attributePaths = {"orders", "orders.items"})`
- Monitor query count in logs during development

**Pagination (MANDATORY for list endpoints):**
- ALL list endpoints MUST use `Pageable` parameter
- Default page size: 20, max size: 100
- Example: `Page<User> findAll(Pageable pageable)`

**Transaction Best Practices:**
- Keep transactions SHORT - no external API calls inside @Transactional
- Default isolation: `READ_COMMITTED` (use `REPEATABLE_READ` only if needed)
- Only `@Transactional` on PUBLIC methods (proxy limitation)
- Read-only transactions: `@Transactional(readOnly = true)` for queries

**SQL Injection Prevention:**
- ALWAYS use `@Param` in `@Query` - NEVER string concatenation
- Bad: `@Query("SELECT u FROM User u WHERE name = '" + name + "'")`
- Good: `@Query("SELECT u FROM User u WHERE name = :name")`

---

## WebSocket & Real-Time (Quick Reference)

**When to use WebSocket:**
- Real-time notifications (new message, status update)
- Live updates (dashboard, chat)
- Server-to-client push (broadcast announcements)

**Destination Patterns:**
- `/topic/*` - Broadcast to all subscribers
- `/queue/*` - Point-to-point messaging
- `/user/{userId}/queue/*` - User-specific messages
- `/app/*` - Client-to-server messages

**See `backend-dev-guidelines` skill `websocket-guide.md` for implementation details.**

---

## Security & Validation (MANDATORY)

**API Security:**
- Rate Limiting: Use Bucket4j or Spring rate limiter for public APIs (e.g., 100 req/min per IP)
- CORS: Configure allowed origins explicitly, NEVER use `allowedOrigins("*")` in production
- CSRF: Enabled by default, disable only for stateless JWT APIs

**Input Validation (Two Layers):**
1. **Format Validation** (Controller): Bean Validation annotations (`@Valid`, `@NotBlank`, etc.)
2. **Business Validation** (Service): Business rules (e.g., "email must not already exist")

**NEVER trust client input:**
- Validate ALL request parameters, path variables, request bodies
- Sanitize before using in queries or logs
- Use parameterized queries to prevent SQL injection

**Exception Handling:**
- Global `@RestControllerAdvice` for consistent error responses
- Log errors with context (user ID, request ID, stack trace)
- NEVER expose internal details in error messages to client

---

## Monitoring & Observability

**Actuator Endpoints (MANDATORY):**
- Health check: `/actuator/health` (readiness/liveness probes)
- Metrics: `/actuator/metrics` (JVM, DB, HTTP metrics)
- Prometheus: `/actuator/prometheus` (for Grafana dashboards)

**See `backend-dev-guidelines` skill `sentry-and-monitoring.md` for setup.**

---

## Critical Rules

- **CRITICAL: Load skill with Skill tool FIRST** (backend-dev-guidelines)
- **CRITICAL: Read relevant resource docs BEFORE writing code**
- **CRITICAL: State intent BEFORE every Write/Edit - explain WHAT, WHY, and HOW**
- **CRITICAL: Check existing patterns BEFORE writing new code**
- **CRITICAL: Review code IMMEDIATELY after writing - NEVER batch reviews**
- **CRITICAL: Verify SKILL COMPLIANCE in every review (first item)**
- **CRITICAL: Check FORBIDDEN PATTERNS in every review (second item)**
- **CRITICAL: Use Constructor injection, NOT field injection**
- **CRITICAL: Use DTOs for API, NEVER expose entities directly**
- **CRITICAL: Business logic in Service, NOT in Controller or Repository**
- **CRITICAL: Use Flyway for schema changes - NEVER alter database manually**
- **CRITICAL: Add @Valid to ALL @RequestBody parameters - NEVER skip validation**
- **CRITICAL: Use @Param in @Query - NEVER concatenate strings (SQL injection risk)**
- **CRITICAL: @Transactional only on PUBLIC methods - private methods won't work**
- **CRITICAL: Paginate ALL list endpoints - use Pageable parameter**
