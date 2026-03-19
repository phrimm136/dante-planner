# Research: Intro/Outro Notes with Empty Note Visibility

## Spec Ambiguities
- None. All requirements clarified during arch-research conversation.

## Schema Version Flow (Critical Discovery)
- schemaVersion is **server-controlled**, not client-set
- Flow: `application.properties` (`planner.schema-version=1`) → `PlannerController.getConfig()` → `/api/planner/md/config` response → frontend `usePlannerConfig()` → `usePlannerSave` payload
- `Planner.java` entity: `@Builder.Default private Integer schemaVersion = 1` — used when builder creates new entities
- `PlannerService.createPlanner()` does NOT explicitly set schemaVersion — relies on entity default
- **Backend change**: Update `application.properties` from `planner.schema-version=1` to `planner.schema-version=2`
- **Entity change**: Update `Planner.java` default from `1` to `2`
- **No controller/service code changes needed** — the config endpoint already serves the property value

## createEmptyNoteContent() Default State
- Located in `NoteEditorSchemas.ts:62-73`
- Creates `{ content: { type: 'doc', content: [{ type: 'paragraph' }] } }`
- This is a **single empty paragraph** — the `isNoteEmpty()` util must treat this as empty

## Spec-to-Code Mapping

### 1. Shared isNoteEmpty Util (NEW: `lib/noteUtils.ts`)
- Extract from `SectionNoteDialog.tsx:38-40` and `FloorNoteDialog.tsx:34-36`
- Must handle: null/undefined content, empty doc `[]`, single empty paragraph, whitespace-only text
- Consumers: GuideModeViewer, FloorGalleryTracker, TrackerModeViewer, SectionNoteDialog, FloorNoteDialog

### 2. createDefaultSectionNotes Extension (`usePlannerEditorStore.tsx:76-89`)
- Add `intro: createEmptyNoteContent()` and `outro: createEmptyNoteContent()` to the factory
- Store initialization at line 224 uses this factory — automatically picks up new keys

### 3. Editor Intro/Outro (`PlannerMDEditorContent.tsx`)
- Intro: Before `visibleSections >= 1` block (line 738). Always visible, no progressive reveal gate.
- Outro: After floor themes section (line 942), before save button (line 945). Always visible.
- Uses existing `handleSectionNoteChange('intro', content)` pattern

### 4. Guide Mode Intro/Outro + Empty Hiding (`GuideModeViewer.tsx`)
- Intro: Before section 0 (line 55). Wrapped in `PlannerSection` with title. Hidden when `isNoteEmpty()`.
- Outro: After section 6 (line 254). Same pattern.
- All 6 existing NoteEditors (lines 82, 108, 134, 167, 207, 233): Wrap each in `!isNoteEmpty()` check
- v1 branching: Check `sectionNotes.intro` existence before rendering

### 5. Guide Mode Floor Note Hiding (`FloorGalleryTracker.tsx:52-57`)
- Wrap each floor NoteEditor in `!isNoteEmpty(sectionNotes[floorNoteKey])` check

### 6. Tracker Mode Intro/Outro (`TrackerModeViewer.tsx`)
- Intro: Before section 0 (line 139). Inline `NoteEditor` + `PlannerSection` wrapper. Hidden when empty.
- Outro: After section 5 (line 299), before SectionNoteDialogs. Same pattern.
- Do NOT modify lines 302-343 (SectionNoteDialog instances) or onViewNotes callbacks

### 7. SectionNoteDialog + FloorNoteDialog Refactor
- `SectionNoteDialog.tsx:38-40`: Replace inline isEmpty with `isNoteEmpty()` import
- `FloorNoteDialog.tsx:34-36`: Replace inline isEmpty with `isNoteEmpty()` import

### 8. Backend Config (`application.properties:81`)
- Change `planner.schema-version=1` to `planner.schema-version=2`

### 9. Entity Default (`Planner.java:54`)
- Change `private Integer schemaVersion = 1` to `private Integer schemaVersion = 2`

### 10. Frontend Constants (`constants.ts:573`)
- Change `PLANNER_SCHEMA_VERSION = 1` to `PLANNER_SCHEMA_VERSION = 2`

### 11. i18n Keys (4 files: `static/i18n/{EN,KR,JP,CN}/planner.json`)
- Add keys under `pages.plannerMD` namespace: `introduction` and `closingNotes`

## Pattern Enforcement

| New/Modified File | MUST Read First | Pattern to Copy |
|-------------------|-----------------|-----------------|
| `lib/noteUtils.ts` | `SectionNoteDialog.tsx`, `FloorNoteDialog.tsx` | isEmpty check logic |
| GuideModeViewer intro/outro | GuideModeViewer existing sections | `visibleSections[N] && <PlannerSection>` wrapping |
| TrackerModeViewer intro/outro | TrackerModeViewer section 0 | `visibleSections[0]` + `cn('transition-opacity...')` wrapping |
| Editor intro/outro | PlannerMDEditorContent existing NoteEditors | `<NoteEditor value={} onChange={} placeholder={} maxBytes={}>` |

## Existing Utilities

| Category | Location | Found |
|----------|----------|-------|
| Note creation | `NoteEditorSchemas.ts:62` | `createEmptyNoteContent()` — reuse |
| Class merging | `lib/utils.ts` | `cn()` — reuse for conditional classes |
| Note types | `types/NoteEditorTypes.ts` | `NoteContent` type — reuse |
| Section wrapper | `components/common/PlannerSection.tsx` | `PlannerSection` with optional `onViewNotes` — reuse |
| Note isEmpty | NONE | Must create `lib/noteUtils.ts` |

## Gap Analysis

**Missing:**
- `isNoteEmpty()` shared util
- intro/outro in createDefaultSectionNotes
- intro/outro rendering in all 3 views (editor, guide, tracker)
- i18n keys for intro/outro section titles
- Version branching in viewers for v1 plans

**Needs Modification:**
- `usePlannerEditorStore.tsx` — factory function
- `GuideModeViewer.tsx` — add sections + hide empty notes
- `FloorGalleryTracker.tsx` — hide empty floor notes
- `TrackerModeViewer.tsx` — add inline intro/outro
- `SectionNoteDialog.tsx` — use shared isEmpty
- `FloorNoteDialog.tsx` — use shared isEmpty
- `application.properties` — schema version
- `Planner.java` — entity default
- `constants.ts` — PLANNER_SCHEMA_VERSION

**Can Reuse:**
- `NoteEditor` component, `PlannerSection` wrapper, `createEmptyNoteContent()`, `cn()`, all existing patterns

## Technical Constraints
- `createEmptyNoteContent()` creates `[{ type: 'paragraph' }]` — isNoteEmpty MUST treat this as empty
- visibleSections in editor is numeric (`>=`), in viewers is boolean array (`[]`) — different gating patterns
- Intro/outro in editor have NO progressive reveal gate (always visible)
- Backend validator does NOT whitelist sectionNotes keys — no validation changes needed
- Backend sanitizer iterates all entries generically — no changes needed
- `UpsertPlannerRequest` does NOT include schemaVersion — it's entity-level, controlled by backend default + config
