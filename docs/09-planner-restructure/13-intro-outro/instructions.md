# Task: Intro/Outro Notes with Empty Note Visibility

## Description

Add "Introduction" and "Closing Notes" sections to the planner. These are tiptap-based notes rendered at the top and bottom of the plan body, respectively. They follow the same data model as existing section notes (stored in `sectionNotes` Record with keys `intro` and `outro`).

This requires bumping the planner schema version from 1 to 2. Migration uses lazy upgrade: plans upgrade to v2 on next save. Frontend handles version branching for v1 plans (no intro/outro rendering).

Additionally, all empty notes across guide mode and tracker mode viewers should be hidden (not rendered). "Empty" includes: null/undefined content, empty doc, single empty paragraph, and whitespace-only text nodes.

### Intro/Outro Behavior
- **Editor**: Always visible with placeholder, regardless of content. Intro renders above the first section (Deck Builder). Outro renders below the last section (Floor Themes), before the save button.
- **Guide mode**: Rendered inline with their own section title ("Introduction" / "Closing Notes"). Hidden entirely (title + note) when empty.
- **Tracker mode**: Rendered inline (not via dialog). Hidden entirely when empty. No "show notes" button needed since the note IS the section content.

### Empty Note Hiding
- **Guide mode**: All inline NoteEditor components (6 section notes + 15 floor notes + intro/outro) are hidden when empty. Only the NoteEditor is hidden, not the parent section's other components (deck builder, buff picker, etc.).
- **Tracker mode**: Intro/outro inline sections hidden when empty. Existing "show notes" buttons and SectionNoteDialog behavior remain untouched. FloorNoteDialog behavior remains untouched.
- **Editor**: No hiding. All notes always visible.

### Schema Version
- `PLANNER_SCHEMA_VERSION` bumps from 1 to 2.
- Backend sets `schemaVersion=2` on save/update.
- Frontend detects v1 plans (missing intro/outro keys) and skips intro/outro rendering.
- No Flyway data migration. Plans upgrade lazily on next save.

## Research
- Existing `isEmpty` logic in `SectionNoteDialog.tsx:38-40` and `FloorNoteDialog.tsx:34-36` — must be extracted to shared util
- Tiptap empty states: empty doc (`content: []`), single empty paragraph (`[{type:'paragraph'}]`), whitespace-only text nodes
- `NoteEditor.tsx:284` uses Tiptap's runtime `editor.isEmpty` — different from serialized JSON check
- `PlannerContentValidator.java` does NOT whitelist sectionNotes keys — no validation changes needed
- `PlannerContentSanitizer.java` iterates all sectionNotes entries generically — no changes needed
- `createDefaultSectionNotes()` in `usePlannerEditorStore.tsx:76-89` — factory to extend
- How `visibleSections` works differently in editor (numeric counter `>=`) vs viewer (boolean array `[]`)

## Scope
- `frontend/src/stores/usePlannerEditorStore.tsx` — createDefaultSectionNotes, state init
- `frontend/src/components/common/SectionNoteDialog.tsx` — existing isEmpty logic
- `frontend/src/components/plannerViewer/FloorNoteDialog.tsx` — existing isEmpty logic (duplicate)
- `frontend/src/components/plannerViewer/GuideModeViewer.tsx` — inline note rendering
- `frontend/src/components/plannerViewer/TrackerModeViewer.tsx` — dialog-based notes, "show notes" buttons
- `frontend/src/components/plannerViewer/FloorGalleryTracker.tsx` — floor note rendering in guide mode
- `frontend/src/routes/PlannerMDEditorContent.tsx` — editor note rendering
- `frontend/src/components/noteEditor/NoteEditor.tsx` — tiptap editor component
- `frontend/src/types/NoteEditorTypes.ts` — NoteContent type
- `frontend/src/schemas/PlannerSchemas.ts` — Zod schemas (sectionNotes is `z.record`)
- `frontend/src/lib/constants.ts` — PLANNER_SCHEMA_VERSION
- `frontend/src/components/common/PlannerSection.tsx` — section wrapper with optional onViewNotes
- `backend/src/main/java/org/danteplanner/backend/controller/PlannerController.java` — schemaVersion on save
- `backend/src/main/java/org/danteplanner/backend/entity/Planner.java` — schemaVersion default
- `backend/src/main/java/org/danteplanner/backend/validation/PlannerContentValidator.java` — verify no key whitelist
- `static/i18n/EN/planner.json` — i18n keys
- `static/i18n/KR/planner.json` — i18n keys
- `static/i18n/JP/planner.json` — i18n keys
- `static/i18n/CN/planner.json` — i18n keys

## Target Code Area
- `frontend/src/lib/noteUtils.ts` (new) — shared `isNoteEmpty()` util with whitespace handling
- `frontend/src/lib/constants.ts` (modify) — bump PLANNER_SCHEMA_VERSION to 2
- `frontend/src/stores/usePlannerEditorStore.tsx` (modify) — add intro/outro to createDefaultSectionNotes
- `frontend/src/routes/PlannerMDEditorContent.tsx` (modify) — add intro/outro NoteEditors
- `frontend/src/components/plannerViewer/GuideModeViewer.tsx` (modify) — add intro/outro sections, hide empty notes
- `frontend/src/components/plannerViewer/TrackerModeViewer.tsx` (modify) — add intro/outro inline sections
- `frontend/src/components/plannerViewer/FloorGalleryTracker.tsx` (modify) — hide empty floor notes
- `frontend/src/components/common/SectionNoteDialog.tsx` (modify) — use shared isNoteEmpty
- `frontend/src/components/plannerViewer/FloorNoteDialog.tsx` (modify) — use shared isNoteEmpty
- `backend/src/main/java/org/danteplanner/backend/controller/PlannerController.java` (modify) — set schemaVersion=2 on save
- `static/i18n/*/planner.json` (modify) — add "Introduction" / "Closing Notes" keys

## System Context (Senior Thinking)
- Feature domain: Planner (Mirror Dungeon / Refracted Railway)
- Core files: `PlannerMDEditorContent.tsx` (editor), `GuideModeViewer.tsx` (guide view), `TrackerModeViewer.tsx` (tracker view), `usePlannerEditorStore.tsx` (state), `PlannerSchemas.ts` (validation)
- Cross-cutting concerns: i18n (4 languages), Zod validation (frontend), Jakarta validation (backend), Zustand state management, tiptap rich text editor

## Impact Analysis
- Files being modified:
  - `PlannerMDEditorContent.tsx` (High impact — main editor page)
  - `GuideModeViewer.tsx` (Medium impact — read-only viewer)
  - `TrackerModeViewer.tsx` (Medium impact — tracker viewer)
  - `usePlannerEditorStore.tsx` (High impact — shared planner state)
  - `constants.ts` (Medium impact — schema version used across save/load paths)
  - `PlannerController.java` (High impact — API endpoint)
- What depends on these files:
  - `usePlannerEditorStore` is consumed by editor, save hooks, sync adapter
  - `PLANNER_SCHEMA_VERSION` is used in save/sync/fork flows
  - `GuideModeViewer` and `TrackerModeViewer` are leaf components (low ripple)
- Potential ripple effects:
  - Schema version bump affects `usePlannerSave.ts`, `usePlannerSyncAdapter.ts`, `usePlannerFork.ts`
  - createDefaultSectionNotes change affects store initialization and content loading
- High-impact files to watch: `usePlannerEditorStore.tsx`, `PlannerController.java`

## Risk Assessment
- **v1 plans missing intro/outro**: Frontend must handle gracefully via version branching. If intro/outro keys are undefined, skip rendering. Do NOT crash.
- **isEmpty definition drift**: Three current locations (SectionNoteDialog, FloorNoteDialog, NoteEditor runtime check) define "empty" differently. Shared util must be the single source of truth.
- **Whitespace handling**: The new `isNoteEmpty()` must strip whitespace from text nodes to match the "whitespace = empty" requirement. Neither current isEmpty implementation does this.
- **Backend schemaVersion on save**: Must ensure ALL save paths (create, update, fork) set schemaVersion=2. Check PlannerController endpoints.
- **Backward compatibility**: v2 frontend viewing v1 plans must work. v1 frontend (if any cached clients) viewing v2 plans should not crash — extra sectionNotes keys are ignored by the existing Record structure.

## Testing Guidelines

### Manual UI Testing

#### Editor — Intro/Outro
1. Navigate to planner editor (create new or edit existing)
2. Verify "Introduction" note editor appears above the Deck Builder section
3. Verify "Closing Notes" note editor appears below the Floor Themes section, before the save button
4. Type text in Introduction, verify it saves
5. Type text in Closing Notes, verify it saves
6. Clear all text from both, verify they remain visible (editor never hides)

#### Guide Mode — Empty Note Hiding
7. Create a plan with only some notes filled (e.g., deckBuilder and floor-0)
8. Open guide mode viewer
9. Verify filled notes (deckBuilder, floor-0) are visible
10. Verify empty notes are NOT rendered (no empty NoteEditor components)
11. Verify section components (DeckBuilder, StartBuff, etc.) still render regardless of note state
12. Add Introduction text, save, reopen guide mode — verify Introduction section with title appears
13. Clear Introduction text, save, reopen guide mode — verify Introduction section and title are gone

#### Tracker Mode — Intro/Outro + Existing Behavior
14. Open tracker mode for a plan with Introduction text
15. Verify Introduction section renders inline at the top
16. Open tracker mode for a plan WITHOUT Introduction text
17. Verify no Introduction section appears
18. Verify all "show notes" buttons on sections (DeckBuilder, StartBuff, etc.) still work
19. Click "show notes" on a section with empty note — verify dialog opens with placeholder text

#### v1 Plan Compatibility
20. Open a plan that was never saved after this update (schemaVersion=1)
21. Verify guide mode renders without intro/outro sections (no crash)
22. Verify tracker mode renders without intro/outro sections (no crash)
23. Edit and save the v1 plan
24. Verify it now has schemaVersion=2
25. Verify intro/outro are available in editor

### Automated Functional Verification
- [ ] `isNoteEmpty()` returns true for null content
- [ ] `isNoteEmpty()` returns true for `{ type: 'doc', content: [] }`
- [ ] `isNoteEmpty()` returns true for `{ type: 'doc', content: [{ type: 'paragraph' }] }`
- [ ] `isNoteEmpty()` returns true for `{ type: 'doc', content: [{ type: 'paragraph', content: [{ type: 'text', text: '   ' }] }] }`
- [ ] `isNoteEmpty()` returns false for `{ type: 'doc', content: [{ type: 'paragraph', content: [{ type: 'text', text: 'hello' }] }] }`
- [ ] `createDefaultSectionNotes()` includes `intro` and `outro` keys
- [ ] Schema version constant equals 2
- [ ] Guide mode: NoteEditor not rendered when note is empty
- [ ] Guide mode: NoteEditor rendered when note has content
- [ ] Tracker mode: intro/outro not rendered when empty
- [ ] Editor: intro/outro always rendered regardless of content

### Edge Cases
- [ ] Plan with schemaVersion=1 (no intro/outro keys): Guide/tracker mode render without crash
- [ ] Plan with schemaVersion=2 but empty intro/outro: Sections hidden in viewer, shown in editor
- [ ] Note with only whitespace: Treated as empty, hidden in viewer
- [ ] Note with mixed whitespace and empty paragraphs: Treated as empty
- [ ] Forked plan: Intro/outro content carries over to fork
- [ ] Plan sync: Intro/outro sync correctly between tabs

### Integration Points
- [ ] Save flow: schemaVersion set to 2 on all save paths (create, update)
- [ ] Fork flow: intro/outro included in forked plan content
- [ ] Sync adapter: schemaVersion=2 plans sync correctly
- [ ] Export/import: Extra sectionNotes keys don't break import of v2 plans
