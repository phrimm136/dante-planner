# Backend

Spring Boot + Java + JPA/Hibernate + Bean Validation (Jakarta) + MySQL + Redis + Flyway.

## Build & Tooling

- Gradle: always `/home/user/github/LimbusPlanner/gradlew -p backend` — bare `gradlew` without a project dir is hook-blocked.
- Forbidden patterns are hook-enforced (`.claude/hooks/forbidden-patterns.json`): field injection, empty catch blocks, string concatenation in `@Query`, `@Transactional` on private methods, and more — the hook blocks the write, so fix before saving.
- Integration tests (MySQL/Redis Testcontainers, tagged `containerized`) run in the default test task and require Docker; exclude them with `-PexcludeTags=containerized`.
- Import order (enforced): java → spring framework → spring boot → spring data jpa → jakarta.validation → jakarta.persistence → third-party → project packages.

## Comments & Javadoc (main and test)

- A comment is justified only as: why/constraint, warning, workaround, cross-ref (spec/OWASP), tracked TODO (`#issue`), or non-obvious invariant (nullability, threading).
- Never: restating code, change history ("was missing", "moved from"), tombstones for deleted code, commented-out code — git owns all of that.
- No parenthetical micro-explanations (`doX(); // (does X)`) — delete, or promote to a standalone why-comment.
- Javadoc required on public/protected classes and business-logic methods; document all business `@throws`; third-person voice; no `@author`/`@version`/`@since`; skip trivial getters and self-explanatory DTOs.

## Migrations touch tests

- Any migration that alters existing columns or modifies data must update the smoke-test seed `src/test/resources/db/seed/migration-test-seed.sql` in the same PR — details in `src/main/resources/db/migration/CLAUDE.md`.
