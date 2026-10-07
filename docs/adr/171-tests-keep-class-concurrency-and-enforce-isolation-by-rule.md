# 171 tests-keep-class-concurrency-and-enforce-isolation-by-rule
epic: none · pr: none @tests @concurrency @deletion @naming @rejections @logs @mocks @memory @isolation @transactions @contexts @fakes

## Decisions
- @tests @concurrency — Backend test classes keep running concurrently inside the test JVM, twelve
  at a time in random order, and each hazard that concurrency exposes is closed by a rule that names
  it rather than by serializing the suite. Concurrent classes share one database, one Redis and one
  application context per configuration, so a class that reaches past its own rows and keys breaks a
  neighbour.
  REJECTED: turning class concurrency off — measured over three runs each, the suite took
  58 s longer on average (+25%), with the wall-clock ranges not overlapping, and peak memory did not
  move outside the run-to-run noise.
  INVARIANT: a test class shares the database only for rows it created: it truncates nothing, deletes
  no table without a WHERE, scans no shared Redis keyspace, and takes no hard-coded identity in a
  shared namespace — the test-isolation convention scan.
  NON-GOAL: lowering memory by running fewer threads, shrinking the context cache or lowering the
  heap below the invariant's margin — memory falls by sharing containers and imports instead.
- @tests @concurrency @memory — The backend suite runs in one test JVM with a 2 GiB heap, and the
  parallelism lives in JUnit's class threads instead of Gradle forks. A Spring context and the static
  Testcontainers a class starts live in one JVM only, so every fork rebuilt the contexts and the
  containers of the classes it happened to run and paid about 350 MiB of class metadata, code and
  GC structures of its own. Measured on one tree: six forks of three threads peaked at 9405 MiB
  (java and Docker,
  20 MySQL containers, 234 s); one JVM of twelve threads, with the whole-table classes on one
  exclusive database, merged context keys (29 contexts to 20) and class-lived harness containers,
  peaked at 6388–6473 MiB over three runs in 199–219 s.
  REJECTED: per-fork tuning (`-XX:ActiveProcessorCount=2`, SerialGC, a context cache of 8, lazy bean
  initialization) — each moved the peak by less than run-to-run noise or raised it, and lazy
  initialization broke the metrics registration test.
  REJECTED: fewer forks at three threads each — the fixed per-JVM cost falls, but the wall clock rises
  with nothing to fill the cores.
  INVARIANT: the heap is at least 1.25 times the largest heap that survives a collecting pause
  (989 MiB, against 2 GiB) — unenforced; re-measure with the GC log when the context count or the
  thread count changes. Neither the peak before a GC nor a Cleanup or Remark pause is the measure:
  G1 grows the young generation into whatever the limit allows, and those pauses free nothing.
- @tests @concurrency @contexts — The test classpath shadows spring-test's
  `DefaultCacheAwareContextLoaderDelegate` with a copy whose only change is per-configuration
  locking: a cached context is returned without a lock, and a load blocks only callers of the same
  configuration. spring-test 6.2.19 holds one monitor on the whole context cache for every lookup
  and load, so while one class booted a context every other class waited, even for a context
  already cached. Measured on one tree: 207 s before, 163–177 s over three runs with the copy,
  0 failures, peak java and Docker 5767–5912 MiB against 6861 MiB.
  REJECTED: a bootstrapper through `@BootstrapWith` — the delegate's state is private, so its load
  path must be rewritten anyway, every Spring test class needs the annotation, and a class that
  misses it mixes both lock disciplines on one cache.
  INVARIANT: the copy matches the spring-test version on the classpath — a test fails when that
  version is not 6.2.19, so an upgrade re-derives the copy from its source. The copy goes when
  Spring Framework ships per-configuration loading (spring-framework PR 34323).
- @tests @concurrency @transactions — No test holds a Spring test-managed transaction: no test class
  or method carries `@Transactional`, so cleanup is ownership of rows, and a test that needs a
  transaction boundary (flush-time constraints, lazy navigation, a rollback under test) opens one
  with `TransactionTemplate`. spring-test keeps the test transaction in an inheritable thread local
  and refuses to start a test while one is present. When a test holding it blocks in a
  `ForkJoinPool.managedBlock`-aware wait (Lettuce's synchronous API waits on a `CompletableFuture`),
  the JUnit pool creates a compensation worker from that thread, the worker inherits the context, and
  the first test it runs fails. A harness of 24 classes that saturated the pool failed 5 tests with
  that `IllegalStateException` in two of three runs, on compensation workers only, and passed once
  with the annotation removed; the inheritance itself is in the bytecode (spring-test's holder is a
  `NamedInheritableThreadLocal`, and JDK 21's `ForkJoinWorkerThread` inherits).
  `TransactionTemplate` binds plain thread locals, which do not inherit.
  REJECTED: JUnit's non-ForkJoin executor — experimental and only in JUnit 6.1, a major upgrade past
  what Spring Boot 3.5 manages, and it would avoid the leak only by not creating such threads.
  REJECTED: an extension that clears the context before each test — it hides a leaked transaction
  instead of removing it.
  INVARIANT: no test class or method carries Spring's or Jakarta's `@Transactional` — the test-isolation
  convention scan (`TESTS_HOLD_NO_SPRING_TRANSACTION`).
- @tests @concurrency @isolation — A test class whose subject is a whole table, or that changes a
  shared object such as a stored procedure, extends `ExclusiveDatabaseSupport`: one database and one
  Redis for all such classes, which run one at a time under `@ResourceLock("exclusive-db")` and share
  one context unless a member overrides a bean. A member owns nothing between classes, so it empties
  what it asserts over at its start (`emptyPlannerTables` for the planner tables) or narrows to its
  own rows; it never relies on a fresh database.
  REJECTED: a database per class — each one minted its own context and Redis container (13 of each),
  and a class that drops a shared procedure on the shared database broke concurrent neighbours.
  INVARIANT: a class that truncates or deletes without a WHERE extends `ExclusiveDatabaseSupport`, and
  the classes that declare their own property source stay within a budget — the test-isolation
  convention scan.
- @tests @deletion — A test is deleted only when one of five criteria holds, with its evidence
  recorded: it kills no mutant in a full mutation-matrix run (unit tests only) and no behavior it
  checks is left unchecked by other tests; it duplicates another test's arrange, act and assert; it
  asserts only what its own stubs return; it covers a case the types or validation can no longer
  produce; or a test at another tier covers the same scenario with stronger assertions.
  REJECTED: deleting by reviewer judgment — coverage then shrinks by taste, and a deletion cannot be
  checked afterwards.
  INVARIANT: a regression test for a real bug and a test that pins a published contract (a response
  shape, an error code the frontend matches on) are never deleted — unenforced.
  NON-GOAL: line coverage as a deletion criterion — it shows that a line ran, not that a test
  protects it.
- @tests @naming — No test class or test method carries `@DisplayName`; the method name is the only
  name a test has, and an identifier that resolves to a record (an invariant, RFC slug or migration
  id) sits in a one-line comment above the method. Three of every five methods carried a display name
  that restated the method name, and several had drifted to say the opposite of the assertions.
  REJECTED: display names that restate the method name — two names drift.
  INVARIANT: no test class or test method carries `@DisplayName` — the display-name convention rule.
- @tests @rejections — A rejection test asserts which rejection: a domain rejection through
  `Rejections.assertRejected` with the wire code or the validation violation (and its path where the
  validator reports one), any other exception through `assertThatThrownBy` with a message or property
  that tells it apart from another failure of the same type. Type-only assertions hid tests whose
  input was refused for a reason other than the one their name claimed.
  REJECTED: asserting the exception type alone — any failure of that type passes.
  INVARIANT: no thrown-exception assertion in the `org.danteplanner.backend` test packages stops at
  the exception type (a bare `assertThrows`, `assertThrowsExactly` or `assertRejected`, or an
  `assertThatThrownBy` chain of type checks only) — the `no-bare-assert-throws` ast-grep rule,
  scanned by `astGrepTestSources` under `check`.
- @tests @logs — Log capture is scoped to the calling thread; no test attaches a global appender.
  A test opens `LogCapture` for the loggers it asserts on and closes it within the test's
  lifecycle, and sees only the lines its own thread logged. The test JVM runs with Spring Boot's
  logging system off (`org.springframework.boot.logging.LoggingSystem=none`) and is configured once by
  `logback-test.xml`, because Spring Boot reinitialises logging on each context start and close,
  which detaches every appender attached at runtime — a concurrent class starting a context
  emptied a capture mid-test.
  REJECTED: `@Isolated` per class — serializes the test JVM for every log assertion.
  INVARIANT: only `support.LogCapture` attaches a logback appender — the log-capture rule
  (`LogCaptureRule`, checked by `TestLogCaptureConventionTest`).
- @tests @mocks — No test replaces a Spring bean with a Mockito mock or spy. An integration test
  observes an effect where it leaves the process: an SSE push on its Redis topic or on a real HTTP
  event stream, an OAuth login through a stub of Google's HTTP endpoints. A fault is injected by a
  hand-written `@TestBean` subclass that fails one method and runs the rest for real. Each
  distinct override set mints its own application context, and a mocked service replaces the code
  under test: two of the eleven overrides were never used, and the mocked `AuthenticationService`
  let the login test assert cookie values the real service never issues.
  REJECTED: a recording `SseChannelSender` bean — it skips the Redis hop and splits the context of
  every class that imports it.
  REJECTED: a scripted `OAuthProvider` bean — it skips the provider's HTTP and id_token handling,
  and the provider registry keys providers by name, so it must replace the real bean per class.
  INVARIANT: no `@MockitoBean`, `@MockitoSpyBean` or Boot's `@MockBean`/`@SpyBean`, directly or
  through a meta-annotation, in the test tree — the mock-bean rule (`MockBeanRule`, checked by
  `TestMockBeanConventionTest`).
- @tests @mocks @fakes — A unit test mocks only infrastructure ports: Spring Data repositories, Redis,
  JDBC, the clock, the Servlet API, outbound HTTP and types outside the codebase. A collaborator
  inside the codebase is a real object, or a fake in `support` whose behaviour a contract test pins:
  the same scenarios run against the fake and, in an integration test, against the real
  implementation. Mocks are fields under `MockitoExtension`, never `mock(...)` calls, and an argument
  a test never reaches is `null`. A test whose subject leans on a heavy collaborator moves to the
  integration tier, unless it kills mutants in the mutation-testing target set: mutation testing
  runs unit tests only, so such a test stays in the unit tier with the heavy collaborator real and
  its ports mocked. A mocked collaborator restated its behaviour in every test that used it (the user
  lookup was mocked at 22 sites), a validator stub invented an error code the real validator never
  raises, and unused strict stubs were the only thing killing some mutants.
  REJECTED: mocking interfaces as contracts — the interface fixes signatures, not behaviour, so each
  test still invents the behaviour it stubs.
  REJECTED: in-memory fakes or an embedded database for the repositories — the queries lean on
  MySQL (native SQL, collation, gap locks, FULLTEXT), so either would re-implement or diverge from
  what the repository integration tests already prove.
  INVARIANT: no `@Mock`/`@Spy` of a type in `org.danteplanner` other than a Spring Data repository,
  mocks only through `MockitoExtension`, and no other Mockito factory (`mock`, `spy`, `mockStatic`,
  `mockConstruction`, through `Mockito` or `BDDMockito`) in test sources, except a static mock of a
  named third-party API with no injectable seam (Sentry) — the mock-port rule (`MockPortRule`) and
  the ast-grep rule `no-mock-call`.

## Takeaway
- takeaway: when concurrency in a test suite exposes shared state, close each hazard with a rule
  that names it; serializing hides the hazards and costs wall clock without saving memory.
