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

## Skill Reference

| Task | Skill |
|------|-------|
| Controllers, endpoints, DTOs | `be-controller` |
| Services, repositories, entities | `be-service` |
| Security, auth, JWT, CORS | `be-security` |
| Async, SSE, error handling | `be-async` |
| Tests | `be-testing` |
| Configuration, properties, monitoring | `be-config` |
| Sentry error tracking | `error-tracking` |
| Route testing with auth | `route-tester` |

**Usage:** `Skill tool: be-controller` (load skill BEFORE writing code)

---

## Core Principles (Priority Order)

**You MUST follow these principles in order of importance:**

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

## Forbidden Patterns

**Hook-enforced** (see `.claude/hooks/forbidden-patterns.json`):
- Field injection, empty catch blocks, string concat in @Query, @Transactional on private, etc.

**Architecture (requires review)**:

| Pattern | Use Instead |
|---------|-------------|
| Business logic in Controller | Move to Service |
| Hardcoded config values | `application.properties` |

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

## Layered Architecture

**Controller → Service → Repository** (never skip layers)

See `be-service` skill for templates and detailed rules.

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

## Critical Rules (Domain-Specific)

1. **Constructor injection only** - No `@Autowired` field injection
2. **DTOs for API** - Never expose entities directly
3. **`@Valid` on all `@RequestBody`** - Never skip validation
4. **`@Param` in `@Query`** - Never concatenate strings (SQL injection)
5. **Flyway for schema changes** - Never alter database manually

*Procedural rules (skill loading, pattern reading, intent) enforced by hooks.*
