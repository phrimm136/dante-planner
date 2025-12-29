# Local Save Learning Findings

## What Was Easy

- Phase-based architecture (data → logic → UI → edge cases) created natural stopping points
- Existing storage.ts foundation meant thin wrapper layer, no storage logic rewrite needed
- Zod schema patterns from NoteEditorSchemas made validation structure familiar
- Promise-caching pattern for deviceId elegantly solved async timing issues
- Set serialization helpers kept concerns clean and reusable

## What Was Challenging

- Metadata preservation timing: createdAt needed refs and careful sequencing to avoid overwrites
- Planner construction duplicated in hook and page - tight coupling emerged late
- Dirty state detection via JSON.stringify is performance-heavy for large states
- Hook API incomplete: usePlannerAutosave didn't expose manual save method
- IndexedDB cursor access in listPlanners bypassed storage abstraction

## Key Learnings

- Hooks managing internal state must expose operations to prevent consumer duplication
- Schema versioning (PLANNER_SCHEMA_VERSION=1) from the start prevents migration debt
- Promise caching prevents duplicate async calls during rapid state updates
- Timestamps like createdAt require ref-based logic to survive serialization cycles
- Spec ambiguities surface during integration, not during research phase
- Storage abstraction layering matters - bypassing creates maintenance burden

## Spec-Driven Process Feedback

- Research.md mappings were accurate but didn't specify where construction logic lives
- Plan.md order worked but needs "define public APIs before implementation" checkpoint
- Instructions.md testing was thorough but missed performance metrics for large states
- Code review after all steps caught API issues late - phase 2 review would help

## Pattern Recommendations

- Document hook API completeness: "Does hook export all operations it performs?"
- Create reusable timestamp-preservation pattern for IndexedDB operations
- Enforce "all storage access through single interface" pattern
- Serialization helpers belong in lib/utils, not in schemas file
- Document shallow comparison as alternative to JSON.stringify for dirty checks

## Next Time

- Define hook APIs in plan phase before implementation
- Extract shared utilities (createSaveablePlanner) before using in multiple places
- Add performance review checkpoint for large state operations
- Plan.md should list "what each file can access" to prevent abstraction leakage
- Test hook APIs with actual page during phase 3 kickoff, not after full implementation
