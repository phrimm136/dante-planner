# 134 planner-id-policy-strict-with-migrations
epic: none · pr: none

## Decisions
- @planner @ids @validation — Every entity id in planner content is validated against the game-data registry on every save, draft and publish alike, on both the frontend and the backend, after a normalization step that applies an id-migration table: renamed ids are rewritten and retired ids are dropped, the way keyword renames and drops already work. The data pipeline is trusted to produce correct game data, so an id that is neither known nor listed in the table is an error, and the table is what keeps a data release from making existing planners unsavable (gift `9247` was once retired without one).
  REJECTED: validating references only on publish — lets drafts accumulate ids no page can render and moves the failure to the moment of sharing.
  REJECTED: accepting unknown ids with warnings — a new warnings channel through the API and UI for an outcome the migration table resolves at the source.
- @planner @ids @agreement — The frontend and backend agree on one definition each of: the valid id sets (the `static` data commit both deploy), the id-migration table, where normalization runs (frontend on load, backend on write before validation, both idempotent), each field's id format including the gift enhancement band, the season whose data validates a planner (derived from the planner's own version, never hardcoded), and the error codes; parity tests hold each pair together.
- @planner @ids @table — The table is read from `static/data/idMigrations.json` and an absent file means an empty table, so this repo ships the normalization before the pipeline emits the file; emitting it from the data pipeline on each release is follow-up work in the `static` repository.
  REJECTED: waiting for the pipeline before shipping — the strict validation, season derivation, partial import and category-only change do not depend on the table's contents.
- @planner @import — Import validates each planner independently and saves the valid ones, reporting each skipped planner and its reason to the user.
  REJECTED: all-or-nothing import — one stale planner blocks a whole backup restore.
- @planner @category — A change of category alone does not re-validate stored content: the content did not change, and re-validation is what turned a data release into a failure on unrelated edits.

## Takeaway
- takeaway: strict validation is only livable when every data release ships the map from the ids it retired to what replaced them; the map, not leniency, is what keeps old documents saveable.
