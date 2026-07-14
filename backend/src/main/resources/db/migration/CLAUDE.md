# Flyway Migrations

- All schema changes go through Flyway (`V{version}__{description}.sql`); never alter the database manually. Migrations are immutable — never edit an existing one; revert with a new migration.
- MySQL strict mode: these succeed on an empty DB but fail — or silently corrupt — populated tables: removing/renaming a SET member, reordering SET members (corrupts the bitmask; only append), narrowing a column type, `ADD COLUMN ... NOT NULL` without `DEFAULT`, NULL→NOT NULL with existing NULLs, charset/collation changes.
- Smoke-test seed (`backend/src/test/resources/db/seed/migration-test-seed.sql`): update it in the same PR whenever a migration changes SET/ENUM members, column types or nullability, adds tables, or contains data-dependent UPDATE/DELETE.
  - Seed values reflect the schema after all merged migrations; every SET and ENUM member appears in at least one row; JSON columns hold realistic structure; use `INSERT IGNORE` for idempotency.
  - CI tests a PR's new migration against the base branch's seed, so your seed update serves the next PR, not your own.
- The `it` test profile validates entities against this real migrated schema (`ddl-auto=validate`) — never weaken to `create-drop`. Consequences:
  - MySQL ENUM/SET columns report to JDBC as CHAR: annotate the entity field `@JdbcTypeCode(SqlTypes.CHAR)` (composes with `@Convert`/`@Enumerated`); a TINYINT column under an `int` field needs `@JdbcTypeCode(SqlTypes.TINYINT)`.
  - A `@Convert` enum must be total over every value the column actually holds, including sentinel/seed rows (e.g. the `provider='system'` deleted-user sentinel), or queries materializing that row throw.
  - When validate surfaces drift, prefer adapting the entity over authoring a corrective migration — unless the column type itself is wrong, which is a product call to confirm first.
