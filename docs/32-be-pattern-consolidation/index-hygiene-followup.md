# Index Hygiene — Follow-up (Phase 15)

Two index-hygiene items surfaced in phase 15. Neither is actioned here: the first is gated on
EXPLAIN evidence unavailable in this environment, the second is a report-only parity finding per
the phase-15 charter (no schema or production changes). Both are recorded for a follow-up
measurement-first workstream.

## 1. `DROP INDEX idx_published` — DECLINED, pending EXPLAIN

### Candidate

Drop `idx_published` on `planners(published)`, created in
`V002__add_planner_publishing.sql:14` (`ADD INDEX idx_published (published)`).

### Rationale for the drop

- **Low selectivity.** `published` is a boolean; the index has at most two distinct values, so the
  optimizer rarely prefers it over a scan on a table of any size.
- **Subsumed by a leftmost prefix.** `idx_planner_recommended` on
  `planners(published, deleted_at, hidden_from_recommended)` (`V020__add_moderation_fields.sql:16`)
  already leads with `published`. Any access path that would use `idx_published` can use the
  leftmost prefix of `idx_planner_recommended` instead.
- **Write-path debt.** `planners` is write-hot (autosave). Every redundant secondary index is a
  per-write maintenance cost with no read-path payoff. The spec's index principle: indexes serve
  access paths, not queries; near-universal / low-selectivity predicates earn no dedicated index.

### Why DECLINED here

The spec gates this drop on evidence, not static reasoning:

> `DROP INDEX idx_published` proceeds only if EXPLAIN + unused-index stats under the loadtest
> profile confirm redundancy.

There is **no live loadtest DB** in this environment, so the gate cannot be satisfied. Authoring
the migration blind would violate the measurement-first charter. This is the spec's explicitly
allowed "declined and documented" outcome.

### Required gate before authoring the migration

1. Seed a realistic dataset under the loadtest profile.
2. `EXPLAIN` (or `EXPLAIN ANALYZE`) the recommended-list and published-detail queries; confirm the
   planner selects `idx_planner_recommended` (or a scan), never `idx_published`.
3. Query `sys.schema_unused_indexes` after a representative load run; confirm `idx_published`
   appears (zero reads).
4. Only then author `V045__drop_redundant_published_index.sql` (next free version — V031 through
   V044 are already applied), reversible by a follow-up `CREATE INDEX` migration.

Note: the spec's placeholder name `V031__drop_redundant_published_index.sql` predates later
migrations; the actual next version is **V045**.

## 2. `ModerationAction` `@Index` ↔ migration parity mismatch — REPORT ONLY

The one-time `@Table(indexes=...)` vs Flyway parity sweep (Hibernate `validate` checks columns but
**never** indexes, so this is otherwise unguarded) found every entity in parity except one.

### Entity: `moderation/entity/ModerationAction.java`

Declares:

```java
@Index(name = "idx_moderation_target_created", columnList = "target_id, created_at")
@Index(name = "idx_moderation_actor",          columnList = "actor_id")
@Index(name = "idx_moderation_type",           columnList = "action_type")
```

Live schema after `V034__add_taken_down_columns.sql`:

- `V034:30` — `DROP INDEX idx_moderation_target_created`
- `V034:33` — `DROP COLUMN target_id`
- `V034:36` — `CREATE INDEX idx_moderation_target_uuid_created (target_uuid, created_at DESC)`
- `V034:39` — `CREATE INDEX idx_moderation_target_type (target_type)`

### Mismatch

- **Stale declaration:** the entity still declares `idx_moderation_target_created` on
  `columnList = "target_id, created_at"`. Both the index and the `target_id` column were dropped by
  V034. The annotation now references a column that no longer exists.
- **Undeclared live indexes:** `idx_moderation_target_uuid_created` and `idx_moderation_target_type`
  exist in the DB but are not declared on the entity.

Why `ddl-auto=validate` (Tier-3) did not catch it: `validate` never inspects index definitions, so
a stale `@Index` referencing a dropped column is inert. The annotation only matters under
`ddl-auto=update`/`create` (not used in any profile), where it would attempt to create an index on
a missing column.

### Suggested fix (follow-up, not this phase)

Update `ModerationAction`'s `@Table(indexes=...)` to mirror the live schema:

```java
@Index(name = "idx_moderation_target_uuid_created", columnList = "target_uuid, created_at DESC")
@Index(name = "idx_moderation_target_type",         columnList = "target_type")
@Index(name = "idx_moderation_actor",               columnList = "actor_id")
@Index(name = "idx_moderation_type",                columnList = "action_type")
```

This is an annotation-only change (no DB effect under `validate`) and is deferred to keep phase 15
report-only for schema/production.

### Other entities — in parity

`Notification`, `PlannerComment`, `PlannerCommentVote`, `Planner` each declare only indexes that a
migration creates with matching name and columns. Migrations create additional indexes those
entities do not declare (e.g. `idx_published*`, `idx_notification_public_id`,
`idx_comment_public_id`, `idx_comment_vote_deleted`) — expected and benign: an `@Index` list is a
subset declaration, and `validate` ignores it.
