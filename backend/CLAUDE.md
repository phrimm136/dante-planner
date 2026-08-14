# Backend

Spring Boot + Java + JPA/Hibernate + Bean Validation (Jakarta) + MySQL + Redis + Flyway.

## Build & Tooling

- Gradle: always `/home/user/github/LimbusPlanner/backend/gradlew -p /home/user/github/LimbusPlanner/backend` — bare `gradlew` without a project dir is hook-blocked, and a relative `-p` resolves against the shell's current directory.
- Forbidden patterns are hook-enforced (`.claude/hooks/forbidden-patterns.json`): field injection, empty catch blocks, string concatenation in `@Query`, `@Transactional` on private methods, and more — the hook blocks the write, so fix before saving.
- Unit tier only (no Docker): `/home/user/github/LimbusPlanner/backend/gradlew -p /home/user/github/LimbusPlanner/backend test -PexcludeTags=containerized`. Add `--tests "<pattern>"` to scope further.
- Full suite (default `test` task) also runs the integration tests (MySQL/Redis Testcontainers, tagged `containerized`), which require Docker.
- Import order (enforced): java → spring framework → spring boot → spring data jpa → jakarta.validation → jakarta.persistence → third-party → project packages.

## Async model

- The async model is `@Scheduled` + `@TransactionalEventListener(AFTER_COMMIT)` + Redis pub/sub + the outbox dispatch executor. `@Async`, `@EnableAsync` and `ThreadPoolTaskExecutor` are banned everywhere else; the single exception is frozen by name in `ConventionBaselineTest` and paired with a staleness check.
- Observer effects are not written by listeners. A write records a `domain_events` row inside its own transaction, `DomainEventDispatcher` derives the effect in a transaction of its own, and `DomainEventRelay` re-dispatches whatever the eager hop missed. Notification rows are derived by `INSERT IGNORE` on `uk_notification_dedup`, never after an existence check.

## Migrations touch tests

- Any migration that alters existing columns or modifies data must update the smoke-test seed `src/test/resources/db/seed/migration-test-seed.sql` in the same PR — details in `src/main/resources/db/migration/CLAUDE.md`.
