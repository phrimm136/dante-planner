# Testing evidence

Why each rule in [testing-principles.md](testing-principles.md) exists. Section numbers match.

Most entries are an incident in this suite rather than an argument. A rule that cost a debugging
session is easier to keep than one that sounded sensible.

---

## 1. The two questions

```java
verify(contentValidator).validate(request.content(), request.category());   // yes to both
```

Passes if `validate` does nothing, and fails the moment validation moves behind another seam. Its
subject is the wiring diagram.

```java
when(contentValidator.validate(any(), any())).thenThrow(ValidationErrors.emptyContent());
assertThrows(PlannerValidationException.class, () -> commandService.createPlanner(...));
```

The guarantee is "invalid content does not get in". Where the check lives is free to change.

A collaboration assertion costs maintenance on every refactor and pays nothing, because no client
depends on it.

---

## 3. What may be mocked

Generality and fidelity are independent axes:

| | test double | real dependency |
|---|---|---|
| **example** | `verify(repo).save(x)` | one request, assert the status |
| **property** | `normalize(normalize(x)) == normalize(x)` | two threads converge on one successor |

A double replaces a dependency's semantics with a model of them, so the test proves "this holds
given my model" — worth what the model is worth.

"Two concurrent rotations converge on one successor" holds because Redis runs the script atomically.
A double has no atomicity, so the property passes and the race ships.

`readpath/PrimaryReCheckTest` holds one mock, and that one stands in for Redis.

---

## 4. Strict stubs, and what un-asserts them

`MockitoExtension` defaults to `STRICT_STUBS`: an uncalled stub raises `UnnecessaryStubbingException`
and a mismatched call raises `PotentialStubbingProblem`. That makes a tightly-matched stub carry the
assertion a following `verify` would restate.

`JwtAuthenticationFilter.attemptAutoRefresh` ends in `catch (Exception e) { return false; }`, which
eats `PotentialStubbingProblem`. On any path whose outcome is "no auth", strict stubs pin only
*that* a collaborator was called, never with what — so removing a `verify` there is a real
weakening.

---

## 5. Which behavior lives after commit

Publish notifications, SSE fan-out, the filter-index rebuild, the tombstone write, GTID capture.
None of it is observable from a test that rolls back.

---

## 6. The naming convention's cost, measured

A subject-first name dies with the method it names and cannot be cited from a javadoc `@see`. The
invariant-phrase form (`deleted_planner_is_masked_on_replica_hit`) survives both, and was permitted
here for exactly that reason.

Measured across 1197 test names: **no test was ever cited from a javadoc.** A capability nothing used
cost the suite its consistency, so the rule now buys consistency instead.

One name in this suite claimed `returns403` while asserting the opposite — rewriting a
misnamed middle part usually exposes that condition and outcome were swapped.

---

## 7. Deriving the axis, and the value

`moderation/ModerationAuditMatrixTest` derives its axis from `ActionType.values()` and found two
constants — `PROMOTE` and `DEMOTE` — that were declared and never reached.

`ConstraintMappingSchemaIT` iterated `KnownConstraint.values()` while retyping each table name in a
switch:

```java
case PLANNER_REPORT -> "planner_reports";   // whatever the enum says
```

Renaming the enum's table left all nine dynamic tests green. The axis was derived; the value was
not. Reading `constraint.table()` made the same mutation fail immediately.

---

## 9. What earns a deletion

A test guarding against locale-sensitive string matching earns deletion the day the code stops
matching strings, because the mistake becomes unrepresentable. Record that in the commit message —
it is a win, not lost coverage.

---

## 10. Values versus layout

A teardown sweep repeated verbatim across dozens of files is one rule stated dozens of times and
owned nowhere; extract it.

Dozens of `User.builder()` blocks sharing a shape but carrying different emails, ids, and roles are
local fixtures. Collapsing them trades visible arrangement for invisible shared defaults, and a
mechanical rewrite can silently change what a test exercises.

---

## 11. Vacuous assertions, and rules that never bite

The literature calls an assertion-free test an *Unknown Test*. Four shapes recurred here:

- **Prohibiting something nothing does.** `verify(tokenValidator, never()).validateToken(any())`
  passed unconditionally because no filter path calls that method.
- **Asserting a value that already held.** A timeout-clearing test asserted `assertNull` on a field
  that was null before the call.
- **Never arranging the condition the name claims.** A fan-out test named `WhenSubscribersExist`
  stubbed no subscribers, so nothing distinguished it from the empty case.
- **Fixtures that cannot be told apart.** Four per-user flags stubbed to the same falsy value pass
  with any two of them crossed.

Three creation tests asserted `assertNotNull(response.id())` while their own `thenAnswer` stub
assigned that id. And a mocked `HttpServletResponse` cannot report a status, forcing
`verify(response).setStatus(503)`, where `MockHttpServletResponse` is a real implementation that
makes the same fact directly assertable.

**Rules.** A truncation rule aimed at the shared database proves nothing when probed against a class
that owns its own. A `deleteAll` rule matching the constant pool by substring rejected
`deleteAllByPlannerIds`, which names the rows it removes and is the correct way to clean up;
narrowing it to whole-table call targets fixed that and could as easily have removed its teeth.

Enabling `vitest/expect-expect` reported thirteen assertion-free tests, twelve of which called a
helper that asserts internally. A rule whose first run is all false positives gets configured away,
so the run after the `assertFunctionNames` fix is the one that told us anything.

---

## 12. What `@Transactional` costs

Removing the annotation surfaces real behavior: listeners bump a row's version, so an entity
captured in `@BeforeEach` is stale by the time the test mutates it, and saving it raises
`ObjectOptimisticLockingFailureException`. Re-read before mutating.

A test staying inside the first-level cache asserts object identity while appearing to assert
persisted equality. One compared an `Instant` for exact equality and passed only because both reads
returned the same instance; once the value round-tripped, MySQL's `DATETIME(6)` dropped the
nanoseconds a Java `Instant` carries.

---

## 13. What sharing a database costs

`deleteAll()` in `@BeforeEach` is a global assertion disguised as cleanup, true only when the class
runs alone. `$.content[0].title` and `actions.get(0)` claim a position in a global list that belongs
to whoever sorted first. `repository.count()` and `hasSize(3)` claim a total; a before/after delta
is the same claim twice with a race between the reads.

Converting `hasSize(1)` to `hasItem(mine)` plus `not(hasItem(theirs))` usually *strengthens* a test,
because the original passes when the single row present belongs to someone else.

`PlannerUserDeleteSweepIT` hard-deleted other classes' users and surfaced as foreign-key violations
in three unrelated classes.

Three classes asserting on one view recorder's in-memory buffer failed intermittently for a day
before that was named. A private database is really a private context, and a private context is a
private bean.

`.actorId(1L)` asserts that user 1 exists and is yours, which holds only on a database nobody else
writes to and no engine enforces.

`registerOwnDatabase` costs one application context, so narrow the assertion first.

**Annotations.** `@Execution(CONCURRENT)` on a class makes its methods concurrent, and they share
that class's `@BeforeEach` fixtures; "classes parallel, methods sequential" exists only in
`junit-platform.properties`. `@ResourceLock` is an in-JVM lock, so a locked class truncating a shared
table still clobbers every class that did not declare it. Spring collects `@DynamicPropertySource`
from a class hierarchy with no defined order, so a subclass binding its own database while
inheriting one that binds the shared database is a coin flip.

Serialization coordinates only participants — a developer's own running Redis never agreed to take
your lock.

---

## 14. Coupled changes

Adding a constructor dependency breaks every test that builds that service positionally, and the
size of that break measures how mock-heavy the suite is around it.

With `targetTests` unset, PIT derives it from `targetClasses` and silently ignores tests whose
package differs from the class under test.

Swapping `deleteAll()` for `deleteAllInBatch()` to dodge a version conflict leaves orphaned satellite
rows, because the batch form issues one `DELETE FROM` and skips the cascade.

An in-memory database named explicitly in `spring.datasource.url`, paired with
`ddl-auto=create-drop`, lets a closing context drop the schema under the ones still running. Boot's
`generate-unique-name` default exists to prevent exactly this.
