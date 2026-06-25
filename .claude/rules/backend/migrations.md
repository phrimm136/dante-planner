---
paths:
  - "backend/src/main/resources/db/migration/*.sql"
---

# Flyway Migration Rules

## Migration Smoke-Test Seed (MANDATORY)

Every migration that alters existing columns or modifies data MUST be tested against populated tables.

**Seed file:** `backend/src/test/resources/db/seed/migration-test-seed.sql`

### When to update the seed:
- Adding, renaming, or removing SET/ENUM members
- Adding `NOT NULL` columns without defaults
- Changing column types (`MODIFY COLUMN`)
- `UPDATE`/`DELETE` statements that depend on existing data
- Creating new tables (add seed rows for the new table)
- Any `ALTER TABLE` that touches columns with existing data

### How to update:
- Seed values must match the schema state **after** all current migrations
- Every SET member must appear in at least one seed row
- Every ENUM value must appear in at least one seed row
- JSON columns must contain realistic structure (for `JSON_TABLE` extraction)
- Use `INSERT IGNORE` for idempotency
- Use conditional inserts for tables that may not exist at earlier migration versions

### Why:
MySQL strict mode rejects schema changes that conflict with existing data (e.g., removing a SET member still present in rows, adding `NOT NULL` without defaults to populated tables, narrowing column types that truncate values). Migrations run against an empty DB always succeed — only populated tables expose these failures.

### CI test flow:
1. Detect new migrations in the PR: `git diff --name-only origin/main...HEAD -- 'backend/src/main/resources/db/migration/*.sql'`
2. Run existing migrations (on main, excluding new ones)
3. Run seed — simulates production data state
4. Run new migrations from the PR
5. If step 4 fails, the migration is unsafe against populated tables

### When to update the seed in a PR:
- The seed reflects the schema state **after all merged migrations** (i.e., before your new migration)
- If your new migration changes SET/ENUM definitions, column types, or adds tables:
  update the seed in the **same PR** so it's ready for the next PR's CI run
- The seed update does NOT affect your own migration's test — CI uses the seed
  from the base branch (main) to test your migration

## MySQL Strict Mode Pitfalls

Operations that succeed on empty tables but fail on populated tables in strict mode:

| Operation | Failure condition |
|-----------|-------------------|
| `MODIFY COLUMN SET(...)` removing a member | Rows contain the removed member |
| `MODIFY COLUMN SET(...)` reordering members | Silently corrupts bitmask data |
| `MODIFY COLUMN` narrowing type (e.g., `VARCHAR(255)` → `VARCHAR(50)`) | Existing values exceed new length |
| `ADD COLUMN ... NOT NULL` without `DEFAULT` | Table has existing rows |
| `MODIFY COLUMN` changing `NULL` → `NOT NULL` | Existing rows contain NULL |
| `ALTER TABLE` changing charset/collation | Existing data incompatible with new encoding |

## SET Column Safety

SET columns use bitmask storage. Member positions are assigned by ordinal order.

**Safe:** Appending new members at the end

**Unsafe (requires add → migrate → remove pattern):**
- Removing a member
- Renaming a member
- Reordering members (silently corrupts all existing data)

## Schema-Truth Tier (`ddl-auto=validate`)

The `it` test profile (`application-it.properties`, used by `@ActiveProfiles("it")` MySQL-Testcontainer tests) runs `spring.flyway.enabled=true` + `spring.jpa.hibernate.ddl-auto=validate` — it validates the entities against the **real migrated schema**, not Hibernate's invented `create-drop` schema. This is the authoritative guard for the dangerous class of bug (a converter writing `"DRAFT"` into `ENUM('draft','saved')`) that every `create-drop` profile silently masks. Never weaken it back to `create-drop`.

**Consequences when adding/changing an entity or migration:**

- **MySQL `ENUM`/`SET` columns report to JDBC as `CHAR`**, but a `String` / `@Enumerated(STRING)` / `@Convert`-backed field maps to `VARCHAR` → `validate` fails with `wrong column type ... found [enum/set], expecting [varchar]`. Fix by annotating the field `@JdbcTypeCode(org.hibernate.type.SqlTypes.CHAR)` (no schema change; length is not strictly checked). It composes with `@Convert` and `@Enumerated`. A `TINYINT` column under an `int` field → `@JdbcTypeCode(SqlTypes.TINYINT)`.
- **Flyway needs its own datasource pointed at the container.** The base `application.properties` pins `spring.flyway.{url,user,password}` to the prod DB; a Testcontainer test MUST also register `spring.flyway.url/user/password` (not just `spring.datasource.*`) via `@DynamicPropertySource`, or Flyway connects to prod and fails.
- **Sentinel/seed rows are part of the column domain.** A `@Convert` enum must be *total* over every value the column actually holds (e.g. the `provider='system'` deleted-user sentinel from V000/V009), or it throws on any query materializing that row.
- **AFTER_COMMIT writes need their own transaction.** A `@TransactionalEventListener(AFTER_COMMIT)` runs with no live transaction; any `notify*`/persist it calls must be `@Transactional(propagation = REQUIRES_NEW)` or the insert never commits (it silently vanishes).

> Decision policy for surfaced drift: prefer adapting the entity (`@JdbcTypeCode`, keep the DB) over a corrective migration, unless the column type itself is wrong (e.g. timestamp precision) — that's a product call, confirm direction before authoring an immutable migration.

> Debugging note: Gradle does **not** capture application stdout/logs in this project's test output — use assertion-based bisection (probe an observable flag like `recommendedNotifiedAt`), not log statements, to localize a failing integration test.
