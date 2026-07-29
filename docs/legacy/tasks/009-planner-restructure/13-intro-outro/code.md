# Code: Intro/Outro Notes with Empty Note Visibility

## What Was Done
- Created shared `isNoteEmpty()` utility with recursive node traversal (handles null, empty doc, empty paragraph, whitespace-only, structural leaves)
- Bumped planner schema version from 1 to 2 in frontend constants, backend properties, and entity default
- Extended `createDefaultSectionNotes()` with intro/outro keys
- Added `PlannerService.setSchemaVersion()` on update path for v1→v2 lazy upgrade
- Added intro/outro NoteEditors to editor (always visible), guide mode (hidden when empty), and tracker mode (hidden when empty)
- Added empty note hiding for all 6 existing section notes and 15 floor notes in guide mode
- Refactored SectionNoteDialog and FloorNoteDialog to use shared `isNoteEmpty()`
- Added i18n keys for "Introduction" and "Closing Notes" in EN/KR/JP/CN
- Fixed v1 plan crash: merged defaults into sectionNotes load paths (store + edit page)

## Files Changed

### New
- `frontend/src/lib/noteUtils.ts`
- `frontend/src/lib/__tests__/noteUtils.test.ts`

### Modified — Frontend
- `frontend/src/lib/constants.ts` (PLANNER_SCHEMA_VERSION 1→2)
- `frontend/src/stores/usePlannerEditorStore.tsx` (createDefaultSectionNotes + load path merge)
- `frontend/src/routes/PlannerMDEditPage.tsx` (load path merge for existing planners)
- `frontend/src/components/planner/PlannerMDEditorContent.tsx` (intro/outro NoteEditors)
- `frontend/src/components/plannerViewer/GuideModeViewer.tsx` (intro/outro + empty note hiding)
- `frontend/src/components/plannerViewer/TrackerModeViewer.tsx` (intro/outro inline)
- `frontend/src/components/plannerViewer/FloorGalleryTracker.tsx` (empty floor note hiding)
- `frontend/src/components/common/SectionNoteDialog.tsx` (use shared isNoteEmpty)
- `frontend/src/components/plannerViewer/FloorNoteDialog.tsx` (use shared isNoteEmpty)

### Modified — Backend
- `backend/src/main/resources/application.properties` (schema-version=2, note count comment)
- `backend/src/main/java/org/danteplanner/backend/entity/Planner.java` (default=2)
- `backend/src/main/java/org/danteplanner/backend/service/PlannerService.java` (setSchemaVersion on update)

### Modified — i18n
- `static/i18n/{EN,KR,JP,CN}/planner.json` (introduction, closingNotes keys)

### Modified — Tests (schemaVersion 1→2)
- 11 test files updated (PlannerSchemas, usePlannerConfig, usePlannerSaveAdapter, usePlannerSyncAdapter, PlannerMDEditorContent, PlannerMDEditPage, PlannerMDDetailPage, PlannerDetailHeader, PlannerViewer, TrackerModeViewer)

## Verification Results
- Phase 1 (Data Layer): pass — `yarn tsc` + `./gradlew compileJava` clean
- Phase 2+3 (Interface + Refactor): pass — `yarn tsc` clean
- Phase 4 (i18n): pass — all 4 JSON files valid
- Phase 5 (Tests): pass — 990/990 tests pass (9 noteUtils unit tests)
- Code Review: ACCEPTABLE after fixes (H1, H2, H3, M4 resolved)

## Issues & Resolutions
- v1 plan crash (`sectionNotes.intro` undefined) → merged `createDefaultSectionNotes()` into both load paths (store + PlannerMDEditPage)
- Duplicate load path missed in plan.md → PlannerMDEditPage.tsx:94 had its own sectionNotes transformation outside the store
- `isNodeEmpty` treated structural leaf nodes (hardBreak) as empty → added type check to return false for non-text/non-paragraph leaves
- TrackerModeViewer inconsistent guard pattern → aligned with GuideModeViewer's double-gate
