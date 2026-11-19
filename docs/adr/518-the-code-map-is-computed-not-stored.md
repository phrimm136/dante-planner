# 518 the-code-map-is-computed-not-stored
epic: none · pr: none

## Decisions
- @tooling @map — Where a frontend route's code lives is answered by one CLI, `map`, that computes each answer on demand from `frontend/src/lib/router.tsx`, the import graph under `frontend/src` and the `*Controller.java` mappings. It stores nothing. An agent that edits an unfamiliar page spends many searches finding where its code is before the first edit, and a hand-written feature map does not help for long: nothing has to read it and nothing has to update it. Re-check: `map doctor` exits 0 and reports 0 imports that resolve to no file; the whole map builds in under one second (`time map list`).
  REJECTED: a stored map file per feature, kept current by a hook or a scheduled agent — it states a conclusion that goes stale without a signal.
  REJECTED: listing every file a route reaches — a route reaches a median of about 200 of the 1,231 source files, because every page pulls in the shell. `map show` therefore splits the files into those the route alone reaches, those it shares with at most four routes, and a count of the rest.
  REJECTED: a TypeScript-compiler graph — exact, but it needs `node_modules` in every worktree and seconds per call; the regex graph needs nothing and `doctor` reports what it could not resolve.
  INVARIANT: `map` never writes a file — enforced by a test that runs every command and compares the fixture tree before and after.
  NON-GOAL: HTTP methods on the frontend side of an endpoint, and paths assembled at run time — the frontend side is found by the `/api/...` text in a file.
  NON-GOAL: sharing a graph with `checks changed`, which asks vitest for the tests a change reaches.

## Takeaway
- takeaway: a map that is written down needs a reader and a writer to stay alive; a map that is computed needs neither.
