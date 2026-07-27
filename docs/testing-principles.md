# Testing principles

What a test should assert, so that it survives a refactor of the code it covers.

Rules only. Each links to the incident or measurement behind it in
[testing-evidence.md](testing-evidence.md); read that when a rule looks wrong, costly, or arbitrary.
`backend/src/test/CLAUDE.md` governs mechanics — which tier, how to wire Testcontainers.

---

## 1. Ask two questions before asserting

1. Could this fail while the system is still correct?
2. Could this pass while the system is broken?

Yes to either means the test's subject is structure, not behavior. → [why](testing-evidence.md#1-the-two-questions)

## 2. Assert high on the stability ladder

| Level | Changes when | Assert here |
|---|---|---|
| Observable outcome — status, body, persisted row, emitted event | behavior changes | **yes** |
| Domain invariant — `comment_count >= 0`, publish is idempotent | the domain changes | **yes** |
| Public API signature | interfaces change | sometimes |
| Internal collaboration — who calls whom, with what arguments | every refactor | **no** |
| Implementation detail — private method, SQL text, field name | constantly | no |

## 3. Mock only what the property does not depend on

- A double replaces a dependency's semantics with a model of them. Mock a dependency only when the
  property under test does not depend on those semantics. → [why](testing-evidence.md#3-what-may-be-mocked)
- Truth from this codebase's own logic needs no environment. Truth from a dependency's guarantee —
  atomicity, isolation, ordering, durability, replication visibility — cannot be asserted against a
  model of itself.
- **Never mock a service in this codebase.** It is a refactoring target, and a mock freezes today's
  decomposition into the test.
- Five mocks to reach an assertion means the assertion is at the wrong level. Drive it through
  MockMvc against a real datastore.

## 4. Prefer the outcome to the collaboration

- `verify(never())` asserts an absence that often has no state-based alternative. `verify(...)`
  asserts a collaboration that almost always does — assert the row, response, or index instead.
- Under `STRICT_STUBS` a stub is already two assertions, so `verify(mock).m(exactArgs)` after
  `when(mock.m(exactArgs))` is redundant. Tighten matchers from `any()` to literals.
- **Check for an enclosing catch first**: a swallowed exception un-asserts a strict
  stub. → [why](testing-evidence.md#4-strict-stubs-and-what-un-asserts-them)

## 5. The tier follows the behavior

Most interesting behavior here runs after commit. A `@Transactional` test rolls back, so
`AFTER_COMMIT` listeners never fire and none of it is observable. The advice to keep the
containerized tier small does not apply where correctness lives in post-commit
effects. → [why](testing-evidence.md#5-which-behavior-lives-after-commit)

## 6. Name the test subject, condition, expectation

`subject_WhenCondition_Expectation`, enforced by `architecture/TestNamingConventionTest`.

| Write | Not |
|---|---|
| `findById_WhenExists_ReturnsUser` | `findById_exists_returnsUser` |
| `upsertPlanner_WhenOwnerBanned_KeepsPrivateWork` | `upsertPlanner_bannedUser_throws` |
| `deletedEntity_WhenReplicaHits_IsMasked` | `deleted_entity_is_masked_on_replica_hit` |

A middle part reading as an outcome (`cannotCreateComment`, `NoDeadlock`) is misnamed, not
exempt. → [why](testing-evidence.md#6-the-naming-conventions-cost-measured)

## 7. Derive the axis, never list it

Read the set from the code: `RequestMappingHandlerMapping`, `UserRole.values()`,
`ModerationAction.ActionType.values()`, `SseEventType.values()`, `KnownConstraint.values()`.

- **Derive the value too, not just the axis.** → [why](testing-evidence.md#7-deriving-the-axis-and-the-value)
- **Assert both arms.** The un-applied arm is where a new member lands by default.
- **Keep the rows independent.** Dynamic tests share one `@BeforeEach`.

## 8. Reach for properties where the domain has laws

Property-based testing generates the sequences nobody writes. §3 governs whether a given law can be
asserted without a real dependency.

- `setPublished(true)` applied twice equals applied once
- `syncVersion` never decreases
- `comment_count` never goes negative after any command sequence
- keyword normalization is idempotent

## 9. Delete a test when its defect can no longer be written

Never when the code merely moved — a test that breaks because a check changed classes has not lost
its subject. Re-aim it. → [why](testing-evidence.md#9-what-earns-a-deletion)

## 10. Repeated shape is not duplication

Check whether the **values** repeat, not just the layout. If the extracted helper would need a
parameter for nearly every field, there was nothing to extract. → [why](testing-evidence.md#10-values-versus-layout)

## 11. What cannot fail is not a check

An assertion is vacuous when it cannot fail against any implementation. Four shapes
recur: → [why](testing-evidence.md#11-vacuous-assertions-and-rules-that-never-bite)

- prohibiting something nothing does
- asserting a value that already held
- never arranging the condition the name claims
- fixtures that cannot be told apart

Also: an arrangement stub that sets the asserted field tests the fixture, and a mocked holder hides
the outcome you wanted.

- **Plant a decoy when a test picks a branch**, so the branch is identified by what came back rather
  than by what crashed.
- **Watch it fail before you trust it.** Change the expected value, confirm the failure, change it
  back. Mutation testing, by hand, on one assertion.
- **Rules need this more than assertions do.** A rule that matches nothing passes on every commit.
  Introduce a real violation, put it where a real one would land, read the failure message, and
  re-run after any narrowing.

## 12. `@Transactional` is not a cleanup mechanism

Use it only where the transaction boundary is the subject. It costs three things, all
silent: → [why](testing-evidence.md#12-what-transactional-costs)

- `AFTER_COMMIT` listeners never fire
- InnoDB flushes its FULLTEXT cache at commit, so `MATCH ... AGAINST` cannot see the test's own rows
- it binds a transaction to a `ThreadLocal` that a blocked ForkJoin thread can inherit

## 13. A test owns the rows it creates, and nothing else

Every integration class shares one database and they run concurrently, so three natural-sounding
claims are false: "this table holds only my rows", "index 0 is mine", "the count is
mine". → [why](testing-evidence.md#13-what-sharing-a-database-costs)

- Scope to an identity the test owns. **Prefer a negative**: "my row is absent" survives neighbouring
  data where "exactly N rows" cannot.
- Acting globally counts too — a sweep or a rebuild touches every class's rows.
- A shared context is a shared object graph. In-memory singleton state (a buffer, a cache, a
  scheduler, a metric registry) can be narrowed to nothing.
- The rule is about stores, not SQL. `flushAll()` is `deleteAll()` for Redis.
- When the subject genuinely is a whole table or index, take `registerOwnDatabase`.

**The parallelism annotations cannot express what you want:** `@Execution(CONCURRENT)` propagates to
a class's methods, `@ResourceLock` excludes only other lock holders and cannot cross Gradle forks,
and sibling `@BeforeEach` methods have no defined order.

> Contention is a naming problem before it is a concurrency problem. Names can be enumerated
> statically; interleavings cannot.

Three remedies in order: **rename**, **privatize**, **serialize**.

## 14. If you change one of these, check the others

- Adding a constructor dependency breaks every test that builds the service positionally.
- Adding an endpoint, role, or enum constant should require no edit to a derived matrix.
- Promoting a check to error requires every existing site to be clean first.
- Mutation coverage measures whether a line is protected; line coverage, only whether it ran.
- Removing `@Transactional` makes a test's `AFTER_COMMIT` effects real.
- `deleteAll()` cascades to children; `deleteAllInBatch()` does not.
- An in-memory database named explicitly in `spring.datasource.url` is shared by every context on
  that profile. → [why](testing-evidence.md#14-coupled-changes)
