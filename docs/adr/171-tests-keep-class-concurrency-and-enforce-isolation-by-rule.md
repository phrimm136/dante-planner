# 171 tests-keep-class-concurrency-and-enforce-isolation-by-rule
epic: none · pr: none @tests @concurrency @deletion @naming @rejections @logs @mocks @memory @isolation

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
  heap below the measured peak — memory falls by sharing containers and merging context keys instead.
- @tests @concurrency @memory — The backend suite runs in one test JVM with a 2560 MiB heap, and the
  parallelism lives in JUnit's class threads instead of Gradle forks. A Spring context and the static
  Testcontainers a class starts live in one JVM only, so every fork rebuilt the contexts and the
  containers of the classes it happened to run and paid about 350 MiB of class metadata, code and
  GC structures of its own. Measured on one tree: six forks of three threads peaked at 9405 MiB
  (java and Docker,
  20 MySQL containers, 234 s); one JVM of twelve threads, with the whole-table classes on one
  exclusive database, peaked at 7078–7576 MiB over three runs (7 MySQL containers) in 176–211 s.
  REJECTED: per-fork tuning (`-XX:ActiveProcessorCount=2`, SerialGC, a context cache of 8, lazy bean
  initialization) — each moved the peak by less than run-to-run noise or raised it, and lazy
  initialization broke the metrics registration test.
  REJECTED: fewer forks at three threads each — the fixed per-JVM cost falls, but the wall clock rises
  with nothing to fill the cores.
  INVARIANT: the heap is at least 1.25 times the measured peak (1785 MiB before GC) —
  unenforced; re-measure with the GC log when the context count or the thread count changes.
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

## Takeaway
- takeaway: when concurrency in a test suite exposes shared state, close each hazard with a rule
  that names it; serializing hides the hazards and costs wall clock without saving memory.
