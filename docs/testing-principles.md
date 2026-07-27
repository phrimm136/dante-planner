# Testing principles

What a test should assert, so that it survives a refactor of the code it covers.

`backend/src/test/CLAUDE.md` governs mechanics: which tier, how to wire Testcontainers, how to name
a method. This document governs the assertion.

---

## 1. The two questions

1. **Could this fail while the system is still correct?**
2. **Could this pass while the system is broken?**

A well-formed test answers no to both. Yes to either means it is testing structure.

```java
verify(contentValidator).validate(request.content(), request.category());   // yes to both
```

It passes if `validate` does nothing, and fails the moment validation moves behind another seam.
Its subject is the wiring diagram.

```java
when(contentValidator.validate(any(), any())).thenThrow(ValidationErrors.emptyContent());
assertThrows(PlannerValidationException.class, () -> commandService.createPlanner(...));
```

The guarantee is "invalid content does not get in". Where the check lives is free to change.

---

## 2. Assert high on the stability ladder

| Level | Changes when | Assert here |
|---|---|---|
| Observable outcome — status, body, persisted row, emitted event | behavior changes | **yes** |
| Domain invariant — `comment_count >= 0`, publish is idempotent | the domain changes | **yes** |
| Public API signature | interfaces change | sometimes |
| Internal collaboration — who calls whom, with what arguments | every refactor | **no** |
| Implementation detail — private method, SQL text, field name | constantly | no |

A collaboration assertion costs maintenance on every refactor and pays nothing, because no client
depends on it.

---

## 3. What may be mocked

Generality and fidelity are independent:

| | test double | real dependency |
|---|---|---|
| **example** | `verify(repo).save(x)` | one request, assert the status |
| **property** | `normalize(normalize(x)) == normalize(x)` | two threads converge on one successor |

Replacing a dependency with a double replaces its semantics with a *model* of them, so the test
proves "the property holds given my model" — worth exactly what the model is worth.

**Mock a dependency only when the property under test does not depend on its semantics.** Ask where
the truth comes from:

- **This codebase's logic** — normalization is idempotent, a counter never goes negative. No
  environment needed, and generated inputs pay off most here.
- **A dependency's guarantee** — atomicity, isolation, ordering, durability, replication
  visibility. Asserting it against a model of itself is circular: "two concurrent rotations converge
  on one successor" holds because Redis runs the script atomically, and a double has no atomicity,
  so the property passes and the race ships.

Beyond that, mock what is someone else's contract — Redis, the OAuth HTTP client, the clock. **Never
mock a service in this codebase**: it is a refactoring target, and a mock freezes today's
decomposition into the test. `readpath/PrimaryReCheckTest` holds one mock, standing in for Redis.

Five mocks to reach an assertion means the assertion is at the wrong level. Drive the behavior
through MockMvc against a real datastore instead.

---

## 4. `verify(never())` and `verify()` are not the same tool

`verify(never())` asserts the **absence** of a side effect, which often has no state-based
alternative — nothing persisted, no event published, the far region never called. `verify(...)`
asserts a collaboration **happened**, and almost always has one: assert the row, the response, the
index it produces. Prefer the outcome; reach for `never()` when absence is the point.

**Under strict stubs, a stub is already two assertions.** `MockitoExtension` defaults to
`STRICT_STUBS`: an uncalled stub raises `UnnecessaryStubbingException`, a mismatched call raises
`PotentialStubbingProblem`. So `verify(mock).m(exactArgs)` after `when(mock.m(exactArgs))` restates
what the framework enforces — tighten the matchers from `any()` to literals and drop the `verify`.

**A swallowed exception un-asserts it.** `JwtAuthenticationFilter.attemptAutoRefresh` ends in
`catch (Exception e) { return false; }`, which eats `PotentialStubbingProblem`. On any path whose
outcome is "no auth", strict stubs pin only *that* a collaborator was called, never with what. Check
for an enclosing catch before treating a stub as an assertion.

---

## 5. The tier follows the behavior

Most interesting behavior here runs after commit: publish notifications, SSE fan-out, the
filter-index rebuild, the tombstone write, GTID capture. A `@Transactional` test rolls back, so
`@TransactionalEventListener(AFTER_COMMIT)` never fires and none of it is observable.

That is a constraint, not a preference. The usual advice to keep the containerized tier small does
not apply where correctness lives in post-commit effects.

---

## 6. Name the test subject, condition, expectation

`subject_WhenCondition_Expectation`, enforced by `architecture/TestNamingConventionTest`: camelCase
subject, a middle part opening with `When`, one or more PascalCase expectation parts.

| Write | Not |
|---|---|
| `findById_WhenExists_ReturnsUser` | `findById_exists_returnsUser` |
| `upsertPlanner_WhenOwnerBanned_KeepsPrivateWork` | `upsertPlanner_bannedUser_throws` |
| `deletedEntity_WhenReplicaHits_IsMasked` | `deleted_entity_is_masked_on_replica_hit` |

One spelling, no exceptions. A name whose middle part reads as an outcome — `cannotCreateComment`,
`NoDeadlock` — is misnamed rather than exempt; rewriting it usually exposes that condition and
outcome were swapped. One such name claimed `returns403` while asserting the opposite.

The cost is real: a subject-first name dies with the method it names and cannot be cited from a
javadoc `@see`. The invariant-phrase form survives both, was permitted here for exactly that reason,
and across 1197 names **no test was ever cited from a javadoc**. A capability nothing used cost the
suite its consistency.

---

## 7. Derive the axis, never list it

Where a rule holds across a set — every endpoint, role, enum constant — read the set from the code:
`RequestMappingHandlerMapping`, `UserRole.values()`, `ModerationAction.ActionType.values()`,
`SseEventType.values()`.

A derived axis grows on its own; a typed list covers what its author thought of, and the gaps are
where defects sit. `security/RestrictedPrincipalMatrixIT` and `moderation/ModerationAuditMatrixTest`
work this way, and the second found two enum constants no code wrote.

- **Assert both arms.** Over-application is as much a defect as under-application, and the
  un-applied arm is where a new member lands by default.
- **Keep the rows independent.** Dynamic tests share one `@BeforeEach`, so a row that mutates shared
  state hands every later row a false pass.

---

## 8. Reach for properties where the domain has laws

Some guarantees hold for all inputs. §3 governs whether one can be asserted without a real
dependency.

- `setPublished(true)` applied twice equals applied once
- `syncVersion` never decreases
- `comment_count` never goes negative after any command sequence
- keyword normalization is idempotent

Property tests survive every implementation and generate the sequences nobody writes. Stateful
property testing over the planner lifecycle is where interaction defects surface.

---

## 9. When a test should be deleted

**Delete a test when the defect it pins can no longer be written. Never when the code merely moved.**

A test guarding against locale-sensitive string matching earns deletion the day the code stops
matching strings, because the mistake becomes unrepresentable. Record that in the commit message.

A test that breaks because a check moved between classes has not lost its subject. Re-aim it.

---

## 10. Repeated shape is not duplication

Check whether the **values** repeat, not just the layout. A teardown sweep repeated verbatim across
dozens of files is one rule stated dozens of times and owned nowhere; extract it. Dozens of
`User.builder()` blocks carrying different emails, ids, and roles are explicit local fixtures;
collapsing them trades visible arrangement for invisible shared defaults.

The test: if the helper would need a parameter for nearly every field, there was nothing to extract.

---

## 11. What cannot fail is not a check

An assertion is vacuous when it cannot fail against any implementation. It passes forever, reads as
coverage, and occupies the slot a real test would take. Four shapes recur, each found here:

- **Prohibiting something nothing does.** `verify(tokenValidator, never()).validateToken(any())`
  passed because no filter path calls it. Re-aim at the method actually on the path.
- **Asserting a value that already held.** A timeout-clearing test asserted `assertNull` on a field
  already null. Arrange the opposite state first.
- **Never arranging the condition the name claims.** A test named `WhenSubscribersExist` stubbed no
  subscribers.
- **Fixtures that cannot be told apart.** Four flags stubbed to the same falsy value pass with any
  two crossed. Give each a distinct value.

Two related traps. If the arrangement stub sets the very field being asserted, the assertion tests
the fixture — three creation tests asserted `assertNotNull(response.id())` while their own
`thenAnswer` assigned that id. And a mocked holder hides the outcome: a mocked `HttpServletResponse`
forces `verify(response).setStatus(503)`, where `MockHttpServletResponse` is a real implementation
that makes the fact directly assertable.

**Plant a decoy when a test picks a branch.** If the wrong branch reaches an unstubbed method it
fails with a null-pointer downstream, indistinguishable from unrelated breakage. Stub the sibling
branch with a distinct value so the correct branch is identified by what came back.

**Watch it fail before you trust it.** Change the expected value, confirm the failure, change it
back. Two edits and one scoped run, and it is the only check that distinguishes an assertion from a
restatement of the fixture.

**The same applies to enforcement rules, more sharply.** An ArchUnit or lint rule that matches
nothing passes on every commit and reads as a guarantee that no one re-reads. After writing or
narrowing one, introduce a real violation and watch it bite:

- **Violate it the way a person would.** A synthetic probe the rule was shaped around proves only
  that it matches itself.
- **Put the violation where a real one would land.** A truncation rule aimed at the shared database
  proves nothing against a class that owns its own.
- **Read the failure message.** It is what the next person gets instead of an explanation; if it
  does not name the offender and the alternative, it is unfinished.
- **Re-run after narrowing.** Narrowing to kill a false positive is exactly when a rule loses its
  teeth. A `deleteAll` rule matching the constant pool by substring rejected `deleteAllByPlannerIds`,
  which names the rows it removes and is the correct way to clean up.

A rule whose first run is all false positives gets configured away. Enabling `vitest/expect-expect`
reported thirteen assertion-free tests, twelve of which called a helper that asserts internally — the
run after the `assertFunctionNames` fix is the one that tells you anything.

---

## 12. `@Transactional` is not a cleanup mechanism

Use it only where the transaction boundary is the subject: a constraint surfacing at flush, a lazy
graph needing an open session. Never as rollback-as-cleanup — unique fixtures give that free, and
the transaction costs three things, all silent.

- **`AFTER_COMMIT` listeners never fire.** A rolled-back controller test drives a path production
  never takes. Removing the annotation surfaces real behavior: listeners bump a row's version, so an
  entity captured in `@BeforeEach` is stale by the time the test mutates it. Re-read before mutating.
- **InnoDB flushes its FULLTEXT cache at commit.** `MATCH ... AGAINST` cannot see the test's own
  rows, so a search returns nothing and looks like a broken query.
- **It binds a transaction to a `ThreadLocal`.** JUnit's executor blocks in `ForkJoinTask.join()`,
  and a blocked ForkJoin thread runs another task that inherits the binding.

A test staying inside the first-level cache asserts object identity while appearing to assert
persisted equality. One compared an `Instant` for exact equality and passed only because both reads
returned the same instance; once it round-tripped, MySQL's `DATETIME(6)` dropped the nanoseconds.

---

## 13. A test owns the rows it creates, and nothing else

Every integration class shares one database and they run concurrently, so three natural-sounding
claims are false:

- **"This table holds only my rows."** `deleteAll()` in `@BeforeEach` enforces it by deleting
  everyone else's — a global assertion disguised as cleanup.
- **"Index 0 is mine."** `$.content[0].title`, `actions.get(0)` — position in a global list belongs
  to whoever sorted first.
- **"The count is mine."** `repository.count()`, `hasSize(3)`, `$.totalElements`. A before/after
  delta is the same claim twice with a race between the reads.

Scope to an identity the test owns; a parameterized query already does this. **Prefer a negative**:
"my row is absent" survives any neighbouring data where "exactly N rows" cannot, and converting
`hasSize(1)` to `hasItem(mine)` plus `not(hasItem(theirs))` usually *strengthens* the test, because
the original passes when the one row present belongs to someone else.

**Acting globally counts too.** `PlannerUserDeleteSweepIT` hard-deleted other classes' users and
surfaced as foreign-key violations in three unrelated classes.

**A shared context is a shared object graph.** A test whose subject is in-memory singleton state — a
write buffer, a cache, a scheduler, a metric registry — owns nothing it can narrow to. Three classes
asserting on one view recorder's buffer failed intermittently for a day. A private database is
really a private context, and a private context is a private bean.

The rule is about stores, not SQL: `flushAll()` is `deleteAll()` for Redis, and `.actorId(1L)`
asserts that user 1 exists and is yours, which no engine enforces.

When the subject genuinely is a whole table or index, take `registerOwnDatabase`. It costs one
application context, so narrow the assertion first.

**What the parallelism annotations cannot express:**

- **`@Execution(CONCURRENT)` propagates to descendants.** On a class it makes the methods concurrent
  too, and they share that class's `@BeforeEach` fixtures. "Classes parallel, methods sequential"
  exists only in `junit-platform.properties`.
- **`@ResourceLock` excludes only other lock holders**, and cannot reach across Gradle forks.
- **Sibling `@BeforeEach` methods have no defined order.** Cleanup belongs in the one setup method.
  Spring collects `@DynamicPropertySource` from a hierarchy with the same absence of ordering: a
  subclass binding its own database while inheriting one that binds the shared database is a coin
  flip.

> Contention is a naming problem before it is a concurrency problem. Names can be enumerated
> statically; interleavings cannot.

Three remedies in order: **rename**, **privatize**, **serialize**. Serialization coordinates only
participants — a developer's own running Redis never agreed to take your lock.

---

## 14. If you change one of these, check the others

- Adding a constructor dependency breaks every test that builds the service positionally. That cost
  measures how mock-heavy the suite is around it; read it as a signal.
- Adding an endpoint, role, or enum constant should require no edit to a derived matrix. If it does,
  the axis was typed out somewhere.
- Promoting a check to error requires every existing site to be clean first. A rule that cannot be
  turned on today is not a rule.
- Mutation coverage measures whether a line is protected; line coverage, only whether it ran. With
  `targetTests` unset, PIT derives it from `targetClasses` and silently ignores tests in another
  package.
- Removing `@Transactional` makes a test's `AFTER_COMMIT` effects real. Expect stale-entity failures
  where a reference was held across a mutating call.
- `deleteAll()` cascades to children; `deleteAllInBatch()` issues one `DELETE FROM` and does not.
  Swapping them to dodge a version conflict leaves orphaned satellite rows.
- An in-memory database named explicitly in `spring.datasource.url` is shared by every context on
  that profile. With `ddl-auto=create-drop`, a closing context drops the schema under the ones still
  running.
