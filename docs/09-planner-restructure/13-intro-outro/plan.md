# Plan: Intro/Outro Notes with Empty Note Visibility

## Research Correction
- research.md claimed "No controller/service code changes needed" for backend — **incorrect**
- `PlannerService.upsertPlanner()` update branch never sets `schemaVersion` on existing entities
- `@Builder.Default` only applies to new entities via builder, not DB-loaded entities
- Fix: Inject `@Value("${planner.schema-version}")` into PlannerService and call `planner.setSchemaVersion(...)` in update branch

## Execution Overview

| Phase | Description | Steps | Risk |
|-------|-------------|-------|------|
| 1 | Data Layer (util, constants, store, backend) | 6 | High — schema version affects save/sync/fork |
| 2 | Interface Layer (editor, viewers) | 4 | Medium — leaf rendering |
| 3 | Refactor (dialog isEmpty extraction) | 2 | Low |
| 4 | i18n | 1 | Low |
| 5 | Tests | 2 | Medium — existing tests have hardcoded schemaVersion: 1 |

## Dependency Analysis

### Ripple Effect Map
- `application.properties` (schema-version=2) → PlannerController.getConfig() serves 2 to frontend
- `Planner.java` (@Builder.Default=2) → createPlanner/importPlanners get default 2
- `PlannerService.upsertPlanner()` setSchemaVersion → v1 plans upgrade on save
- `constants.ts` (PLANNER_SCHEMA_VERSION=2) → usePlannerSyncAdapter fallback
- `createDefaultSectionNotes()` + intro/outro → store init → usePlannerSave serializes all keys via Object.entries
- `noteUtils.ts` (isNoteEmpty) → GuideModeViewer, FloorGalleryTracker, TrackerModeViewer, SectionNoteDialog, FloorNoteDialog

### High-Risk Modifications
- **PlannerService.java**: Must inject schemaVersion and set on update. Failure = v1 plans never upgrade.
- **usePlannerEditorStore.tsx**: Adding keys changes sectionNotes shape. Save serialization uses Object.entries (generic) — safe.
- **GuideModeViewer.tsx**: Most complex change. Must NOT accidentally hide parent section components.

## Execution Order

### Phase 1: Data Layer

- **Step 1.1**: Create `frontend/src/lib/noteUtils.ts` — `isNoteEmpty(note)` utility
  - Depends on: none
  - Enables: Phase 2, Phase 3
  - Must handle: undefined/null, empty doc, single empty paragraph, whitespace-only text
  - `isNoteEmpty(undefined)` must return `true` (v1 compatibility)

- **Step 1.2**: Bump `frontend/src/lib/constants.ts:573` — `PLANNER_SCHEMA_VERSION = 2`
  - Depends on: none

- **Step 1.3**: Extend `createDefaultSectionNotes` in `usePlannerEditorStore.tsx:76-89`
  - Add `intro: createEmptyNoteContent()` and `outro: createEmptyNoteContent()`
  - Depends on: none

- **Step 1.4**: Backend `application.properties:81` — `planner.schema-version=2`
  - Depends on: none

- **Step 1.5**: Backend `Planner.java:54` — `@Builder.Default private Integer schemaVersion = 2`
  - Depends on: none

- **Step 1.6**: Backend `PlannerService.java` — set schemaVersion on update path
  - Inject `@Value("${planner.schema-version}") private Integer schemaVersion`
  - In `upsertPlanner()` update branch: add `planner.setSchemaVersion(schemaVersion)`
  - Depends on: Step 1.4

### Phase 2: Interface Layer

- **Step 2.1**: Editor intro/outro in `PlannerMDEditorContent.tsx`
  - Intro: Before `visibleSections >= 1` (line 738). Always visible, no progressive reveal gate.
  - Outro: After floor themes (line 942), before save button (line 945). Always visible.
  - Pattern: Copy NoteEditor at line 739-744
  - Depends on: Step 1.3

- **Step 2.2**: Guide mode in `GuideModeViewer.tsx`
  - Import `isNoteEmpty` from `@/lib/noteUtils`
  - Intro: Before section 0 (line 55). `PlannerSection` with i18n title. Gated by key existence + `!isNoteEmpty()`
  - Outro: After section 6 (line 254). Same pattern.
  - All 6 existing NoteEditors (lines 82, 108, 134, 167, 207, 233): Add `!isNoteEmpty()` AND condition
  - Do NOT change `SECTION_COUNT` — intro/outro are not progressive-reveal sections
  - Depends on: Step 1.1, Step 4.1

- **Step 2.3**: Floor note hiding in `FloorGalleryTracker.tsx:52-57`
  - Import `isNoteEmpty`, wrap each floor NoteEditor in conditional
  - Depends on: Step 1.1

- **Step 2.4**: Tracker mode in `TrackerModeViewer.tsx`
  - Import `isNoteEmpty`, `NoteEditor`, `PlannerSection`
  - Intro: Before section 0 (line 139). Inline render with `cn('transition-opacity...')` pattern. Hidden when empty.
  - Outro: After section 5 (line 299), before SectionNoteDialogs (line 302).
  - Do NOT modify lines 302-343 (SectionNoteDialog instances) or onViewNotes callbacks
  - Depends on: Step 1.1, Step 4.1

### Phase 3: Refactor

- **Step 3.1**: `SectionNoteDialog.tsx:38-40` — Replace inline isEmpty with `isNoteEmpty()` import
  - Depends on: Step 1.1

- **Step 3.2**: `FloorNoteDialog.tsx:34-36` — Replace inline isEmpty with `isNoteEmpty()` import
  - Depends on: Step 1.1

### Phase 4: i18n

- **Step 4.1**: Add keys to `static/i18n/{EN,KR,JP,CN}/planner.json`
  - Under `pages.plannerMD` namespace: `"introduction"` and `"closingNotes"`
  - EN: "Introduction", "Closing Notes"
  - KR/JP/CN: Placeholder with English until translated
  - Depends on: none (can run in parallel with Phase 1)

### Phase 5: Tests

- **Step 5.1**: Create `frontend/src/lib/noteUtils.test.ts`
  - Unit tests for all `isNoteEmpty` edge cases (5 cases from instructions.md)
  - Depends on: Step 1.1

- **Step 5.2**: Update existing tests with `schemaVersion: 2`
  - Files: PlannerSchemas.test.ts, usePlannerSyncAdapter.test.ts, PlannerMDEditorContent.test.tsx, PlannerMDEditPage.test.tsx, PlannerMDDetailPage.test.tsx, PlannerDetailHeader.test.tsx, PlannerViewer.test.tsx, TrackerModeViewer.test.tsx, usePlannerSaveAdapter.test.ts, usePlannerConfig.test.ts
  - Only change where test expects current config version. Keep `1` in v1-compatibility tests.
  - Depends on: Step 1.2

## Verification Checkpoints

| After | Verify |
|-------|--------|
| Phase 1 | `yarn tsc` passes. Backend compiles. `createDefaultSectionNotes()` includes intro/outro. |
| Phase 2 | Editor: intro/outro render. Guide: empty notes hidden. Tracker: intro/outro inline. |
| Phase 3 | SectionNoteDialog/FloorNoteDialog unchanged behavior. |
| Phase 4 | No missing i18n key warnings in console. |
| Phase 5 | All tests pass. |

## Risk Mitigation

| Risk | Mitigation |
|------|------------|
| v1 plans crash on undefined `sectionNotes.intro` | `isNoteEmpty(undefined)` returns true. Guard with optional chaining or key existence. |
| Backend update path misses schemaVersion | Step 1.6 explicitly handles this. Verify via response check. |
| Editor intro position ambiguity | Intro before deckBuilder note (line 738), NOT inside progressive reveal block. |
| `SECTION_COUNT` in GuideModeViewer | Do NOT change. Intro/outro render immediately if non-empty. |

## Pre-Implementation Validation Gate

| Check | File | Verified? |
|-------|------|-----------|
| sectionNotes serialization is generic (Object.entries) | usePlannerSave.ts:188-191 | Yes (research) |
| No sectionNotes key whitelist in backend | PlannerContentValidator.java | Yes (research) |
| Generic sectionNotes iteration in sanitizer | PlannerContentSanitizer.java | Yes (research) |
| NoteContent type definition | NoteEditorTypes.ts | Yes (research) |
| PlannerService constructor for @Value injection | PlannerService.java | Verify at Step 1.6 |

## Rollback Strategy
- All changes are backward-compatible
- v2 plans with extra sectionNotes keys won't crash on v1 frontend (Record ignores unknown keys)
- Revert `application.properties` + `constants.ts` + entity default to restore v1 behavior
- No data migration needed in either direction
