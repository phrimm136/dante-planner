# Main Source

Package-by-feature: feature roots (`admin`, `auth`, `comment`, `moderation`, `notification`, `planner`, `user`) plus `shared`, each keeping layer subpackages (`controller`, `service`, `repository`, ...) — ArchUnit `..service..`/`..controller..` matchers depend on them. `FeatureBoundaryTest` lets a class reach another module only through that module's `api` package (subpackages included), keeps the module graph free of cycles and each `api` package free of its own module's internals, and requires an interface implemented outside its module to end in `Provider`, `Participant`, `Target` or `Handler` or carry `@TypeLabel` (implemented only by enums, records and interfaces). Its frozen list only shrinks; never add an entry. `shared` imports no feature module; a feature that the platform needs to call implements an interface `shared` declares.

## Transactions & replica routing (most important)

- `@Transactional(readOnly = true)` is the REPLICA-ROUTING SIGNAL (`ReadOnlyRoutingDataSource`), not a free optimization. Read-after-write paths must use the GTID cookie gate / primary re-check / tombstone layer; never assume a readOnly read sees your own just-committed write.
- `@Transactional` at the service layer only, on public methods only. No external API calls inside a transaction.
- Layering: Controller → Service → Repository, never skip. Cross-module: inject the other module's `api` interface; its services, repositories and entities are internal.

## Async

- The async model is `@Scheduled` + `@TransactionalEventListener(AFTER_COMMIT)` + Redis pub/sub + the outbox dispatch executor. `@Async`/`@EnableAsync`/`ThreadPoolTaskExecutor` are banned everywhere else; the single exception is `DomainEventEagerDispatch.onDomainEventRecorded` on `OutboxAsyncConfig`'s pool, frozen by name in `ConventionBaselineTest` with a staleness check. Do not introduce another executor or `@Async` path; a `@Scheduled` job that must not wait behind long jobs can run on its own single-thread `ThreadPoolTaskScheduler`. The freshness marker batcher (`RaiseBatcher`, thread `marker-raise`) owns one daemon thread by design: Redis `WAIT` counts only one connection's writes, so one thread must own that connection.
- AFTER_COMMIT listeners run with no live transaction: any write they perform directly needs `@Transactional(propagation = REQUIRES_NEW)` or it silently never commits. The eager hop deliberately carries no such annotation — it writes nothing itself, and the `DomainEventDispatcher` it calls opens a transaction of its own.
- An observer effect is not written by a listener at all. The causing transaction commits a `domain_events` row; the dispatcher derives the effect, and its arms enqueue SSE pushes that are released only after that dispatch commits.

## Controllers & DTOs

- All endpoints under `/api/`; plural-noun lowercase-hyphen paths; non-CRUD actions as a noun suffix on POST (`/api/planners/{id}/publish`).
- Constructor injection (`@RequiredArgsConstructor`) — no `@Autowired` fields.
- Return `ResponseEntity<T>`; POST create → 201, DELETE → 204.
- `@Valid` on every `@RequestBody`, and on nested DTO/list fields — Jakarta does not cascade without it.
- DTOs only at the API boundary, never entities; separate request and response DTOs; mapping via a static `from(entity)` on the DTO, not in Controller/Service.
- Never `.get()` on Optional — `.orElseThrow()` with a domain exception in the Service; `ApiExceptionHandler` maps it.
- SSE and REST endpoints live in separate controllers.

## Repositories & entities

- `@Param` on every `@Query` parameter — never string concatenation.
- Collections `FetchType.LAZY`; prevent N+1 with `@EntityGraph` or `JOIN FETCH`; explicit `countQuery` on paginated `@Query`.
- List endpoints take `Pageable` (default size 20, max 100) — never an unbounded `List<Entity>`.
- Check-then-act races: when a PK or unique key can own the invariant, insert against it (`INSERT IGNORE` and read the affected count, or handle the violation); a `@Lock(LockModeType.PESSIMISTIC_WRITE)` repository method only for an invariant no constraint can express. Counters: atomic `@Modifying @Query("... SET x = x + 1")`, not read-modify-write.
- Method names carry the cardinality: a plural noun returns a collection, `…ById`/`…Of` returns at most one row, a verb prefix (`try…`, `insert…`, `refresh…`, `increment…`) mutates and returns the affected count.
- Projection interfaces live in the repository package with a `*Row` suffix; repositories never return `*Response` types — a `*Row` is mapped by a static `from(row)` on the DTO.
- A method returning `boolean`/`Boolean` is named `is…`, never `get…`

## Security

- JWT access tokens are read from COOKIES (not the Authorization header) by `JwtAuthenticationFilter` in `auth/security/`. The filter also consults `TokenBlacklistService` (Redis-backed, fail-open ladder) and skips ASYNC dispatch; `shared/config/SecurityConfig` places it in the chain.
- RSA PEM keys are validated ≥2048-bit at startup, fail-fast.
- Verify ownership server-side; never trust client-supplied IDs.
- No wildcard CORS origins — env-driven explicit list. Rate-limit public endpoints via bucket4j-redis.
- Secrets come from env-backed properties only, never hardcoded.

## Exceptions

- One class per business error, extending `DomainException`, which builds its own RFC 9457 body: PlannerNotFound→404, PlannerForbidden→403, PlannerConflict→409, PlannerLimitExceeded→409, RateLimitExceeded→429, InvalidToken→401, UserBanned/UserTimedOut→403. `ApiExceptionHandler` renders; a handler exists only for a response a body cannot express, such as `SessionRevokedException`'s cookie clear in `auth/security/SessionRevokedExceptionAdvice`.
- `PlannerConflictException`'s field is `actualVersion`, surfaced in JSON as the `serverVersion` problem property.
- Errors answer `application/problem+json`: the code rides as the `code` property, the text as `detail`.
- Typed degradation: DB/Redis/Lettuce failures map to 503 with `DB_UNAVAILABLE` / `AUTH_UNAVAILABLE` / `RATE_LIMIT_UNAVAILABLE` — deliberately not sent to Sentry.
- Expected user errors: `log.warn`, no stack, no Sentry. Unexpected errors: `log.error` + Sentry. SSE disconnect/async timeout: `log.debug`.
- Never expose internals: structural validation errors map to a generic `VALIDATION_ERROR`; expose only user-fixable codes.

## Config & logging

- SLF4J only — no `System.out.println`.
- `@ConfigurationProperties` (never `System.getenv()`); `@PostConstruct` fail-fast validation when loading external resources from configured paths.
- Environment-specific values → `application.properties`; environment-invariant business constants → a constants class; never inline hardcode either.
- Actuator health/metrics/prometheus endpoints stay exposed.
