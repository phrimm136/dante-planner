# Backend Tests

## Which tier

Tier follows where the asserted truth lives, not which layer the class sits in.

- **Unit** (`@ExtendWith(MockitoExtension.class)`, no Docker) — the truth is this codebase's own logic: validators, mappers, key formats, policies, service logic that does not depend on a dependency's guarantee.
- **Integration** (MySQL/Redis Testcontainers, `@ActiveProfiles("it")`, `@Tag("containerized")`) — the truth is a dependency's guarantee: SQL and dialect behavior, constraints, transactions, `AFTER_COMMIT` effects, replication, FULLTEXT, Redis atomicity, and every controller.

Repositories belong to the second tier without exception. A repository test against an embedded database asserts against a database this project does not ship; `DataIntegrityViolationException` message text alone differs between H2 and MySQL.

No `@WebMvcTest`, `@MockBean`, `@SpyBean`, or `@DataJpaTest` anywhere. Each distinct mock set mints its own application context, and the context cache is what keeps this suite's wall clock down.

Run: default `test` task is both tiers (Docker required); `-PexcludeTags=containerized` is the Docker-free tier.

## What a test may assume about data

Every integration class shares one database and they run concurrently. A test owns the rows it creates and nothing else.

- **Fixtures carry a unique identity.** `TestDataFactory` sub-addresses every email it issues, so two classes asking for `owner@example.com` get different rows. Anything built by hand needs the same treatment.
- **Never truncate in the shared database.** `deleteAll()` there removes rows a concurrent neighbour is mid-assertion on. `@ResourceLock` does not make it safe: a lock excludes only the classes that declare it.
- **Assert about your rows, never about the table.** `repository.count()`, `findAll()`, and `$.content.length()` are claims about every test's data. Narrow to the id the test created.
- **When the subject is a per-context singleton** — a write buffer, a cache, a scheduler — take
  `registerOwnDatabase` as well. The point there is not the data but the context: a distinct cache
  key gives the class its own bean, which is what the assertion actually needs.
- **When the subject genuinely is a whole table or index** — title search, keyword facets, a global rebuild procedure — take `SharedMySqlContainerSupport.registerOwnDatabase(registry, "name")` and truncate freely inside it. Costs one application context, so reach for a narrowed assertion first.

## `@Transactional` on a test class

Only where the transaction boundary is itself the subject: constraint violations that surface at
flush, and repository tests that navigate a lazy graph. Never on a test that drives HTTP, where
the endpoint owns the boundary; that is the case `TestIsolationConventionTest` enforces. Never as a cleanup mechanism — unique fixtures already provide that.

Three costs, all silent:

- `@TransactionalEventListener(AFTER_COMMIT)` never fires, so a rolled-back controller test exercises a path production never takes.
- InnoDB flushes its FULLTEXT index cache at commit, so `MATCH ... AGAINST` cannot see the test's own rows and a `q=` search returns nothing.
- The transaction is bound to a `ThreadLocal`, and JUnit's executor blocks in `ForkJoinTask.join()`. A blocked ForkJoin thread runs another task while waiting, which then inherits the binding and fails with `Cannot start new transaction without ending existing transaction`.

## Parallelism

Classes run concurrently, methods within a class do not (`junit-platform.properties`). Only the global setting can express that split.

- **Never annotate a class with `@Execution`.** The mode propagates to its methods, and concurrent methods share the class's `@BeforeEach` fixtures.
- **Never add a second `@BeforeEach`.** JUnit does not order siblings, so a cleanup method can run after the setup it was meant to precede. Put cleanup as the first statements of the one existing setup method.

## Mechanics

- The `it` profile runs Flyway + `ddl-auto=validate` against the real migrated schema — the authoritative schema guard; never weaken it to `create-drop`.
- A class registering its own datasource must register `spring.flyway.url/user/password` too, not just `spring.datasource.*`, or Flyway connects to the prod-pinned datasource and fails.
- A class carrying `@Tag("containerized")` ends in `IT`; everything else ends in `Test`. The suffix
  is what the tier rules select on, so a misnamed class silently escapes them.
- Naming: `methodName_WhenCondition_ExpectedBehavior`, or an invariant phrase for a name a comment cites; enforced by `architecture/TestNamingConventionTest`.
- Time-dependent JWT tests inject a fixed `Clock` — never 1 ms expiries (see `JwtTokenServiceTest`).
- Deterministic ordering via `entity.setCreatedAt(now.minusSeconds(n))` — never `Thread.sleep()`.
- Build reusable entities with `TestDataFactory`.
- Gradle does not capture application stdout in this project's test output — localize a failing integration test by assertion-based bisection, not log statements.
