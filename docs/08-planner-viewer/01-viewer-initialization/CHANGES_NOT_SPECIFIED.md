# Changes Not in Original Specification

This document tracks changes made during implementation that were not part of the original specification.

## 2025-01-11: Tracker Mode - Current Skill Count Editing

### Problem Identified
Original specification only showed planned skill counts from editor. No way to track current/actual skill counts during dungeon run.

### Solution Implemented
Added session-only current skill count tracking in tracker mode.

### Changes Made

#### New Component
- `/frontend/src/components/plannerViewer/SkillTrackerPanel.tsx`
  - Displays both planned counts (from planner) and current counts (tracker state)
  - Editable current count with +/- buttons
  - Visual comparison between planned vs current

#### New Hook
- `/frontend/src/hooks/useTrackerState.ts`
  - Session-only state management
  - `currentSkillCounts: Record<string, number>` - tracks current counts per identity
  - `updateCurrentSkillCount(identityId, count)` - updates current count
  - State resets on page refresh (not persisted)

#### Integration
- `TrackerModeViewer.tsx` uses SkillTrackerPanel instead of SkillReplacementSection
- Current counts stored in tracker session state, not planner data

### Rationale
During dungeon runs, users need to track how many skills they've actually replaced vs planned. Session-only storage is appropriate since this is temporary tracking data.

### Files Created (2)
1. `frontend/src/components/plannerViewer/SkillTrackerPanel.tsx`
2. `frontend/src/hooks/useTrackerState.ts`

### Files Modified (1)
1. `frontend/src/components/plannerViewer/TrackerModeViewer.tsx`

---

## 2025-01-11: Tracker Mode - Unified Floor Display

### Problem Identified
Original specification showed floor-by-floor sections. This was inefficient for tracker mode where users need to see all theme packs and gifts at once.

### Solution Implemented
Replaced floor-by-floor sections with two unified collections:
1. **ComprehensiveGiftGridTracker** - All floor gifts in single grid with highlighting/dimming
2. **HorizontalThemePackGallery** - All theme packs in horizontal scroll with done tracking

### Changes Made

#### New Components

**ComprehensiveGiftGridTracker.tsx**
- Shows ALL gifts from ALL floors in unified grid
- Highlights gifts when hovering corresponding theme pack
- Dims gifts when theme pack marked as done
- Floor-based logic: iterate floors, check if pack is done, dim all gifts from that floor

**HorizontalThemePackGallery.tsx**
- Single horizontal ScrollArea with all theme pack cards
- Each card shows: theme pack image, floor number, hover actions (done/notes)
- Done state stored per floor in tracker state
- Maps theme pack → floor index using `floorSelections.findIndex()`

**ThemePackTrackerCard.tsx**
- Card component for tracker mode
- Shows theme pack with floor number label
- Hover reveals icon-only action buttons (centered, no text):
  - CheckCircle2: Toggle done state
  - FileText: Open notes dialog
- `isDone` dims card image/text (opacity-50), NOT buttons
- Passes `onHoverChange` to parent for gift highlighting

#### Integration
- `TrackerModeViewer.tsx` renders ComprehensiveGiftGridTracker and HorizontalThemePackGallery
- `useTrackerState.ts` manages `doneMarks: Record<number, Set<string>>` (floor index → theme pack IDs)
- Hover state managed in TrackerModeViewer, passed down as `hoveredThemePackId`

### Rationale
Tracker mode is for dungeon runs where users need quick overview of all floors. Unified view is more efficient than scrolling through 15 separate floor sections. Hover linking helps users understand theme pack → gift relationships.

### Files Created (3)
1. `frontend/src/components/plannerViewer/ComprehensiveGiftGridTracker.tsx`
2. `frontend/src/components/plannerViewer/HorizontalThemePackGallery.tsx`
3. `frontend/src/components/plannerViewer/ThemePackTrackerCard.tsx`

### Files Modified (1)
1. `frontend/src/components/plannerViewer/TrackerModeViewer.tsx`

---

## 2025-01-11: Guide Mode - Floor Gallery Component

### Problem Identified
GuideModeViewer needed floor display but was using inline floor mapping logic, creating code duplication with TrackerModeViewer's original floor-by-floor approach.

### Solution Implemented
Extracted floor display logic into reusable `FloorGalleryTracker` component.

### Changes Made

#### New Component
- `/frontend/src/components/plannerViewer/FloorGalleryTracker.tsx`
  - Wraps floor-by-floor display in single PlannerSection
  - Maps through floors, renders FloorThemeGiftSection for each
  - Passes `hideHoverHighlight={true}` and `readOnly={true}` for viewer mode
  - Used only in GuideModeViewer (TrackerModeViewer uses unified collections)

### Rationale
Guide mode shows complete planner as-is, so floor-by-floor display is appropriate. Extracting to component reduces duplication and improves maintainability.

### Files Created (1)
1. `frontend/src/components/plannerViewer/FloorGalleryTracker.tsx`

### Files Modified (1)
1. `frontend/src/components/plannerViewer/GuideModeViewer.tsx`

---

## 2025-01-11: Viewer Empty State Messages

### Problem Identified
Empty state placeholders in viewer mode (GuideModeViewer, TrackerModeViewer) were showing editor-focused messages like:
- "시작 버프를 선택하세요" / "Click to select start buffs"
- "E.G.O 기프트를 선택하세요" / "Select E.G.O Gifts"

These are inappropriate for read-only viewer modes.

### Solution Implemented
Added `readOnly` prop pattern across all summary/viewer components to show viewer-appropriate empty state messages.

### Changes Made

#### 1. Translation Keys Added
Added `emptyState` section to all i18n files (KR, EN, JP, CN):

```json
{
  "emptyState": {
    "noStartBuffs": "선택된 시작 버프 없음",
    "noStartGifts": "선택된 시작 E.G.O 기프트 없음",
    "noEgoGifts": "선택된 E.G.O 기프트 없음",
    "noThemePack": "선택된 테마팩 없음",
    "noFloorGifts": "선택된 E.G.O 기프트 없음"
  }
}
```

#### 2. Components Modified

**Added `readOnly?: boolean` prop:**
- `/frontend/src/components/startBuff/StartBuffSection.tsx`
- `/frontend/src/components/startGift/StartGiftSummary.tsx`
- `/frontend/src/components/egoGift/EGOGiftObservationSummary.tsx`
- `/frontend/src/components/floorTheme/ThemePackViewer.tsx` (ThemePackPlaceholder)
- `/frontend/src/components/floorTheme/FloorGiftViewer.tsx`
- `/frontend/src/components/floorTheme/FloorThemeGiftSection.tsx`

**Conditional message rendering:**
```tsx
{readOnly
  ? t('pages.plannerMD.emptyState.noStartBuffs')
  : t('pages.plannerMD.selectStartBuffs')}
```

**Always uses viewer message (viewer-only component):**
- `/frontend/src/components/plannerViewer/ComprehensiveGiftGridTracker.tsx`

#### 3. Viewer Components Updated

**Pass `readOnly={true}` to summary components:**
- `/frontend/src/components/plannerViewer/GuideModeViewer.tsx`
  - StartBuffSection
  - StartGiftSummary
  - EGOGiftObservationSummary

- `/frontend/src/components/plannerViewer/TrackerModeViewer.tsx`
  - StartBuffSection
  - StartGiftSummary
  - EGOGiftObservationSummary

- `/frontend/src/components/plannerViewer/FloorGalleryTracker.tsx`
  - FloorThemeGiftSection → ThemePackPlaceholder, FloorGiftViewer

### Rationale
Viewer mode is read-only and should not prompt users to "click to select" items. The new messages ("No X selected") are more appropriate for displaying the current state without implying interactivity.

### Files Modified
**Translation Files (4):**
- `/static/i18n/KR/planner.json`
- `/static/i18n/EN/planner.json`
- `/static/i18n/JP/planner.json`
- `/static/i18n/CN/planner.json`

**Component Files (7):**
1. `frontend/src/components/startBuff/StartBuffSection.tsx`
2. `frontend/src/components/startGift/StartGiftSummary.tsx`
3. `frontend/src/components/egoGift/EGOGiftObservationSummary.tsx`
4. `frontend/src/components/plannerViewer/ComprehensiveGiftGridTracker.tsx`
5. `frontend/src/components/floorTheme/ThemePackViewer.tsx`
6. `frontend/src/components/floorTheme/FloorGiftViewer.tsx`
7. `frontend/src/components/floorTheme/FloorThemeGiftSection.tsx`

**Viewer Files (3):**
8. `frontend/src/components/plannerViewer/GuideModeViewer.tsx`
9. `frontend/src/components/plannerViewer/TrackerModeViewer.tsx`
10. `frontend/src/components/plannerViewer/FloorGalleryTracker.tsx`

---

## 2025-01-11: Remove Hover Highlight from Floor Sections (Earlier Session)

### Problem Identified
Floor sections in GuideModeViewer showed yellow hover highlights (`.selectable` class) when hovering over theme pack and gift viewers, which was inappropriate for read-only viewer mode.

### Solution Implemented
Added `hideHoverHighlight` prop pattern to decouple hover highlighting from disabled state.

### Changes Made

#### Components Modified
**Added `hideHoverHighlight?: boolean` prop:**
- `/frontend/src/components/floorTheme/FloorThemeGiftSection.tsx`
- `/frontend/src/components/floorTheme/FloorGiftViewer.tsx`
- `/frontend/src/components/floorTheme/ThemePackViewer.tsx` (ThemePackPlaceholder)

**Conditional selectable class:**
```tsx
className={cn(
  !hideHoverHighlight && 'selectable',
  !disabled ? 'cursor-pointer' : 'cursor-not-allowed opacity-50'
)}
```

#### Viewer Components Updated
- `/frontend/src/components/plannerViewer/FloorGalleryTracker.tsx`
  - Changed `disabled={true}` to `disabled={false}` (prevent dimming)
  - Added `hideHoverHighlight={true}` (remove hover border)

### Rationale
Separating `hideHoverHighlight` from `disabled` allows components to be non-interactive (no pane opening) without showing dimming or hover highlights, which is appropriate for viewer mode.

### Files Modified (4)
1. `frontend/src/components/floorTheme/FloorThemeGiftSection.tsx`
2. `frontend/src/components/floorTheme/FloorGiftViewer.tsx`
3. `frontend/src/components/floorTheme/ThemePackViewer.tsx`
4. `frontend/src/components/plannerViewer/FloorGalleryTracker.tsx`

---

## 2025-01-11: Unified `readOnly` Prop Pattern (Current Session)

### Problem Identified
Multiple props were being used inconsistently across components:
- `disabled` in NoteEditor and DeckBuilder
- `hideHoverHighlight` in floor sections
- Both `disabled` and `readOnly` in some components

This created confusion and inconsistent behavior across viewer components.

### Solution Implemented
Unified all components to use single `readOnly` prop pattern with consistent behavior:
- `readOnly={true}`: No editing, no hover highlights, normal opacity (readable)
- Removed `disabled` prop entirely
- Removed `hideHoverHighlight` prop (merged into `readOnly`)

### Changes Made

#### 1. Skill Exchange Section - Hover Highlighting Removal

**SkillReplacementSection.tsx**
- Added `readOnly?: boolean` prop
- Pass `readOnly` down to SinnerSkillCard
- Modal only renders when `!disabled` (prevents opening in guide mode)

**SinnerSkillCard.tsx**
- Added `readOnly?: boolean` prop
- Conditional `.selectable` class: `!readOnly && 'cursor-pointer selectable'`
- Removes yellow hover ring when readOnly

**GuideModeViewer.tsx**
- Pass `readOnly={true}` to SkillReplacementSection

#### 2. Summary Sections - Unified `readOnly` Pattern

**Removed `hideHoverHighlight`, replaced with `readOnly`:**
- `StartBuffSection.tsx`: `!readOnly && 'selectable cursor-pointer'`
- `StartGiftSummary.tsx`: `!readOnly && 'selectable cursor-pointer'`
- `EGOGiftObservationSummary.tsx`: `!readOnly && 'selectable cursor-pointer'`

**Floor Components - Removed `hideHoverHighlight` prop:**
- `FloorThemeGiftSection.tsx`: Removed `hideHoverHighlight` from interface and usage
- `FloorGiftViewer.tsx`: Changed `!hideHoverHighlight && 'selectable'` → `!readOnly && 'selectable'`
- `ThemePackViewer.tsx` (ThemePackPlaceholder): Same change

**FloorGalleryTracker.tsx**
- Removed `hideHoverHighlight={true}` (now uses `readOnly={true}`)

**GuideModeViewer.tsx & TrackerModeViewer.tsx**
- Removed `disabled={true}` from EGOGiftObservationSummary
- All components now only use `readOnly={true}`

#### 3. Deck Builder - `disabled` to `readOnly` Migration

**DeckBuilderSummary.tsx**
- Removed `disabled?: boolean` prop
- Only `readOnly?: boolean` remains
- Pass `readOnly` down to SinnerGrid
- ActionBar only renders when `!readOnly`

**SinnerGrid.tsx**
- Added `readOnly?: boolean` prop
- Pass `readOnly` down to each SinnerDeckCard

**SinnerDeckCard.tsx**
- Added `readOnly?: boolean` prop
- `onClick={readOnly ? undefined : () => onToggleDeploy(sinnerIndex)}`
- `style={{ cursor: readOnly ? 'default' : 'pointer' }}`
- Prevents deployment order editing when readOnly

**GuideModeViewer.tsx**
- Changed `disabled={true}` → `readOnly={true}` for DeckBuilderSummary

**DeckTrackerPanel.tsx**
- Changed `disabled={true}` → `readOnly={true}` for DeckBuilderSummary

#### 4. NoteEditor - Complete `disabled` to `readOnly` Migration

**NoteEditorTypes.ts**
- Removed `disabled?: boolean` prop
- Only `readOnly?: boolean` remains
- Comment: "Whether the editor is in read-only mode (disables editing and changes placeholder)"

**NoteEditor.tsx**
- Removed all references to `disabled`
- Changed to use `readOnly` throughout:
  - `editable: !readOnly && isFocused`
  - `handleClick`: `if (!readOnly && !isFocused)`
  - Toolbar: `visible={isFocused && !readOnly}`
  - Removed `opacity-50 cursor-not-allowed` (readOnly has normal opacity)
  - Byte counter: `{maxBytes && !readOnly && (...)}`

**Placeholder Logic**
```tsx
{!isFocused && editor?.isEmpty && (
  <div className="...">
    {readOnly
      ? t('pages.plannerMD.noteEditor.placeholderReadOnly')
      : (placeholder || t('pages.plannerMD.noteEditor.placeholder'))}
  </div>
)}
```

**All Viewer Components Updated**
- `GuideModeViewer.tsx`: All NoteEditor → `readOnly={true}`
- `TrackerModeViewer.tsx`: All NoteEditor → `readOnly={true}`
- `FloorGalleryTracker.tsx`: NoteEditor → `readOnly={true}`
- `FloorNoteDialog.tsx`: NoteEditor → `readOnly={true}`

#### 5. i18n - NoteEditor ReadOnly Placeholder

Added `placeholderReadOnly` key to all languages:
- **KR**: "작성된 노트가 없습니다"
- **EN**: "No notes written"
- **JP**: "ノートがありません"
- **CN**: "没有笔记"

#### 6. PlannerViewer - i18n Support

**PlannerViewer.tsx**
- Changed `useTranslation()` → `useTranslation(['planner', 'common'])`
- Guide Mode button: `t('pages.plannerMD.viewer.guideMode')`
- Tracker Mode button: `t('pages.plannerMD.viewer.trackerMode')`

**PlannerMDDetailPage.tsx**
- Untitled placeholder: `t('pages.plannerMD.untitled')`

**i18n Keys Added** (all 4 languages):
```json
{
  "pages.plannerMD": {
    "untitled": "제목 없음 / Untitled / 無題 / 无标题",
    "viewer": {
      "guideMode": "가이드 모드 / Guide Mode / ガイドモード / 指南模式",
      "trackerMode": "트래커 모드 / Tracker Mode / トラッカーモード / 跟踪模式"
    }
  }
}
```

### Rationale

**Why unify to `readOnly`:**
1. **Semantic clarity**: "readOnly" clearly indicates viewing mode vs editing mode
2. **No dimming**: Viewers need to read content, so opacity-50 is inappropriate
3. **Single prop**: Reduces prop proliferation and confusion
4. **Consistent pattern**: All components behave the same way

**Why remove `hideHoverHighlight`:**
- Same use case as `readOnly` (viewer mode)
- Redundant prop that just adds complexity
- `readOnly` is more semantic

**Why remove opacity from readOnly:**
- `disabled` traditionally means "cannot interact" (dimmed, grayed out)
- `readOnly` means "can view but not edit" (normal appearance)
- NoteEditor and other viewers need full readability

### Files Modified

**Type Definitions (1):**
1. `frontend/src/types/NoteEditorTypes.ts`

**Components (16):**
1. `frontend/src/components/noteEditor/NoteEditor.tsx`
2. `frontend/src/components/skillReplacement/SkillReplacementSection.tsx`
3. `frontend/src/components/skillReplacement/SinnerSkillCard.tsx`
4. `frontend/src/components/startBuff/StartBuffSection.tsx`
5. `frontend/src/components/startGift/StartGiftSummary.tsx`
6. `frontend/src/components/egoGift/EGOGiftObservationSummary.tsx`
7. `frontend/src/components/floorTheme/FloorThemeGiftSection.tsx`
8. `frontend/src/components/floorTheme/FloorGiftViewer.tsx`
9. `frontend/src/components/floorTheme/ThemePackViewer.tsx`
10. `frontend/src/components/deckBuilder/DeckBuilderSummary.tsx`
11. `frontend/src/components/deckBuilder/SinnerGrid.tsx`
12. `frontend/src/components/deckBuilder/SinnerDeckCard.tsx`
13. `frontend/src/components/plannerViewer/GuideModeViewer.tsx`
14. `frontend/src/components/plannerViewer/TrackerModeViewer.tsx`
15. `frontend/src/components/plannerViewer/FloorGalleryTracker.tsx`
16. `frontend/src/components/plannerViewer/FloorNoteDialog.tsx`

**Viewer Container (2):**
17. `frontend/src/components/plannerViewer/PlannerViewer.tsx`
18. `frontend/src/components/plannerViewer/DeckTrackerPanel.tsx`

**Routes (1):**
19. `frontend/src/routes/PlannerMDDetailPage.tsx`

**i18n Files (4):**
20. `static/i18n/KR/planner.json`
21. `static/i18n/EN/planner.json`
22. `static/i18n/JP/planner.json`
23. `static/i18n/CN/planner.json`

**Total Files Modified: 24**
# Changes Not Specified in Original Requirements

This document tracks all changes and additions made during implementation that were not explicitly specified in the original task requirements.

## 2026-01-11: Tracker Mode Deck Builder Full Functionality

### Context
User requested full deck builder functionality in tracker mode, equivalent to planner editor mode. All deck editing features needed to be accessible during gameplay tracking.

### Changes Made

#### 1. Removed `readOnly` Prop from Tracker Mode Deck Builder
**Files Modified:**
- `frontend/src/components/plannerViewer/DeckTrackerPanel.tsx`

**Change:** Removed `readOnly={true}` prop from `DeckBuilderSummary` component.

**Reason:** User required full deck editing functionality in tracker mode, not read-only display.

---

#### 2. Added Tracker Mode Support to DeckBuilderSummary
**Files Modified:**
- `frontend/src/components/deckBuilder/DeckBuilderSummary.tsx`

**Changes:**
- Added `trackerMode?: boolean` prop
- Added `onResetToInitial?: () => void` prop
- Changed `showEditDeck={!trackerMode}` to `showEditDeck={true}` to always show "Edit Deck" button
- Props passed through to `DeckBuilderActionBar`

**Reason:** Tracker mode needed to display "Edit Deck" button and support "Reset to Initial" functionality.

---

#### 3. Added Tracker Mode UI to DeckBuilderActionBar
**Files Modified:**
- `frontend/src/components/deckBuilder/DeckBuilderActionBar.tsx`

**Changes:**
- Added `trackerMode?: boolean` prop
- Added `onResetToInitial?: () => void` prop
- Added conditional "Reset to Initial" button when `trackerMode={true}`
- Both "Reset Order" and "Reset to Initial" buttons visible in tracker mode

**Reason:** Tracker mode needed additional reset functionality to restore planner's preset deployment.

---

#### 4. Expanded TrackerState to Include Equipment
**Files Modified:**
- `frontend/src/hooks/useTrackerState.ts`

**Changes:**
- Added `equipment: Record<string, SinnerEquipment>` to `TrackerState` interface
- Changed `deploymentOrder: string[]` to `deploymentOrder: number[]` (type fix)
- Changed `setDeploymentOrder` to `React.Dispatch<React.SetStateAction<number[]>>`
- Added `setEquipment: React.Dispatch<React.SetStateAction<Record<string, SinnerEquipment>>>`
- Added `initialEquipment` and `initialDeployment` parameters to `useTrackerState()` hook
- Updated `resetState()` to accept equipment and deployment parameters
- Removed `useEffect` cleanup (no longer needed)

**Reason:** Equipment can change during gameplay (sinner swaps), so tracker state needed to manage equipment dynamically.

---

#### 5. Implemented Full Deck Editing in TrackerModeViewer
**Files Modified:**
- `frontend/src/components/plannerViewer/TrackerModeViewer.tsx`

**Changes:**
- Added imports: `startTransition`, `toast`, `Dialog` components, `encodeDeckCode`/`decodeDeckCode`/`validateDeckCode`, `useIdentityListSpec`, `useEGOListSpec`, `DeckBuilderPane`, `DeckFilterState`
- Added state: `isDeckPaneOpen`, `importDialogOpen`, `pendingImport`, `deckFilterState`
- Initialized `deckFilterState` with correct structure:
  ```typescript
  {
    entityMode: 'identity',
    selectedSinners: new Set<string>(),
    selectedKeywords: new Set<string>(),
    searchQuery: '',
  }
  ```
- Added handlers: `handleDeckExport()`, `handleDeckImport()`, `handleImportConfirm()`, `handleResetOrder()`
- Passed handlers to `DeckTrackerPanel` and `DeckBuilderPane`
- Added `<DeckBuilderPane>` component with full props
- Added Deck Import Confirmation Dialog
- Changed `equipment={content.equipment}` to `equipment={trackerState.equipment}` (uses tracker state)
- Changed `deploymentOrder` to use `trackerState.deploymentOrder`

**Reason:** User required import/export, deck editing pane, and all deck builder functionality in tracker mode.

---

#### 6. Updated DeckTrackerPanel Props and Handlers
**Files Modified:**
- `frontend/src/components/plannerViewer/DeckTrackerPanel.tsx`

**Changes:**
- Updated props interface:
  - Added `setEquipment: React.Dispatch<React.SetStateAction<Record<string, SinnerEquipment>>>`
  - Added `setDeploymentOrder: React.Dispatch<React.SetStateAction<number[]>>`
  - Added `onEditDeck: () => void`
  - Added `onImport: () => void`
  - Added `onExport: () => void`
  - Added `onResetOrder: () => void`
  - Removed standalone `onSetDeploymentOrder`
- Updated `handleToggleDeploy()` to use `setDeploymentOrder` with functional update
- Updated `handleResetToPreset()` to call `onResetOrder()`
- Updated `handleMoveUp()` and `handleMoveDown()` to use `setDeploymentOrder` with functional update
- Removed temporary alert placeholders for import/export
- Props now passed directly to `DeckBuilderSummary`: `onImport`, `onExport`, `onEditDeck`

**Reason:** Handlers needed to be properly wired from TrackerModeViewer through DeckTrackerPanel to DeckBuilderSummary.

---

#### 7. Fixed DeckFilterState Initialization Type Error
**Files Modified:**
- `frontend/src/components/plannerViewer/TrackerModeViewer.tsx`

**Issue:** `DeckFilterState` requires `Set<string>` for filter fields, but was initialized with arrays `[]`.

**Fix:** Changed initialization to:
```typescript
{
  entityMode: 'identity',
  selectedSinners: new Set<string>(),
  selectedKeywords: new Set<string>(),
  searchQuery: '',
}
```

**Error Prevented:** `TypeError: Cannot read properties of undefined (reading 'has')` in IconFilter.tsx:80

---

#### 8. Added i18n Support for Tracker Mode
**Files Modified:**
- `static/i18n/EN/planner.json`
- `static/i18n/KR/planner.json`
- `static/i18n/JP/planner.json`
- `static/i18n/CN/planner.json`

**Keys Added:**

1. `pages.plannerMD.tracker.deckResetNote`:
   - EN: "Changes reset on page refresh"
   - KR: "변경사항은 페이지 새로고침 시 초기화됩니다"
   - JP: "変更はページの再読み込み時にリセットされます"
   - CN: "更改将在页面刷新时重置"

2. `deckBuilder.resetToInitial`:
   - EN: "Reset to Initial"
   - KR: "초기 설정으로 복원"
   - JP: "初期設定に戻す"
   - CN: "恢复初始设定"

**Reason:** UI text needed translation support for all languages.

---

### Implementation Principles Applied

1. **Session-only State**: All tracker changes (equipment, deployment, skill counts) reset on page refresh
2. **Initial Values from Planner**: Tracker state initialized from planner's saved data
3. **Reset to Preset**: "Reset to Initial" button restores planner's original equipment/deployment
4. **Full Deck Editor Access**: DeckBuilderPane accessible in tracker mode for equipment changes
5. **Import/Export Support**: Deck codes work in tracker mode (session-only)
6. **Type Safety**: Fixed type mismatches between state and component interfaces

---

### Data Flow

```
Initial Load:
  planner.content.equipment → useTrackerState(equipment) → trackerState.equipment

User Edits:
  DeckBuilderPane → setEquipment → trackerState.equipment (updated)

Reset to Initial:
  handleResetOrder() → setDeploymentOrder([]) → falls back to content.deploymentOrder

Page Refresh:
  trackerState reset → loads from planner.content again
```

---

### Files Created
None (all modifications to existing files)

### Files Modified
1. `frontend/src/components/plannerViewer/DeckTrackerPanel.tsx`
2. `frontend/src/components/deckBuilder/DeckBuilderSummary.tsx`
3. `frontend/src/components/deckBuilder/DeckBuilderActionBar.tsx`
4. `frontend/src/hooks/useTrackerState.ts`
5. `frontend/src/components/plannerViewer/TrackerModeViewer.tsx`
6. `static/i18n/EN/planner.json`
7. `static/i18n/KR/planner.json`
8. `static/i18n/JP/planner.json`
9. `static/i18n/CN/planner.json`

---

## Summary

Implemented full deck builder functionality in tracker mode, including equipment editing, import/export, deployment management, and "Reset to Initial" feature. All changes are session-only and reset on page refresh. Fixed type safety issues and added complete i18n support.

---

## 2026-01-11: Guide Mode Read-Only Enforcement & UI Polish

### Context
Guide mode should be fully read-only with no interactive elements. User requested blocking all popups (skill exchange, theme pack selector, floor gift selector) and polishing the UI to remove visual disabled states while still preventing interaction.

### Changes Made

#### 1. Blocked Skill Exchange Popup in Guide Mode
**Files Modified:**
- `frontend/src/components/skillReplacement/SinnerSkillCard.tsx`
- `frontend/src/components/skillReplacement/SkillReplacementSection.tsx`
- `frontend/src/components/plannerViewer/GuideModeViewer.tsx`

**Changes:**
- SinnerSkillCard: Added `onClick={readOnly ? undefined : onClick}` and `disabled={readOnly}`
- SkillReplacementSection: Added `!readOnly` check to modal rendering condition
- GuideModeViewer: Cleaned up duplicate `readOnly={true}` props

**Reason:** Guide mode should not allow opening skill exchange modal for editing.

---

#### 2. Blocked Theme Pack and Floor Gift Selectors in Guide Mode
**Files Modified:**
- `frontend/src/components/floorTheme/ThemePackViewer.tsx`
- `frontend/src/components/floorTheme/FloorGiftViewer.tsx`
- `frontend/src/components/floorTheme/FloorThemeGiftSection.tsx`

**Changes:**
- ThemePackViewer: Added `readOnly` prop, blocks onClick when `readOnly={true}`
- ThemePackPlaceholder: Added `readOnly` prop support
- FloorGiftViewer: Added `readOnly` prop, prevents onClick in both empty and filled states
- FloorThemeGiftSection: Removed `disabled` prop entirely, merged functionality into `readOnly`

**Reason:** Guide mode should not allow opening theme pack or floor gift selector panes.

---

#### 3. Removed Visual Disabled Styling from ReadOnly Components
**Files Modified:**
- `frontend/src/components/skillReplacement/SinnerSkillCard.tsx`
- `frontend/src/components/floorTheme/ThemePackViewer.tsx`
- `frontend/src/components/floorTheme/FloorGiftViewer.tsx`

**Changes:**
- Removed `cursor-not-allowed` styling
- Removed `opacity-50` styling
- Kept HTML `disabled` attribute for accessibility
- Kept `selectable` class only for interactive mode

**Reason:** ReadOnly components should look normal, just not respond to clicks. Visual disabled states (grayed out, special cursor) are removed for cleaner appearance.

---

#### 4. Consolidated disabled and readOnly Props
**Files Modified:**
- `frontend/src/components/floorTheme/FloorThemeGiftSection.tsx`
- `frontend/src/components/floorTheme/ThemePackViewer.tsx`
- `frontend/src/components/floorTheme/ThemePackPlaceholder.tsx`
- `frontend/src/components/floorTheme/FloorGiftViewer.tsx`

**Changes:**
- Removed `disabled` prop from all floor theme components
- Renamed `isThemePackDisabled` → `isThemePackReadOnly`
- Renamed `isGiftDisabled` → `isGiftReadOnly`
- Single `readOnly` prop now controls all non-interactive behavior

**Reason:** Simplified component API - one prop instead of two overlapping props.

---

#### 5. Moved Tracker Mode Current EA Badge Position
**Files Modified:**
- `frontend/src/components/skillReplacement/SinnerSkillCard.tsx`

**Changes:**
- Changed Current EA badge position from `-top-1 -left-1` (upper-left) to `-bottom-1 -right-1` (lower-right)
- Updated comment from `왼쪽 위` to `오른쪽 아래`
- Badge layout now: Planned EA (upper-right), Current EA (lower-right)

**Reason:** Better visual alignment - both EA indicators on the right side for easier comparison.

---

#### 6. Added Current EA Display to Skill Exchange Modal
**Files Modified:**
- `frontend/src/components/skillReplacement/SkillEADisplay.tsx`
- `frontend/src/components/skillReplacement/SkillExchangeModal.tsx`
- `frontend/src/components/plannerViewer/SkillTrackerPanel.tsx`

**Changes:**
- SkillEADisplay: Added `currentEA?: number` prop, displays second badge at lower-right when provided
- SkillExchangeModal:
  - Added `currentEA?: SkillEAState` prop
  - Pass `currentEA` to SkillEADisplay components
  - Use `currentEA || skillEA` for exchange validation and source EA checks
- SkillTrackerPanel:
  - Changed from SkillTrackerModal to SkillExchangeModal
  - Pass `skillEA={plannedSkillEA}` and `currentEA={currentSkillCounts}`

**Reason:** Unified modal component - planner editor uses skillEA only, tracker mode shows both planned and current EA. Modal now displays both badges matching the main section pattern.

---

#### 7. Fixed Tracker Mode Reset Logic
**Files Modified:**
- `frontend/src/components/plannerViewer/SkillTrackerPanel.tsx`

**Changes:**
- Reset handler now uses `plannedEA` values instead of `DEFAULT_SKILL_EA`
- Comment updated: "restore EA to defaults" → "restore currentEA to plannedEA values"

**Reason:** Tracker mode reset should restore to planner's preset values, not hardcoded defaults.

---

#### 8. Theme Pack Collection Scrollbar Spacing
**Files Modified:**
- `frontend/src/components/plannerViewer/HorizontalThemePackGallery.tsx`

**Changes:**
- Changed container padding from `p-2` to `p-2 pb-4`
- Added bottom padding to create gap between theme packs and scrollbar

**Reason:** Scrollbar was too close to theme pack cards, creating visual clutter.

---

#### 9. Global Scrollbar Theme Unification
**Files Modified:**
- `frontend/src/styles/globals.css`

**Changes:**
- Added global scrollbar styling section matching ScrollArea component theme
- Webkit (Chrome/Safari/Edge):
  - Width/height: 10px
  - Track: transparent background
  - Thumb: `#6a5a4a66` (--border color with 40% opacity)
  - Thumb hover: `#6a5a4a99` (60% opacity)
  - Border radius: 9999px (fully rounded)
- Firefox:
  - scrollbar-width: thin
  - scrollbar-color: `#6a5a4a66` transparent
- All styles use `!important` to override any component-specific styles

**Reason:**
- Unified scrollbar appearance across entire application
- DeckBuilderPane modal and other overflow containers now match theme pack collection scrollbar
- Webkit scrollbar `background` property doesn't support CSS variable functions (hsl, oklch), requires direct hex/rgba values

**Technical Note:**
Webkit scrollbar pseudo-elements (`::-webkit-scrollbar-thumb`) do not properly evaluate CSS custom properties with color functions. Direct color values (`#6a5a4a66`) must be used instead of `oklch(var(--border))` or `hsl(var(--border))`.

---

### Files Modified

1. `frontend/src/components/skillReplacement/SinnerSkillCard.tsx`
2. `frontend/src/components/skillReplacement/SkillReplacementSection.tsx`
3. `frontend/src/components/plannerViewer/GuideModeViewer.tsx`
4. `frontend/src/components/floorTheme/ThemePackViewer.tsx`
5. `frontend/src/components/floorTheme/FloorGiftViewer.tsx`
6. `frontend/src/components/floorTheme/FloorThemeGiftSection.tsx`
7. `frontend/src/components/skillReplacement/SkillEADisplay.tsx`
8. `frontend/src/components/skillReplacement/SkillExchangeModal.tsx`
9. `frontend/src/components/plannerViewer/SkillTrackerPanel.tsx`
10. `frontend/src/components/plannerViewer/HorizontalThemePackGallery.tsx`
11. `frontend/src/styles/globals.css`

---

#### 10. Removed Unused SkillTrackerModal Component
**Files Deleted:**
- `frontend/src/components/plannerViewer/SkillTrackerModal.tsx`

**Files Modified:**
- `frontend/src/components/plannerViewer/SkillTrackerPanel.test.tsx`

**Changes:**
- Deleted SkillTrackerModal.tsx (132 lines) - no longer used after modal unification
- Updated test mock from `./SkillTrackerModal` to `@/components/skillReplacement/SkillExchangeModal`
- All 17 tests still pass

**Reason:** SkillTrackerPanel now uses unified SkillExchangeModal with optional currentEA prop. Separate SkillTrackerModal component was redundant.

---

### Files Modified

1. `frontend/src/components/skillReplacement/SinnerSkillCard.tsx`
2. `frontend/src/components/skillReplacement/SkillReplacementSection.tsx`
3. `frontend/src/components/plannerViewer/GuideModeViewer.tsx`
4. `frontend/src/components/floorTheme/ThemePackViewer.tsx`
5. `frontend/src/components/floorTheme/FloorGiftViewer.tsx`
6. `frontend/src/components/floorTheme/FloorThemeGiftSection.tsx`
7. `frontend/src/components/skillReplacement/SkillEADisplay.tsx`
8. `frontend/src/components/skillReplacement/SkillExchangeModal.tsx`
9. `frontend/src/components/plannerViewer/SkillTrackerPanel.tsx`
10. `frontend/src/components/plannerViewer/SkillTrackerPanel.test.tsx`
11. `frontend/src/components/plannerViewer/HorizontalThemePackGallery.tsx`
12. `frontend/src/styles/globals.css`

### Files Deleted

1. `frontend/src/components/plannerViewer/SkillTrackerModal.tsx`

---

### Summary

Enhanced guide mode read-only enforcement by blocking all interactive popups while maintaining clean visual appearance (no grayed-out styling). Improved tracker mode UX by unifying skill exchange modal to show both planned and current EA, and moving current EA badge to lower-right for better visual alignment. Polished scrollbar appearance across the entire application to match ScrollArea component theme, including proper handling of Webkit scrollbar CSS limitations. Removed redundant SkillTrackerModal component after modal unification.

---

## 2026-01-11: Skill Replacement Tracker Mode Support & i18n Enhancement

### Context
User requested completing Skill Replacement section modifications that were left incomplete. The section needed proper tracker mode support (showing both planned and current EA values) and full i18n support for modal text and sinner names.

### Changes Made

#### 1. Skill Replacement Props Refactoring for Tracker Mode
**Files Modified:**
- `frontend/src/components/skillReplacement/SkillReplacementSection.tsx`
- `frontend/src/components/skillReplacement/SinnerSkillCard.tsx`
- `frontend/src/components/skillReplacement/SkillExchangeModal.tsx`
- `frontend/src/components/skillReplacement/SkillEADisplay.tsx`

**Changes:**

**SkillReplacementSection:**
- Changed prop `skillEAState` → `plannedEAState` (semantic clarity)
- Added prop `currentEAState?: Record<string, SkillEAState>` (tracker mode)
- Added prop `readOnly?: boolean` (guide mode)
- Updated `handleExchange()` to use `currentEAState || plannedEAState`
- Updated `handleReset()` to restore to `plannedEAState` values (not DEFAULT_SKILL_EA)
- Added `!readOnly` check to modal rendering condition
- Passed both `plannedEAState` and `currentEAState` to child components

**SinnerSkillCard:**
- Added prop `currentEA?: SkillEAState`
- Added prop `readOnly?: boolean`
- Added second EA badge at lower-right position (`-bottom-1 -right-1`) for current EA
- Planned EA badge remains at upper-right (`-top-1 -right-1`)
- `bg-primary` for planned EA, `bg-accent` for current EA
- Conditional rendering: `currentEA !== undefined` shows second badge

**SkillExchangeModal:**
- Added prop `currentEA?: SkillEAState`
- Updated exchange validation to use `currentEA || skillEA`
- Updated source EA checks to use active EA state
- Passed `currentEA` prop to SkillEADisplay components

**SkillEADisplay:**
- Added prop `currentEA?: number`
- Added second badge at lower-right for current EA
- Badge styling: planned (upper-right, primary), current (lower-right, accent)

**Reason:** Tracker mode needs to display both planned (from planner preset) and current (user-modified during gameplay) EA values side-by-side.

---

#### 2. Fixed Parent Component Prop Names
**Files Modified:**
- `frontend/src/routes/PlannerMDNewPage.tsx`
- `frontend/src/components/plannerViewer/GuideModeViewer.tsx`
- `frontend/src/components/plannerViewer/TrackerModeViewer.tsx` (already correct)

**Changes:**

**PlannerMDNewPage:**
- Changed `skillEAState={skillEAState}` → `plannedEAState={skillEAState}`

**GuideModeViewer:**
- Fixed typo: `plannedEA={content.skillEAState}` → `plannedEAState={content.skillEAState}`
- Added `readOnly={true}` prop

**TrackerModeViewer:**
- Already correctly passing both `plannedEAState` and `currentEAState`

**Reason:** Prop names must match interface definition to pass values correctly.

---

#### 3. Fixed Modal Prop Passing Bug
**Files Modified:**
- `frontend/src/components/skillReplacement/SkillReplacementSection.tsx`

**Issue:** Modal was receiving `currentEAState={...}` but interface expects `currentEA={...}`

**Fix:** Changed line 127 from:
```tsx
currentEAState={currentEAState?.[selectedSinner]}
```
to:
```tsx
currentEA={currentEAState?.[selectedSinner]}
```

**Error Prevented:** Current EA badge not displaying in modal because prop name mismatch caused `undefined` value.

---

#### 4. Modal Layout Centering
**Files Modified:**
- `frontend/src/components/skillReplacement/SkillExchangeModal.tsx`

**Changes:**
- Added `justify-center` to skill display flex container (line 81)
- Changed h3 from left-aligned to `text-center` for "Current Skills" header (line 78)

**Reason:** User requested centering the current skills EA viewer section in the modal.

**Note:** "Exchange Options" header remains left-aligned for UI consistency (matching the vertical list layout below it).

---

#### 5. Sinner Name i18n Support
**Files Created:**
- `static/i18n/EN/sinnerNames.json`
- `static/i18n/KR/sinnerNames.json`
- `static/i18n/JP/sinnerNames.json`
- `static/i18n/CN/sinnerNames.json`

**Content Structure:**
```json
{
  "YiSang": "Yi Sang",      // EN / 이상 (KR) / イサン (JP) / 李箱 (CN)
  "Faust": "Faust",         // EN / 파우스트 (KR) / ファウスト (JP) / 浮士德 (CN)
  "DonQuixote": "Don Quixote", // EN / 돈키호테 (KR) / ドン・キホーテ (JP) / 堂吉诃德 (CN)
  // ... all 12 sinners
}
```

**Reason:** Sinner names need translation (camelCase identifiers → localized display names with proper spacing).

---

#### 6. Modal Text i18n Support
**Files Modified:**
- `static/i18n/EN/planner.json`
- `static/i18n/KR/planner.json`
- `static/i18n/JP/planner.json`
- `static/i18n/CN/planner.json`

**Keys Added to `pages.plannerMD.skillReplacement` section:**

```json
"currentSkills": "Current Skills",      // 현재 스킬 (KR) / 現在のスキル (JP) / 当前技能 (CN)
"exchangeOptions": "Exchange Options"   // 교체 옵션 (KR) / 交換オプション (JP) / 交换选项 (CN)
```

**Reason:** Hardcoded English strings in modal headers needed translation keys.

---

#### 7. i18n Namespace Registration
**Files Modified:**
- `frontend/src/lib/i18n.ts`
- `frontend/src/components/skillReplacement/SkillExchangeModal.tsx`

**Changes:**

**i18n.ts:**
- Added imports for `enSinnerNames`, `jpSinnerNames`, `krSinnerNames`, `cnSinnerNames`
- Added `sinnerNames` to resources object for all 4 languages (line 32-35)
- Added `'sinnerNames'` to ns array (line 45)

**SkillExchangeModal:**
- Changed `useTranslation(['planner', 'common'])` → `useTranslation(['planner', 'common', 'sinnerNames'])`
- Changed dialog title: `{sinnerName}` → `{t(\`sinnerNames:${sinnerName}\`)}`
- Changed header: `"Current Skills"` → `{t('pages.plannerMD.skillReplacement.currentSkills')}`
- Changed header: `"Exchange Options"` → `{t('pages.plannerMD.skillReplacement.exchangeOptions')}`

**Reason:** New i18n namespace must be imported, registered in resources, and added to namespace array for translations to load. Components must use `t()` function with correct namespace prefix.

---

### Implementation Principles Applied

1. **Dual EA Display**: Planned (preset from planner) vs Current (modified during gameplay)
2. **Visual Distinction**: Color-coded badges (primary for planned, accent for current)
3. **Position Consistency**: Both badges on right side (upper/lower) for easy comparison
4. **Session-Only Current EA**: Current values reset on page refresh
5. **Proper i18n Pattern**: JSON files + namespace registration + t() function calls
6. **Semantic Prop Names**: `plannedEAState` vs `currentEAState` clarifies purpose

---

### Data Flow

```
Planner Editor Mode:
  skillEAState → plannedEAState → SkillReplacementSection → display only

Guide Mode:
  content.skillEAState → plannedEAState, readOnly=true → blocks modal

Tracker Mode:
  content.skillEAState → plannedEAState (top-right badge, primary color)
  trackerState.currentSkillCounts → currentEAState (bottom-right badge, accent color)
  Modal shows both planned and current EA
```

---

### Files Created

1. `static/i18n/EN/sinnerNames.json`
2. `static/i18n/KR/sinnerNames.json`
3. `static/i18n/JP/sinnerNames.json`
4. `static/i18n/CN/sinnerNames.json`

### Files Modified

1. `frontend/src/components/skillReplacement/SkillReplacementSection.tsx`
2. `frontend/src/components/skillReplacement/SinnerSkillCard.tsx`
3. `frontend/src/components/skillReplacement/SkillExchangeModal.tsx`
4. `frontend/src/components/skillReplacement/SkillEADisplay.tsx`
5. `frontend/src/routes/PlannerMDNewPage.tsx`
6. `frontend/src/components/plannerViewer/GuideModeViewer.tsx`
7. `static/i18n/EN/planner.json`
8. `static/i18n/KR/planner.json`
9. `static/i18n/JP/planner.json`
10. `static/i18n/CN/planner.json`
11. `frontend/src/lib/i18n.ts`

---

### Summary

Completed Skill Replacement section tracker mode support by adding dual EA display (planned vs current) with color-coded badges, proper prop refactoring, and full i18n support for modal text and sinner names. Fixed prop passing bugs between components and modal. Modal layout centered for better visual appearance.

---

## 2026-01-11: Section Notes Dialog Pattern

### Context
User requested converting inline notes display to dialog pattern (matching theme pack note button). Instead of showing notes inline below each section, add "View Notes" button to section headers that opens notes in a dialog.

### Changes Made

#### 1. Created Generic SectionNoteDialog Component
**Files Created:**
- `frontend/src/components/common/SectionNoteDialog.tsx`

**Features:**
- Reusable dialog wrapper for section notes
- Supports both read-only (viewer) and editable (editor) modes
- Empty state handling with placeholder text
- Uses NoteEditor component internally
- maxBytes validation (2000 bytes)

**Props:**
```typescript
{
  open: boolean
  onOpenChange: (open: boolean) => void
  sectionTitle: string
  noteContent: NoteContent
  onChange?: (content: NoteContent) => void
  readOnly?: boolean
}
```

**Reason:** Centralized component for consistent note dialog behavior across all sections.

---

#### 2. Added View Notes Button to PlannerSection
**Files Modified:**
- `frontend/src/components/common/PlannerSection.tsx`

**Changes:**
- Added `onViewNotes?: () => void` prop
- Added FileText icon button next to section title
- Button appears only when `onViewNotes` is provided
- Layout: flex justify-between (title left, button right)
- Added useTranslation for button text

**Reason:** Unified pattern for all sections to show notes button.

---

#### 3. Added onViewNotes Prop to All Section Components
**Files Modified:**
- `frontend/src/components/deckBuilder/DeckBuilderSummary.tsx`
- `frontend/src/components/startBuff/StartBuffSection.tsx`
- `frontend/src/components/startGift/StartGiftSummary.tsx`
- `frontend/src/components/egoGift/EGOGiftObservationSummary.tsx`
- `frontend/src/components/skillReplacement/SkillReplacementSection.tsx`
- `frontend/src/components/floorTheme/FloorThemeGiftSection.tsx`

**Changes for Each Component:**
- Added `onViewNotes?: () => void` to props interface
- Added to function signature
- Passed `onViewNotes` to internal `PlannerSection` component

**Reason:** All section components needed to support note button callback.

---

#### 4. Updated GuideModeViewer to Use Note Dialogs
**Files Modified:**
- `frontend/src/components/plannerViewer/GuideModeViewer.tsx`
- `frontend/src/components/plannerViewer/FloorGalleryTracker.tsx`

**GuideModeViewer Changes:**
- Added import: `SectionNoteDialog`
- Removed import: `NoteEditor`, `MAX_NOTE_BYTES`
- Added 6 section note dialog states: `deckBuilderNotesOpen`, `startBuffsNotesOpen`, `startGiftsNotesOpen`, `observationNotesOpen`, `skillReplacementNotesOpen`, `comprehensiveGiftsNotesOpen`
- Added floor notes state: `floorNotesOpen: Record<number, boolean>` initialized for all floors
- Removed all inline `<NoteEditor>` components (6 instances)
- Added `onViewNotes` callback to all section components
- Wrapped `ComprehensiveGiftGridTracker` in `PlannerSection` with title and notes button
- Added 6 `SectionNoteDialog` components for main sections
- Added floor note dialogs (mapped for all floors)
- Passed `onFloorNotesOpen` callback to `FloorGalleryTracker`

**FloorGalleryTracker Changes:**
- Added `onFloorNotesOpen: (floorNumber: number) => void` to props
- Removed inline `<NoteEditor>` from each floor (15 instances)
- Added `onViewNotes={() => onFloorNotesOpen(floorNumber)}` to each `FloorThemeGiftSection`
- Removed `<div className="space-y-2">` wrapper around each floor (no longer needed without inline notes)

**Reason:** Guide mode viewer should use dialog pattern for notes, not inline display.

---

#### 5. Updated TrackerModeViewer to Use Note Dialogs
**Files Modified:**
- `frontend/src/components/plannerViewer/TrackerModeViewer.tsx`
- `frontend/src/components/plannerViewer/DeckTrackerPanel.tsx`

**TrackerModeViewer Changes:**
- Added import: `SectionNoteDialog`
- Removed import: `NoteEditor`
- Added 6 section note dialog states (same as GuideModeViewer)
- Removed all inline `<NoteEditor>` components (5 instances)
- Added `onViewNotes` callback to all section components
- Wrapped `ComprehensiveGiftGridTracker` in `PlannerSection` with title and notes button
- Added 6 `SectionNoteDialog` components before Deck Import Dialog

**DeckTrackerPanel Changes:**
- Added `onViewNotes?: () => void` to props
- Passed `onViewNotes` to `DeckBuilderSummary`

**Reason:** Tracker mode viewer should use same dialog pattern as guide mode. Floor notes already handled by `ThemePackTrackerCard` View Notes button.

---

#### 6. Added i18n Support for View Notes Button
**Files Modified:**
- `static/i18n/EN/common.json`
- `static/i18n/KR/common.json`
- `static/i18n/JP/common.json`
- `static/i18n/CN/common.json`
- `frontend/src/components/common/PlannerSection.tsx`

**i18n Keys Added:**
```json
"viewNotes": "View Notes"  // 노트 보기 (KR) / ノートを表示 (JP) / 查看笔记 (CN)
```

**PlannerSection Changes:**
- Added `useTranslation('common')`
- Changed button text from `"View Notes"` → `{t('viewNotes')}`

**Reason:** All UI text requires i18n support for multi-language.

---

### Implementation Principles Applied

1. **Dialog Pattern**: Consistent with ThemePackTrackerCard's floor notes
2. **Header Action Button**: Notes button in section header (right-aligned)
3. **Read-Only vs Editable**: Viewer pages (guide/tracker) are read-only, editor allows editing
4. **Empty State Handling**: Dialogs show placeholder when no notes exist
5. **Component Reusability**: Single SectionNoteDialog for all sections
6. **No Layout Change**: Removing inline notes makes sections cleaner without affecting spacing

---

### Data Flow

```
Section Component (e.g., DeckBuilderSummary)
  └─ PlannerSection
      └─ View Notes button (if onViewNotes provided)
          └─ onClick={() => setDeckBuilderNotesOpen(true)}

Parent Component (GuideModeViewer/TrackerModeViewer)
  └─ SectionNoteDialog
      ├─ open={deckBuilderNotesOpen}
      ├─ noteContent={content.sectionNotes.deckBuilder}
      └─ readOnly={true}
```

---

### Files Created

1. `frontend/src/components/common/SectionNoteDialog.tsx`

### Files Modified

**Section Components (onViewNotes prop added):**
1. `frontend/src/components/common/PlannerSection.tsx`
2. `frontend/src/components/deckBuilder/DeckBuilderSummary.tsx`
3. `frontend/src/components/startBuff/StartBuffSection.tsx`
4. `frontend/src/components/startGift/StartGiftSummary.tsx`
5. `frontend/src/components/egoGift/EGOGiftObservationSummary.tsx`
6. `frontend/src/components/skillReplacement/SkillReplacementSection.tsx`
7. `frontend/src/components/floorTheme/FloorThemeGiftSection.tsx`

**Viewer Components (dialog pattern applied):**
8. `frontend/src/components/plannerViewer/GuideModeViewer.tsx`
9. `frontend/src/components/plannerViewer/TrackerModeViewer.tsx`
10. `frontend/src/components/plannerViewer/FloorGalleryTracker.tsx`
11. `frontend/src/components/plannerViewer/DeckTrackerPanel.tsx`

**i18n Files:**
12. `static/i18n/EN/common.json`
13. `static/i18n/KR/common.json`
14. `static/i18n/JP/common.json`
15. `static/i18n/CN/common.json`

---

### Summary

Converted inline note display to dialog pattern for all sections in viewer pages (guide and tracker modes). Added "View Notes" button to all section headers that opens notes in a dialog. Created reusable SectionNoteDialog component for consistent behavior. Editor page (PlannerMDNewPage) keeps inline notes for easier editing workflow. Added full i18n support for View Notes button.

---

## 2026-01-11: Guide Mode Note Editor Pattern Restoration

### Context
User identified that dialog pattern for notes made guide mode inconsistent with planner editor. Requested restoration to inline NoteEditor pattern matching PlannerMDNewPage structure, with readOnly mode applied and maxBytes removed.

### Changes Made

#### 1. Restored Inline NoteEditor Pattern in GuideModeViewer
**Files Modified:**
- `frontend/src/components/plannerViewer/GuideModeViewer.tsx`

**Changes:**
- Removed `SectionNoteDialog` import
- Added `NoteEditor` import
- Removed all section note dialog state variables (6 states + floor states)
- Removed all `onViewNotes` props from section components
- Removed all `<SectionNoteDialog>` components (6 main sections + 15 floors)
- Added inline `<NoteEditor>` after each section component:
  - DeckBuilder section
  - Start Buff section
  - Start Gift section
  - EGO Gift Observation section
  - Skill Replacement section
  - Comprehensive Gift Grid section
- All NoteEditors configured with:
  - `value={content.sectionNotes.xxx}`
  - `onChange={() => {}}`
  - `placeholder={t('pages.plannerMD.noteEditor.placeholder')}`
  - `readOnly={true}`
  - No `maxBytes` prop (optional, not needed for read-only)

**Reason:** Guide mode should match planner editor structure exactly - inline notes below each section, not in dialogs.

---

#### 2. Removed maxBytes from FloorGalleryTracker
**Files Modified:**
- `frontend/src/components/plannerViewer/FloorGalleryTracker.tsx`

**Changes:**
- Removed `MAX_NOTE_BYTES` import
- Removed `maxBytes={MAX_NOTE_BYTES}` prop from floor NoteEditor components
- NoteEditor now only has: `value`, `onChange={() => {}}`, `placeholder`, `readOnly={true}`

**Reason:** maxBytes is optional and unnecessary for read-only mode. NoteEditor conditionally renders byte counter only when maxBytes is provided and not readOnly.

---

#### 3. Verification: maxBytes Optional Handling
**Files Verified:**
- `frontend/src/types/NoteEditorTypes.ts` - Type definition confirms `maxBytes?: number` (optional)
- `frontend/src/components/noteEditor/NoteEditor.tsx` - Implementation confirms:
  - Line 71-75: Byte calculation skipped when `!maxBytes`
  - Line 274-285: Byte counter only rendered when `maxBytes && !readOnly`

**Result:** NoteEditor correctly handles missing maxBytes prop without errors.

---

### Implementation Principles Applied

1. **Pattern Consistency**: Guide mode now matches PlannerMDNewPage structure (inline notes)
2. **Optional Props**: Removed maxBytes (optional, not needed for read-only)
3. **Read-Only Mode**: All NoteEditors use `readOnly={true}` and `onChange={() => {}}`
4. **No View Notes Buttons**: Removed `onViewNotes` callbacks (PlannerSection optional prop)
5. **Clean Structure**: Each section followed by its inline NoteEditor

---

### Data Flow

```
PlannerMDNewPage (Editor Mode):
  Section Component
  ↓
  <NoteEditor value={...} onChange={handleChange} placeholder={...} maxBytes={2000} />

GuideModeViewer (Read-Only):
  Section Component (readOnly={true}, no onViewNotes)
  ↓
  <NoteEditor value={...} onChange={() => {}} placeholder={...} readOnly={true} />
  (no maxBytes = no byte counter rendered)
```

---

### Files Modified

1. `frontend/src/components/plannerViewer/GuideModeViewer.tsx`
2. `frontend/src/components/plannerViewer/FloorGalleryTracker.tsx`

---

### Summary

Restored guide mode to match planner editor pattern by replacing dialog-based notes with inline NoteEditor components. Removed unnecessary maxBytes prop for read-only mode. Guide mode now has consistent structure with editor, displaying notes inline below each section with readOnly mode applied.

---

## 2026-01-11: Code Quality Review Fixes

### Context
Code review orchestrator identified performance, reliability, and consistency issues in planner viewer implementation. Fixed high-priority issues to improve performance and code quality.

### Changes Made

#### 1. Gift Sorting Performance Optimization
**Files Modified:**
- `frontend/src/components/plannerViewer/ComprehensiveGiftGridTracker.tsx`

**Issue:** Sorting 200+ gifts on every hover event using single-pass comparison function

**Fix:**
- Separate gifts into highlighted and non-highlighted arrays during initial iteration
- Sort each array independently by gift ID
- Concatenate arrays (highlighted first, then non-highlighted)
- Reduces computational complexity from O(n log n) to O(h log h + (n-h) log (n-h)) where h << n

**Reason:** With 200+ gifts and frequent hover events, the original sort was causing unnecessary computation on every hover.

---

#### 2. Planner Type Validation
**Files Modified:**
- `frontend/src/routes/PlannerMDDetailPage.tsx`

**Issue:** Viewer assumed MDPlannerContent structure without validating planner.config.type

**Fix:**
- Added type guard checking `planner.config.type === 'MIRROR_DUNGEON'`
- Returns error state with clear message if type is invalid
- Shows current type and back button for user navigation

**Reason:** Viewer components are designed specifically for Mirror Dungeon planners and will fail with other planner types.

---

#### 3. Dead Code Removal and Documentation
**Files Modified:**
- `frontend/src/hooks/useTrackerState.ts`

**Issues:**
- hoveredThemePack field defined but unused (TrackerModeViewer uses separate state)
- Empty deployment order reset behavior undocumented
- Floor index 0-based vs 1-based UI display not clarified

**Fixes:**
- Removed hoveredThemePack field from TrackerState interface
- Removed setHoveredThemePack from return type
- Added comprehensive JSDoc to TrackerState interface documenting reset behavior
- Clarified deploymentOrder empty array signals fallback to content.deploymentOrder
- Documented floorIndex as 0-based in doneMarks and toggleDoneMark JSDoc
- Added reset behavior documentation for all state fields

**Reason:** Dead code creates maintenance burden and unclear contracts cause bugs. Explicit documentation prevents off-by-one errors.

---

### Files Modified

1. `frontend/src/components/plannerViewer/ComprehensiveGiftGridTracker.tsx`
2. `frontend/src/routes/PlannerMDDetailPage.tsx`
3. `frontend/src/hooks/useTrackerState.ts`

---

### Summary

Fixed high-priority code quality issues identified in review: optimized gift sorting performance for 200+ items, added planner type validation to prevent runtime errors with incompatible planner types, removed dead hover state code, and added comprehensive documentation for tracker state reset behavior and floor indexing conventions.