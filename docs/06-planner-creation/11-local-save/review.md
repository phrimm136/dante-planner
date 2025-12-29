# Local Save Code Quality Review

## Verdict: NEEDS WORK

## Spec-Driven Compliance

- Spec-to-Code Mapping: Mostly followed, but save button duplicates planner construction logic
- Spec-to-Pattern Mapping: Followed for IndexedDB, Zod validation, Set serialization
- Technical Constraints: SSR safety, debounce, Zod validation all respected
- Execution Order: Plan.md phases 1-4 followed correctly
- Research alignment: Matches storage, hooks, and dialog patterns

## What Went Well

- Comprehensive Zod schemas with strict mode and ID validation patterns
- Set serialization helpers cleanly separate concerns
- Promise caching pattern prevents deviceId race conditions
- Draft recovery dialog correctly filters by status='draft'
- createdAt preservation uses ref to maintain original timestamp

## Code Quality Issues

- [HIGH] Duplicated planner construction: usePlannerAutosave has createSaveablePlanner but PlannerMDNewPage rebuilds entire structure for manual save. Schema changes require two updates.
- [HIGH] Redundant deviceId fetch: Save handler fetches deviceId independently despite autosave hook already managing it. Creates duplicate async calls.
- [HIGH] Hook API incomplete: usePlannerAutosave doesn't expose manual save. Page must duplicate serialization logic.
- [MEDIUM] Direct IndexedDB cursor: listPlanners bypasses storage.ts abstraction for iteration.
- [MEDIUM] Misplaced helpers: serializeSets/deserializeSets in schemas file instead of utils.
- [MEDIUM] Expensive dirty check: JSON.stringify on every state change for comparison.
- [LOW] Manual note serialization loop duplicates serializeSets pattern.

## Technical Debt Introduced

- Code duplication: createSaveablePlanner logic exists in two places
- Incomplete hook API: Manual save requires page to reimplement serialization
- Abstraction leak: listPlanners directly uses IndexedDB cursors
- Performance gap: No optimization for expensive serialization operations

## Backlog Items

- Extract createSaveablePlanner to shared utility (lib/plannerUtils.ts)
- Add savePlanner(status) method to usePlannerAutosave hook
- Move serialization helpers from PlannerSchemas.ts to lib/plannerSerializationUtils.ts
- Add unit tests for usePlannerStorage, usePlannerAutosave, and serialization helpers
- Optimize dirty checking with shallow comparison instead of JSON.stringify
