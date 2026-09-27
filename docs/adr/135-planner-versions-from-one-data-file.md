# 135 planner-versions-from-one-data-file
epic: none · pr: none

## Decisions
- @planner @versions — Every planner version value (the content schema version, the Mirror Dungeon season list, the Refracted Railway version list) is read by the frontend and the backend from one hand-written file, `static/data/plannerVersions.json`, and no code carries its own copy; the planner config endpoint, which published the backend's copy, is removed. The two sides held separate copies (a frontend constant, backend properties) kept in step by a sync script that had silently stopped running, so nothing held them together, and the endpoint had no frontend caller.
  REJECTED: publishing the backend's values through the endpoint — adds a network request to the editor and still leaves the backend's own copy hand-maintained.
  REJECTED: repairing the sync script and running it in CI — keeps two copies plus a generator, and the generator's silent breakage is the failure being removed.
  REJECTED: deriving the season list from the per-season data folders — covers the season list but not the schema version or the Refracted Railway versions, which have no data folder, so the values would still come from two mechanisms.
- @planner @versions — The current Mirror Dungeon season is the last element of the season list, which is strictly ascending; both loaders reject a list that is not.
  REJECTED: a separate current-season field — two values that must agree, which is the drift this file exists to remove.

## Takeaway
- takeaway: a value two deployables must agree on is read from one file both ship, never copied and kept in sync.
