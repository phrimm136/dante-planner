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

## 3. Mock only what you do not own

Mock the things whose behavior is someone else's contract: Redis, the OAuth HTTP client, the
clock. Never mock a service in this codebase — it is a refactoring target, and a mock of it
freezes today's decomposition into the test.

`readpath/PrimaryReCheckTest` holds one mock, and that one stands in for Redis.

When a unit test needs five mocks to reach its assertion, the assertion is usually at the wrong
level. Drive the same behavior through MockMvc against a real datastore instead.

---

## 4. `verify(never())` and `verify()` are not the same tool

`verify(never())` asserts the **absence** of a side effect, which frequently has no state-based
alternative — nothing was persisted, no event was published, the far region was never called. It
stays valid across refactors as long as the prohibition holds.

`verify(...)` asserts a collaboration **happened**, and almost always has a state-based
replacement: assert the row, the response, or the index that the collaboration produces.

Prefer the outcome. Reach for `verify(never())` when absence is the whole point.

---

## 5. The tier follows the behavior, not the preference

Most of this system's interesting behavior runs after commit: publish notifications, SSE fan-out,
the filter-index rebuild, the tombstone write, GTID capture. A `@Transactional` test rolls back,
so `@TransactionalEventListener(AFTER_COMMIT)` never fires and none of it is observable.

That is a hard constraint, not a style choice. The usual advice to keep the containerized tier
small does not apply where correctness lives in post-commit effects and cross-region routing.
Follow the behavior.

---

## 6. Name the test after the invariant

A test named after a method dies with the method, and cannot be cited from a javadoc `@see` once
the method is renamed. A test named after the rule survives both.

| Dies | Survives |
|---|---|
| `isTombstoned_WhenRedisDown_ReturnsFalse` | `deleted_planner_is_masked_on_replica_hit` |
| `upsertPlanner_bannedUser_throws` | `a_ban_does_not_block_private_planner_work` |

The mechanical `methodName_WhenCondition_ExpectedBehavior` form stays right for ordinary cases.
Use the invariant form for anything a comment will point at, because `-Xdoclint:reference` makes
that citation a compile-time link.

---

## 7. Derive the axis, never list it

Where a rule holds across a set — every endpoint, every role, every enum constant — read the set
from the code rather than typing it out:

- endpoints from `RequestMappingHandlerMapping`
- roles from `UserRole.values()`
- action types from `ModerationAction.ActionType.values()`
- event types from `SseEventType.values()`

A derived axis grows on its own, so a new member is covered without anyone remembering. A typed
list covers what its author thought of, and the gaps are exactly where defects sit.
`security/RestrictedPrincipalMatrixTest` and `moderation/ModerationAuditMatrixTest` both work this
way; the second found two enum constants that no code wrote.

Two rules for a derived matrix:

- **Assert both arms.** Over-application is as much a defect as under-application, and the
  un-applied arm is where a newly added member lands by default.
- **Keep the rows independent.** Dynamic tests share one `@BeforeEach`, so a row that mutates
  shared state hands every later row a false pass, and the matrix degenerates into a list.

---

## 8. Reach for properties where the domain has laws

Some guarantees hold for all inputs, not one:

- `setPublished(true)` applied twice equals applied once
- `syncVersion` never decreases
- `comment_count` never goes negative after any command sequence
- keyword normalization is idempotent

Property tests survive every implementation, and they generate the sequences nobody thinks to
write. Stateful property testing over the planner lifecycle is where interaction defects surface.

---

## 9. When a test should be deleted

**Delete a test when the defect it pins can no longer be written. Never when the code merely
moved.**

A test guarding against locale-sensitive string matching earns deletion the day the code stops
matching strings, because the mistake becomes unrepresentable. Record that in the commit message —
it is a win, not a loss of coverage.

A test that breaks because a check moved between classes has not lost its subject. It was asserting
the wrong thing, and the fix is to re-aim it, not to delete it.

---

## 10. Repeated shape is not duplication

Before extracting a repeated block, check whether the **values** repeat, not just the layout.

A teardown sweep repeated verbatim across dozens of files is a rule stated dozens of times and
owned nowhere; extract it. Dozens of `User.builder()` blocks that share a shape but carry different
emails, ids, and roles are explicit local fixtures; collapsing them trades readable, visible
arrangement for invisible shared defaults, and a mechanical rewrite can silently change what a test
exercises.

The test: if the extracted helper would need a parameter for nearly every field, there was nothing
to extract.

---

## 11. If you change one of these, check the others

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
