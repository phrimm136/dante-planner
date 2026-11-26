# Backend

Spring Boot + Java + JPA/Hibernate + Bean Validation (Jakarta) + MySQL + Redis + Flyway.

## Build & Tooling

- Gradle checks run through the `checks` CLI: `<checkout or worktree>/.claude/skills/checks/bin/checks be test|check|checkstyle|pit`; a direct `gradlew` test, check, build, compile, checkstyle or pitest task is hook-refused. Before a commit, `checks changed` runs the test classes named after the changed sources. A whole `be test`, `be check` or `be pit` takes `--full REASON`, because PR Gate runs them (ADR 515).
- Lint runs locally before a commit that touches Java: `checks changed` runs `checks be lint` (checkstyle in all its tasks, CPD and the ast-grep tasks of `gradle check`), and PR Gate runs the same. It needs `frontend/node_modules`.
- Unit tier only (no Docker): `checks be test --exclude-tags containerized`. Add `--tests "<pattern>"` to scope further; the ArchUnit suite is added to any `--tests` run.
- Full suite (default `test` task) also runs the integration tests (MySQL/Redis Testcontainers, tagged `containerized`), which require Docker.
- Import order (enforced): java → spring framework → spring boot → spring data jpa → jakarta.validation → jakarta.persistence → third-party → project packages.

## Checked facts, decisions and effects

- Check at construction and keep the result: a fact later code relies on is a type annotated `@Parsed` with a static factory that returns it, returns a `Result` of it, or throws, and the code after it takes that type (`ParsedTypeRuleTest`). A new `void requireX` fails `VoidCheckRuleTest`.
- Checks that run one after another with no read between them return `Result` and are joined with `flatMap`; the service unwraps with `orElseThrow` or a `switch`. A method returning `Result` sits under `@CheckReturnValue` (`ResultRuleTest`), and no `@Transactional` method returns one (`FailureUnionBoundaryTest`).
- A fact about a row holds under the lock or transaction it was read in. Parse it again after taking a lock.
- A decision is a pure method returning a sealed type or enum (`DecisionPurityTest`); the method that performs effects switches on it or ends early by a guard, an `if` without `else` whose body is one `throw`. That branching is read in review, through the raised lines of `shell-branches.txt` (where a `case null` or `default` label is counted), not refused by a test. A type ending in `Outcome` reports what a command did.
- A public method takes no `boolean`: use an enum or two methods (`FlagParameterRuleTest`); a `@Modifying @Query` method may take a boolean its SET clause assigns, and a `@RestController` handler a boolean bound by `@RequestParam`, `@PathVariable`, `@RequestHeader` or `@CookieValue`, which its first line turns into a type.
- A write is an insert or a conditional `UPDATE` or `DELETE` decided by its matched-row count (`WriteOutcome`); an entity is `@Immutable` (`ImmutableEntityRuleTest`), and no `@Modifying` query clears the persistence context (`PersistenceContextClearingTest`).
- A request names the state it wants; no query assigns a column from its own negation (`SelfFlipQueryTest`).
- The lists `backend/config/void-checks.txt`, `flag-parameters.txt`, `mutable-entities-*.txt`, `context-clearing-*.txt` and `self-flip-queries.txt` only shrink: delete the line your change ended.
- `backend/config/shell-branches.txt` counts the branches of each effectful method and never blocks a change: `checks be freeze-branches` rewrites it, and a raised line in the diff is a method review reads for a decision inside an effect method.

## Async model

- The async model is `@Scheduled` + `@TransactionalEventListener(AFTER_COMMIT)` + Redis pub/sub + the outbox dispatch executor. `@Async`, `@EnableAsync` and `ThreadPoolTaskExecutor` are banned everywhere else; the single exception is frozen by name in `ConventionBaselineTest` and paired with a staleness check.
- Observer effects are not written by listeners. A write records a `domain_events` row inside its own transaction, `DomainEventDispatcher` derives the effect in a transaction of its own, and `DomainEventRelay` re-dispatches whatever the eager hop missed. Notification rows are derived by `INSERT IGNORE` on `uk_notification_dedup`, never after an existence check.

## Migrations touch tests

- Any migration that alters existing columns or modifies data must update the smoke-test seed `src/test/resources/db/seed/migration-test-seed.sql` in the same PR — details in `src/main/resources/db/migration/CLAUDE.md`.
