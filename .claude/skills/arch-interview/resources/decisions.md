# Architectural Decisions Catalog

Quick reference of key architectural decisions with their trade-offs.

## Authentication & Security

### JWT Authentication
- **Choice:** JWT with refresh tokens
- **vs:** Session-based, API keys
- **Why:** Stateless, scalable, mobile-friendly
- **Trade-off:** Token revocation complexity → solved with blacklist
- **File:** `JwtAuthenticationFilter.java:98-198`

### Refresh Token Rotation
- **Choice:** Blacklist old refresh token when rotating
- **vs:** Simple refresh, no rotation
- **Why:** Detects token theft, limits breach damage
- **Trade-off:** Blacklist storage overhead
- **File:** `JwtAuthenticationFilter.java:266`

### SameSite=Lax Cookies
- **Choice:** CSRF disabled, rely on SameSite=Lax
- **vs:** CSRF tokens, SameSite=Strict
- **Why:** Simpler, sufficient for read-only GETs
- **Trade-off:** Browser dependency, GET must be safe
- **File:** `SecurityConfig.java:48-52`

### Role Hierarchy
- **Choice:** ADMIN > MODERATOR > NORMAL
- **vs:** Flat roles, permission-based
- **Why:** Implicit permissions, less code
- **Trade-off:** Less flexible for parallel roles
- **File:** `SecurityConfig.java:38-43`

## Data Layer

### DTO Pattern
- **Choice:** Separate DTOs, never expose entities
- **vs:** Expose entities, JsonView
- **Why:** API stability, security, avoid N+1
- **Trade-off:** Boilerplate mapping code
- **File:** `UserService.java:toDto()`

### Soft Delete with Grace Period
- **Choice:** Mark deleted, permanent after 30 days
- **vs:** Hard delete, soft only, archive table
- **Why:** User protection, data integrity, compliance
- **Trade-off:** Storage cost, query complexity
- **File:** `UserAccountLifecycleService.java`

## Architecture

### Layered Architecture
- **Choice:** Controller → Service → Repository (strict)
- **vs:** Fat controllers, CQRS
- **Why:** Single responsibility, testability, reusability
- **Trade-off:** More files, boilerplate
- **File:** Any controller + service pair

### Constructor Injection
- **Choice:** Constructor injection, never field `@Autowired`
- **vs:** Field injection, setter injection
- **Why:** Immutability, testability, circular dependency detection
- **Trade-off:** Verbosity, long constructors
- **File:** All services/controllers

### Bean Validation at Controller
- **Choice:** `@Valid` at controller, business rules in service
- **vs:** Manual validation, service-only validation
- **Why:** Fail fast, clear separation of concerns
- **Trade-off:** Two-layer validation
- **File:** `AuthController.java:56`

## Performance & Reliability

### Rate Limiting (Bucket4j)
- **Choice:** In-memory token bucket per user/IP
- **vs:** No limiting, Redis-based, fixed window
- **Why:** Smooth rate, fast, no external dependency
- **Trade-off:** Not distributed, lost on restart
- **File:** `RateLimitConfig.java`

### Flyway Migrations
- **Choice:** All schema via Flyway SQL
- **vs:** Manual SQL, Hibernate ddl-auto, Liquibase
- **Why:** Version control, repeatable, audit trail
- **Trade-off:** Immutable migrations, learning curve
- **File:** `db/migration/`

### Error Response Strategy
- **Choice:** Generic to client, detailed to Sentry
- **vs:** Expose full errors, no details
- **Why:** Security, debugging, UX
- **Trade-off:** Harder client debugging
- **File:** `GlobalExceptionHandler.java:166-189`

## Research Questions

For each decision, you should be able to answer:
1. What problem does this solve?
2. What are the alternatives?
3. Why was this chosen over alternatives?
4. What did we sacrifice (trade-offs)?
5. When would you NOT use this approach?

See `resources/examples/` for detailed implementations.
