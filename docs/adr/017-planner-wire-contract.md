# 017 planner-wire-contract
epic: none · pr: none

## Decisions
- @publish @api — Publishing is a single content-carrying request that uploads and toggles atomically; unpublishing stays a lightweight toggle. REJECTED: sync-then-toggle as two requests — it spends two sequential cross-region round trips on one user action, and leaves a window where the document is published at stale content.
- @dto @column-rename — Storage column renames stay internal; the JSON wire names are frozen. The redundant saved-at timestamp is dropped and its DTO field is served from last-modified-at, which becomes the save time once counter and moderation writes no longer touch the content row. REJECTED: renaming wire names to match storage — it breaks the client for a purely internal change. REJECTED: keeping both timestamps — they become the same value after the split.
- @publish @idempotency @deploy — Publish and bookmark become state-targeted and therefore idempotent, with the legacy toggle handler retained and marked deprecated until measured usage reaches zero. REJECTED: removing the toggle at frontend deploy — deploy time is not adoption time, and this application is local-first with long-lived tabs, so a hashed bundle from before the deploy would 404 on its own publish button.

## Takeaway
- takeaway: in a local-first application the deployed frontend and the running frontend are different populations, so any wire change needs a deprecation window measured in observed usage rather than in elapsed time since the deploy.
