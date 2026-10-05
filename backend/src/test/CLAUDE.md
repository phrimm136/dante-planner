# Backend Tests

## Which tier

Tier follows where the asserted truth lives, not which layer the class sits in.

- **Unit** (`@ExtendWith(MockitoExtension.class)`, no Docker) — the truth is this codebase's own logic: validators, mappers, key formats, policies, service logic that does not depend on a dependency's guarantee.
- **Integration** (MySQL/Redis Testcontainers, `@ActiveProfiles("it")`, `@Tag("containerized")`) — the truth is a dependency's guarantee: SQL and dialect behavior, constraints, transactions, `AFTER_COMMIT` effects, replication, FULLTEXT, Redis atomicity, and every controller.

Repositories belong to the second tier without exception. A repository test against an embedded database asserts against a database this project does not ship; `DataIntegrityViolationException` message text alone differs between H2 and MySQL.

No `@WebMvcTest`, `@MockitoBean`, `@MockitoSpyBean`, Boot's `@MockBean`/`@SpyBean`, or `@DataJpaTest` anywhere; the mock-bean rule (`architecture/TestMockBeanConventionTest`) enforces all but the first and last. Each distinct mock set mints its own application context, and the context cache is what keeps this suite's wall clock down.

Run: default `test` task is both tiers (Docker required); `-PexcludeTags=containerized` is the Docker-free tier.

## What a test may assume about data

Every integration class shares one database. A test owns the rows it creates and nothing
else — see **Parallelism** for which classes run beside it.

- **Fixtures carry a unique identity.** `TestDataFactory` sub-addresses every email it issues, so two classes asking for `owner@example.com` get different rows. Anything built by hand needs the same treatment.
- **Never truncate in the shared database.** `deleteAll()` there removes rows a concurrent neighbour is mid-assertion on. `@ResourceLock` does not make it safe: a lock excludes only the classes that declare it.
- **Assert about your rows, never about the table.** `repository.count()`, `findAll()`, and `$.content.length()` are claims about every test's data. Narrow to the id the test created.
- **When the subject is a per-context singleton** — a write buffer, a cache, a scheduler — extend
  `ExclusiveDatabaseSupport` as well. The point there is not the data but the context: its lock
  keeps every other class using that bean from running beside this one. Two beans in this
  codebase hold mutable state: `PlannerViewRecorder`'s flush buffer and `AbstractSseService`'s
  emitter registry. A new one is found by looking for a collection or atomic assigned inline in a
  `@Service` or `@Component`; per-call objects do not count.
- **When the subject genuinely is a whole table or index** — title search, keyword facets, a global rebuild procedure — extend `ExclusiveDatabaseSupport`. Its classes run one at a time on a database of their own, which holds whatever the earlier ones left: truncate what you count at the start of your setup (`emptyPlannerTables` empties the planner tables in raw SQL, whatever shape an earlier class left a planner in), or narrow to your rows. They serialize, so reach for a narrowed assertion first.

## `@Transactional` on a test class

Only where the transaction boundary is itself the subject: constraint violations that surface at
flush, and repository tests that navigate a lazy graph. Never on a test that drives HTTP, where
the endpoint owns the boundary; that is the case `TestIsolationConventionTest` enforces. Never as a cleanup mechanism — unique fixtures already provide that.

Three costs, all silent:

- `@TransactionalEventListener(AFTER_COMMIT)` never fires, so a rolled-back controller test exercises a path production never takes.
- InnoDB flushes its FULLTEXT index cache at commit, so `MATCH ... AGAINST` cannot see the test's own rows and a `q=` search returns nothing.
- The transaction is bound to a `ThreadLocal`, and JUnit's executor blocks in `ForkJoinTask.join()`. A blocked ForkJoin thread runs another task while waiting, which then inherits the binding and fails with `Cannot start new transaction without ending existing transaction`.

## Parallelism

The suite runs in one JVM, twelve classes at a time and in random order; the methods of one class
run on one thread (`junit-platform.properties`, `maxParallelForks = 1`). So every class runs beside
others against the same database, Redis and application context, and the ownership rules above are
what keeps the suite passing. Breaking one costs a debugging session in somebody else's class.

- **Never annotate a class with `@Execution`.** The mode propagates to its methods, and concurrent methods share the class's `@BeforeEach` fixtures.
- **Never add a second `@BeforeEach`.** JUnit does not order siblings, so a cleanup method can run after the setup it was meant to precede. Put cleanup as the first statements of the one existing setup method.

## Mechanics

- The `it` profile runs Flyway + `ddl-auto=validate` against the real migrated schema — the authoritative schema guard; never weaken it to `create-drop`.
- A class registering its own datasource must register `spring.flyway.url/user/password` too, not just `spring.datasource.*`, or Flyway connects to the prod-pinned datasource and fails.
- A class carrying `@Tag("containerized")` ends in `IT`; everything else ends in `Test`. The suffix
  is what the tier rules select on, so a misnamed class silently escapes them.
- Naming: `subject_WhenCondition_Expectation`, one style with no exceptions; enforced by `architecture/TestNamingConventionTest`. The method name is the only name: no `@DisplayName` on a test class or method, enforced by `architecture/TestDisplayNameConventionTest`.
- Time-dependent JWT tests inject a fixed `Clock` — never 1 ms expiries (see `JwtTokenServiceTest`).
- Deterministic ordering via `entity.setCreatedAt(now.minusSeconds(n))` — never `Thread.sleep()`.
- Build reusable entities with `TestDataFactory`.
- A failing test is retried once in a fresh fork, and the build fails even when the retry passes. A failed execution followed by a passing one in the `build/test-results` XML (in CI, the `backend-test-reports` artifact, because the PR gate runs `check -q` and prints no per-test lines) means the test passes without the rest of the suite: it is nondeterministic, or it depends on order or suite size. Before filing it as flaky, rerun the full suite (for a CI failure, re-run the CI job): class order comes from the random orderer's seed (`junit.jupiter.execution.order.random.seed` replays one), so a lone rerun of the class tells nothing, and a failure that comes back at full size depends on order or suite size. Never raise `maxRetries`.
- Gradle does not capture application stdout in this project's test output — localize a failing integration test by assertion-based bisection, not log statements.
