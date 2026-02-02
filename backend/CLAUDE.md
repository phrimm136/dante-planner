# BACKEND DEVELOPMENT GUIDELINES

**Tech Stack:** Spring Boot + Java + JPA/Hibernate + Bean Validation (JSR-380) + PostgreSQL

---

## Auto-Loading Rules

**Patterns automatically load based on file type:**

| File Type | Auto-Loaded Rules | Location |
|-----------|-------------------|----------|
| **Controllers** | Core, DTO, Validation, HTTP Status | `.claude/rules/backend/controllers/` |
| **Services** | Core, Transactions, Repositories | `.claude/rules/backend/services/` |
| **Security** | Auth, CORS | `.claude/rules/backend/security/` |
| **Async/SSE** | SSE, Exceptions | `.claude/rules/backend/async/` |
| **Config** | Properties, Logging | `.claude/rules/backend/config/` |
| **Tests** | Unit, Controller, Repository Tests | `.claude/rules/backend/testing/` |

**How it works:** Edit a controller → relevant rules load automatically (no manual skill loading)

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

**State flags:** `propertiesChecked`, `patternChecked`, `intentStated`

### STOP if:
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

---

## Quick Reference: Where to Find Patterns

| Need Pattern For | Check These Files |
|------------------|-------------------|
| Controller | `PlannerController.java` |
| Service | `PlannerService.java` |
| Repository | `PlannerRepository.java` |
| Exception Handler | `GlobalExceptionHandler.java` |
| Security Config | `SecurityConfig.java` |
| SSE Service | `PlannerSseService.java` |

---

## Layered Architecture

**Controller → Service → Repository** (never skip layers)

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

---

## Critical Rules (Domain-Specific)

1. **Constructor injection only** - No `@Autowired` field injection
2. **DTOs for API** - Never expose entities directly
3. **`@Valid` on all `@RequestBody`** - Never skip validation
4. **`@Param` in `@Query`** - Never concatenate strings (SQL injection)
5. **Flyway for schema changes** - Never alter database manually
