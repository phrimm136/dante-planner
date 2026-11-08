# Backend

Spring Boot + Java + JPA/Hibernate + Bean Validation (Jakarta) + MySQL + Redis + Flyway.

## Build & Tooling

- Gradle checks run through the `checks` CLI: `<checkout or worktree>/.claude/skills/checks/bin/checks be test|check|checkstyle|pit`; a direct `gradlew` test, check, build, compile, checkstyle or pitest task is hook-refused.
- Unit tier only (no Docker): `checks be test --exclude-tags containerized`. Add `--tests "<pattern>"` to scope further; the ArchUnit suite is added to any `--tests` run.
- Full suite (default `test` task) also runs the integration tests (MySQL/Redis Testcontainers, tagged `containerized`), which require Docker.
- Import order (enforced): java → spring framework → spring boot → spring data jpa → jakarta.validation → jakarta.persistence → third-party → project packages.

## Async model

- The async model is `@Scheduled` + `@TransactionalEventListener(AFTER_COMMIT)` + Redis pub/sub + the outbox dispatch executor. `@Async`, `@EnableAsync` and `ThreadPoolTaskExecutor` are banned everywhere else; the single exception is frozen by name in `ConventionBaselineTest` and paired with a staleness check.
- Observer effects are not written by listeners. A write records a `domain_events` row inside its own transaction, `DomainEventDispatcher` derives the effect in a transaction of its own, and `DomainEventRelay` re-dispatches whatever the eager hop missed. Notification rows are derived by `INSERT IGNORE` on `uk_notification_dedup`, never after an existence check.

## Migrations touch tests

- Any migration that alters existing columns or modifies data must update the smoke-test seed `src/test/resources/db/seed/migration-test-seed.sql` in the same PR — details in `src/main/resources/db/migration/CLAUDE.md`.
