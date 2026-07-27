# Testing principles

What a test should assert, so that it survives a refactor of the code it covers.

`backend/src/test/CLAUDE.md` governs mechanics: which tier, how to wire Testcontainers, how to
name a method. This document governs the assertion, which is what decides whether a test breaks
every time the code beneath it moves.

---

## 1. The two questions

Before writing an assertion, ask both:

1. **Could this fail while the system is still correct?**
2. **Could this pass while the system is broken?**

A well-formed test answers no to both. Anything that answers yes to either is testing structure.

Worked example. This answers yes to both:

```java
verify(contentValidator).validate(request.content(), request.category());
```

It passes if `validate` does nothing. It fails the moment validation moves behind a different
seam, while the system stays correct. Its subject is the wiring diagram, not the behavior.

This answers no to both:

```java
when(contentValidator.validate(any(), any())).thenThrow(ValidationErrors.emptyContent());

assertThrows(PlannerValidationException.class,
        () -> commandService.createPlanner(userId, deviceId, request));
```

The guarantee is "invalid content does not get in". Where the check lives is free to change.

---

## 2. Assert high on the stability ladder

| Level | Changes when | Assert here |
|---|---|---|
| Observable outcome — status, response body, persisted row, emitted event | behavior changes | **yes** |
| Domain invariant — `comment_count >= 0`, publish is idempotent | the domain changes | **yes** |
| Public API signature | interfaces change | sometimes |
| Internal collaboration — who calls whom, with what arguments | every refactor | **no** |
| Implementation detail — private method, SQL text, field name | constantly | no |

A test that asserts a collaboration costs maintenance on every refactor and pays nothing, because
the collaboration is not what any client depends on.

---

## 3. Where a property's truth lives decides what may be mocked

Generality and fidelity are independent. A property does not imply a pure environment, and a real
dependency does not imply an example.

| | test double | real dependency |
|---|---|---|
| **example** | `verify(repo).save(x)` | one request, assert the status |
| **property** | `normalize(normalize(x)) == normalize(x)` | two threads converge on one successor |

A property claims that something holds for all inputs. Whether it holds depends on the semantics of
everything the code touches, so replacing a dependency with a double replaces its semantics with a
*model* of them. What the test then proves is "the property holds given my model", which is worth
exactly what the model is worth.

**Mock a dependency only when the property under test does not depend on that dependency's
semantics.** This is the reason behind §4's shorter form: what you do not own is what you cannot
model faithfully.

Ask where the truth comes from.

- **From this codebase's own logic** — keyword normalization is idempotent, a category maps to a
  fixed floor count, a counter never goes negative given correct arithmetic. No environment needed,
  and generated inputs pay off most here.
- **From a dependency's guarantee** — atomicity, isolation, ordering, durability, replication
  visibility. The guarantee is the thing being relied on, so asserting it against a model of itself
  is circular. "Two concurrent rotations converge on one successor" is true because Redis runs the
  script atomically; a double has no atomicity, so the property passes and the race ships.

The two compose rather than competing. Generated inputs supply the cases nobody thinks of; a real
dependency supplies the semantics nobody can fake. The strongest available test of a lifecycle is
both at once — generated command sequences run against a real datastore, invariants asserted after
every step.

---

## 4. Mock only what you do not own

See §3 for when a double is admissible at all. Beyond that, mock the things whose behavior is
someone else's contract: Redis, the OAuth HTTP client, the clock. Never mock a service in this codebase — it is a refactoring target, and a mock of it
freezes today's decomposition into the test.

`readpath/PrimaryReCheckTest` holds one mock, and that one stands in for Redis.

When a unit test needs five mocks to reach its assertion, the assertion is usually at the wrong
level. Drive the same behavior through MockMvc against a real datastore instead.

---

## 5. `verify(never())` and `verify()` are not the same tool

`verify(never())` asserts the **absence** of a side effect, which frequently has no state-based
alternative — nothing was persisted, no event was published, the far region was never called. It
stays valid across refactors as long as the prohibition holds.

`verify(...)` asserts a collaboration **happened**, and almost always has a state-based
replacement: assert the row, the response, or the index that the collaboration produces.

Prefer the outcome. Reach for `verify(never())` when absence is the whole point.

**Under strict stubs, a stub is already two assertions.** `MockitoExtension` defaults to
`STRICT_STUBS`: a stub that is never called raises `UnnecessaryStubbingException`, and a call with
different arguments raises `PotentialStubbingProblem`. So `verify(mock).m(exactArgs)` after
`when(mock.m(exactArgs))` restates what the framework already enforces. Tighten the stub's matchers
from `any()` to literal values and the `verify` becomes redundant.

**A swallowed exception un-asserts it.** That property only holds while the stubbing exception can
escape. `JwtAuthenticationFilter.attemptAutoRefresh` ends in `catch (Exception e) { return false; }`,
which eats `PotentialStubbingProblem` — so on any path whose outcome is "no auth", strict stubs pin
only *that* a collaborator was called, never with what. Removing a `verify` behind a broad catch is
a real weakening. Check for an enclosing catch before treating a stub as an assertion.

---

## 6. The tier follows the behavior, not the preference

Most of this system's interesting behavior runs after commit: publish notifications, SSE fan-out,
the filter-index rebuild, the tombstone write, GTID capture. A `@Transactional` test rolls back,
so `@TransactionalEventListener(AFTER_COMMIT)` never fires and none of it is observable.

That is a hard constraint, not a style choice. The usual advice to keep the containerized tier
small does not apply where correctness lives in post-commit effects and cross-region routing.
Follow the behavior.

---

## 7. Name the test subject, condition, expectation

Every test method is spelled `subject_WhenCondition_Expectation`, enforced by
`architecture/TestNamingConventionTest`: a camelCase subject, a middle part opening with `When`, and
one or more PascalCase expectation parts.

| Write | Not |
|---|---|
| `findById_WhenExists_ReturnsUser` | `findById_exists_returnsUser` |
| `upsertPlanner_WhenOwnerBanned_KeepsPrivateWork` | `upsertPlanner_bannedUser_throws` |
| `deletedEntity_WhenReplicaHits_IsMasked` | `deleted_entity_is_masked_on_replica_hit` |

One spelling, no exceptions. `After`, `With` and `Given` each read well in isolation, and admitting
them is how half the suite drifted into shapes that were neither one thing nor the other; a reader
scanning a file should not have to hold four forms in mind. Casing is not free either — the segments
after the subject are PascalCase, so the parts are visible at a glance.

A name whose middle part reads as an outcome — `cannotCreateComment`, `NoDeadlock` — is misnamed
rather than exempt. The condition belongs in the middle and the outcome at the end, and rewriting it
that way usually exposes that the two were swapped. One such name in this suite claimed
`returns403` while asserting the opposite.

The cost is real and worth naming: a name beginning with its subject dies with the method it names,
and cannot be cited from a javadoc `@see` once that method is renamed. The invariant-phrase form
(`deleted_planner_is_masked_on_replica_hit`) survives both. That form was permitted here for exactly
that reason and, measured across 1197 test names, **no test was ever cited from a javadoc**. A
capability nothing used cost the suite its consistency, so the rule now buys consistency instead. If
a specific invariant genuinely needs a citable name, make it a deliberate exception with a comment
saying why, not a standing licence.

---

## 8. Derive the axis, never list it

Where a rule holds across a set — every endpoint, every role, every enum constant — read the set
from the code rather than typing it out:

- endpoints from `RequestMappingHandlerMapping`
- roles from `UserRole.values()`
- action types from `ModerationAction.ActionType.values()`
- event types from `SseEventType.values()`

A derived axis grows on its own, so a new member is covered without anyone remembering. A typed
list covers what its author thought of, and the gaps are exactly where defects sit.
`security/RestrictedPrincipalMatrixIT` and `moderation/ModerationAuditMatrixTest` both work this
way; the second found two enum constants that no code wrote.

Two rules for a derived matrix:

- **Assert both arms.** Over-application is as much a defect as under-application, and the
  un-applied arm is where a newly added member lands by default.
- **Keep the rows independent.** Dynamic tests share one `@BeforeEach`, so a row that mutates
  shared state hands every later row a false pass, and the matrix degenerates into a list.

---

## 9. Reach for properties where the domain has laws

Some guarantees hold for all inputs, not one. §3 governs whether a given one can be asserted
without a real dependency:

- `setPublished(true)` applied twice equals applied once
- `syncVersion` never decreases
- `comment_count` never goes negative after any command sequence
- keyword normalization is idempotent

Property tests survive every implementation, and they generate the sequences nobody thinks to
write. Stateful property testing over the planner lifecycle is where interaction defects surface.

---

## 10. When a test should be deleted

**Delete a test when the defect it pins can no longer be written. Never when the code merely
moved.**

A test guarding against locale-sensitive string matching earns deletion the day the code stops
matching strings, because the mistake becomes unrepresentable. Record that in the commit message —
it is a win, not a loss of coverage.

A test that breaks because a check moved between classes has not lost its subject. It was asserting
the wrong thing, and the fix is to re-aim it, not to delete it.

---

## 11. Repeated shape is not duplication

Before extracting a repeated block, check whether the **values** repeat, not just the layout.

A teardown sweep repeated verbatim across dozens of files is a rule stated dozens of times and
owned nowhere; extract it. Dozens of `User.builder()` blocks that share a shape but carry different
emails, ids, and roles are explicit local fixtures; collapsing them trades readable, visible
arrangement for invisible shared defaults, and a mechanical rewrite can silently change what a test
exercises.

The test: if the extracted helper would need a parameter for nearly every field, there was nothing
to extract.

---

## 12. What makes an assertion vacuous

An assertion is vacuous when it cannot fail against any implementation. These pass forever, read as
coverage, and are worse than no test because they occupy the slot a real one would take. Four shapes
recur, each found in this suite:

- **Prohibiting something nothing does.** `verify(tokenValidator, never()).validateToken(any())`
  passed unconditionally because no filter path calls that method. Re-aim a prohibition at the
  method actually on the path.
- **Asserting a value that already held.** A timeout-clearing test asserted `assertNull` on a field
  that was null before the call, so it passed whether or not the service cleared anything. Arrange
  the opposite state first.
- **Never arranging the condition the name claims.** A fan-out test named `WhenSubscribersExist`
  stubbed no subscribers, so nothing distinguished it from the empty case.
- **Fixtures that cannot be told apart.** Stubbing four per-user flags to the same falsy value and
  asserting none of them passes with any two of them crossed. Give each a distinct value and assert
  all of them.

**Plant a decoy when a test picks a branch.** If the wrong branch reaches an unstubbed method, it
fails with a null-pointer somewhere downstream, which is indistinguishable from unrelated breakage
and tells you nothing about which branch ran. Stub the sibling branch with a distinct value instead,
so the correct branch is identified by what came back rather than by what crashed.

**A new assertion is unproven until you have watched it fail.** Vacuous assertions survive for
years because nobody breaks them on purpose. After writing one, change the expected value, confirm
the test fails, then change it back. This is the only check that distinguishes an assertion from a
restatement of the fixture, and it is cheap: two edits and one scoped run.

A related trap: if the arrangement stub sets the very field being asserted, the assertion tests the
fixture rather than the code. Three creation tests here asserted `assertNotNull(response.id())`
while their own `thenAnswer` stub assigned that id. Reduce the stub to identity and let the value
come from production.

**Mocking a holder hides the outcome you wanted.** A mocked `HttpServletResponse` cannot report a
status or a cookie, so every outcome has to be read back as `verify(response).setStatus(503)`. The
Spring mock-web objects are real implementations, not doubles: `MockHttpServletResponse` makes the
same facts directly assertable, and so does a real `CookieUtils`.

---

## 13. A test owns the rows it creates, and nothing else

Every integration class shares one database and they run concurrently. Three claims are therefore
false however natural they read:

- **"This table holds only my rows."** `deleteAll()` in `@BeforeEach` asserts it and enforces it by
  deleting everyone else's. It is a global assertion disguised as cleanup, true only when the class
  runs alone.
- **"Index 0 is mine."** `$.content[0].title`, `actions.get(0)`, `$[0].usernameSuffix` — position in
  a global list belongs to whoever sorted first.
- **"The count is mine."** `repository.count()`, `hasSize(3)`, `$.totalElements`. A before/after
  delta is the same claim twice with a race between the reads.

Scope to an identity the test owns. A parameterized query already does this — `findByPlannerId(mine)`
is correct where `findAll()` is not, and a list endpoint narrowed by an auth cookie is scoped by the
caller. An audit that flags every `count()` and `hasSize()` will be wrong about most of them.

Prefer a negative: "my row is absent" survives any amount of neighbouring data, where "exactly N rows"
cannot. Converting `hasSize(1)` to `hasItem(mine)` plus `not(hasItem(theirs))` usually *strengthens*
the test, because the original passes when the single row present belongs to someone else.

**Acting globally counts too.** A sweep that hard-deletes by criteria, or a stored procedure that
rebuilds an index, touches every class's rows. `PlannerUserDeleteSweepIT` deleted other classes'
users and surfaced as foreign-key violations in three unrelated classes.

A shared context is a shared object graph, not only a shared connection. A test whose subject is
in-memory state in a singleton — a write buffer, a cache, a scheduler, a metric registry — owns
nothing it can narrow an assertion to, because the state belongs to the bean rather than to a row.
Three classes asserting on one view recorder's buffer failed intermittently for a day before that
was named. The remedy is the same escape hatch: a private database is really a private context, and
a private context is a private bean.

The rule is about stores, not about SQL. `flushAll()` and `flushDb()` are `deleteAll()` for Redis,
and a literal foreign key is the same claim in miniature: `.actorId(1L)` asserts that user 1 exists
and is yours, which holds only on a database nobody else writes to and no engine enforces. A class
that wipes its Redis keeps its own container for the same reason a class that truncates keeps its
own database.

When the subject genuinely is a whole table or index — title search, keyword facets, a global
rebuild — take `registerOwnDatabase`. It costs one application context, so reach for a narrowed
assertion first.

---

## 14. `@Transactional` is not a cleanup mechanism

Use it only where the transaction boundary is the subject: a constraint that surfaces at flush, a
lazy graph that needs an open session. Never for rollback-as-cleanup — unique fixtures give that for
free, and the transaction costs three things that are all silent.

- **`@TransactionalEventListener(AFTER_COMMIT)` never fires.** A rolled-back controller test drives
  a path production never takes. Removing the annotation surfaces real behaviour: listeners bump a
  row's version, so an entity captured in `@BeforeEach` is stale by the time the test mutates it,
  and saving it raises `ObjectOptimisticLockingFailureException`. Re-read before mutating.
- **InnoDB flushes its FULLTEXT cache at commit.** `MATCH ... AGAINST` cannot see the test's own
  uncommitted rows, so a search returns nothing and looks like a broken query.
- **It binds a transaction to a `ThreadLocal`.** JUnit's executor blocks in `ForkJoinTask.join()`,
  and a blocked ForkJoin thread runs another task while waiting; that task inherits the binding.

A test that stays inside the first-level cache asserts object identity while appearing to assert
persisted equality. One here compared an `Instant` for exact equality and only passed because both
reads returned the same instance; once the value round-tripped, MySQL's `DATETIME(6)` dropped the
nanoseconds a Java `Instant` carries.

---

## 15. What the parallelism annotations cannot express

- **`@Execution(CONCURRENT)` propagates to a node's descendants.** On a class it also makes the
  methods concurrent, and methods share that class's `@BeforeEach` fixtures. "Classes parallel,
  methods sequential" exists only in `junit-platform.properties`, which forces isolation to be
  opt-out rather than opt-in.
- **`@ResourceLock` excludes only other lock holders.** A locked class truncating a shared table
  still clobbers every class that did not declare the lock. It is also an in-JVM lock and cannot
  reach across Gradle forks.
- **Sibling `@BeforeEach` methods have no defined order.** Cleanup must be the first statements of
  the one setup method, not a second method beside it. Spring collects `@DynamicPropertySource`
  from a class hierarchy with the same absence of ordering: a subclass binding its own database
  while inheriting one that binds the shared database is a coin flip.

> Contention is a naming problem before it is a concurrency problem. Names can be enumerated
> statically; interleavings cannot.

Three remedies, in order of preference: **rename** (make the name unique), **privatize** (give the
test its own instance), **serialize** (order access). Serialization is the fallback, and it only
coordinates participants — a developer's own running Redis never agreed to take your lock.

---

## 16. If you change one of these, check the others

- Adding a constructor dependency to a service breaks every test that builds it positionally.
  That cost is the measure of how mock-heavy the suite is around that service, and it is worth
  reading as a signal rather than absorbing silently.
- Adding an endpoint, a role, or an enum constant should require no edit to a derived matrix. If
  it does, the axis was typed out somewhere.
- Promoting a compiler or lint check to error requires every existing site to be clean first. A
  rule that cannot be turned on today is not a rule.
- Mutation coverage measures whether a line is protected; line coverage measures only whether it
  ran. When `targetTests` is unset, PIT derives it from `targetClasses` and silently ignores tests
  whose package differs from the class under test.
- Removing `@Transactional` from a test makes its `AFTER_COMMIT` effects real. Expect stale-entity
  failures where the test held a reference across a mutating call, and cleanup ordering failures
  where a delete now actually commits.
- `deleteAll()` cascades to child entities; `deleteAllInBatch()` issues one `DELETE FROM` and does
  not. Swapping them to avoid a version conflict leaves orphaned satellite rows.
- An in-memory database named explicitly in `spring.datasource.url` is shared by every context on
  that profile. Paired with `ddl-auto=create-drop`, a closing context drops the schema under the
  ones still running. Boot's `generate-unique-name` default exists to prevent exactly this.
