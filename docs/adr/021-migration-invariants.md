# 021 migration-invariants
epic: none · pr: none

## Decisions
- @flyway @immutability — Applied migration filenames are immutable; naming inconsistencies are fixed forward from the next version, never corrected in place. REJECTED: renaming a migration for consistency — Flyway records version and description in its schema history, so a rename fails validation on every database that has already applied it, including production.
- @migration @backfill @ci — Data backfills accompanying a schema change are written as plain SQL, so the migration CI gate executes the entire chain end to end; runtime parity with the application-side extractor is enforced by the drift reconciler rather than by shared code. REJECTED: an application-side migration class for the backfill — it is invisible to a smoke gate that only runs SQL, which means the most consequential step of the migration is the one step CI never exercises.

## Takeaway
- takeaway: a migration gate only protects the steps it can execute. Choosing the backfill's language is therefore a decision about test coverage, and the more expressive option is the one that gets no coverage at all.
