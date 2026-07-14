# Main Source

Package-by-feature: feature roots (`admin`, `auth`, `comment`, `moderation`, `notification`, `planner`, `user`) plus `shared`, each keeping layer subpackages (`controller`, `service`, `repository`, ...) — ArchUnit `..service..`/`..controller..` matchers depend on them. `FeatureBoundaryTest` freezes cross-feature edges via an explicit allowlist; extend the allowlist deliberately, never delete the rule. `shared` is not a pure sink (it legitimately imports some feature code); keep its rule in the truthful residual form rather than faking a pure-sink rule.

## Transactions & replica routing (most important)

- `@Transactional(readOnly = true)` is the REPLICA-ROUTING SIGNAL (`ReadOnlyRoutingDataSource`), not a free optimization. Read-after-write paths must use the GTID cookie gate / primary re-check / tombstone layer; never assume a readOnly read sees your own just-committed write.
- `@Transactional` at the service layer only, on public methods only. No external API calls inside a transaction.
- Layering: Controller → Service → Repository, never skip. Cross-domain: inject the other Service, never its Repository.

## Async

- Deliberately NO `@Async`/`@EnableAsync`/`ThreadPoolTaskExecutor` anywhere. The async model is `@Scheduled` + `@TransactionalEventListener(AFTER_COMMIT)` + Redis pub/sub — do not introduce thread pools.
- AFTER_COMMIT listeners run with no live transaction: any write they perform needs `@Transactional(propagation = REQUIRES_NEW)` or it silently never commits.

## Controllers & DTOs

- All endpoints under `/api/`; plural-noun lowercase-hyphen paths; non-CRUD actions as a noun suffix on POST (`/api/planners/{id}/publish`).
- Constructor injection (`@RequiredArgsConstructor`) — no `@Autowired` fields.
- Return `ResponseEntity<T>`; POST create → 201, DELETE → 204.
- `@Valid` on every `@RequestBody`, and on nested DTO/list fields — Jakarta does not cascade without it.
- DTOs only at the API boundary, never entities; separate request and response DTOs; mapping via a static `from(entity)` on the DTO, not in Controller/Service.
- Never `.get()` on Optional — `.orElseThrow()` with a domain exception in the Service; `GlobalExceptionHandler` maps it.
- SSE and REST endpoints live in separate controllers.

## Repositories & entities

- `@Param` on every `@Query` parameter — never string concatenation.
- Collections `FetchType.LAZY`; prevent N+1 with `@EntityGraph` or `JOIN FETCH`; explicit `countQuery` on paginated `@Query`.
- List endpoints take `Pageable` (default size 20, max 100) — never an unbounded `List<Entity>`.
- Check-then-act races: `@Lock(LockModeType.PESSIMISTIC_WRITE)` repository method. Counters: atomic `@Modifying @Query("... SET x = x + 1")`, not read-modify-write.

## Security

- JWT access tokens are read from COOKIES (not the Authorization header) by `JwtAuthenticationFilter` in `shared/security/`, which also consults `TokenBlacklistService` (Redis-backed, fail-open ladder). The filter skips ASYNC dispatch.
- RSA PEM keys are validated ≥2048-bit at startup, fail-fast.
- Verify ownership server-side; never trust client-supplied IDs.
- No wildcard CORS origins — env-driven explicit list. Rate-limit public endpoints via bucket4j-redis.
- Secrets come from env-backed properties only, never hardcoded.

## Exceptions

- One class per business error, extending `RuntimeException`, with `@Getter` fields the handler reads: PlannerNotFound→404, PlannerForbidden→403, PlannerConflict→409, PlannerLimitExceeded→409, RateLimitExceeded→429, InvalidToken→401, UserBanned/UserTimedOut→403.
- `PlannerConflictException`'s field is `actualVersion`, surfaced in JSON as `serverVersion`.
- Typed degradation: DB/Redis/Lettuce failures map to 503 with `DB_UNAVAILABLE` / `AUTH_UNAVAILABLE` / `RATE_LIMIT_UNAVAILABLE` — deliberately not sent to Sentry.
- Expected user errors: `log.warn`, no stack, no Sentry. Unexpected errors: `log.error` + Sentry. SSE disconnect/async timeout: `log.debug`.
- Never expose internals: structural validation errors map to a generic `VALIDATION_ERROR`; expose only user-fixable codes.

## Config & logging

- SLF4J only — no `System.out.println`.
- `@ConfigurationProperties` (never `System.getenv()`); `@PostConstruct` fail-fast validation when loading external resources from configured paths.
- Environment-specific values → `application.properties`; environment-invariant business constants → a constants class; never inline hardcode either.
- Actuator health/metrics/prometheus endpoints stay exposed.
