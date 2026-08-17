# Task: Planner Editor UI Standardization

## Description
Standardize the planner editor's 8+ sections to follow consistent patterns for wrappers, clickable regions, empty states, reset buttons, and filter behavior. The planner page (`PlannerMDNewPage.tsx`) has evolved with multiple patterns added incrementally, creating visual and interaction inconsistencies.

Key standardizations needed:

**Wrapper Pattern**:
- All sections must use `PlannerSection` wrapper for consistent h2 headers and container styling
- DeckBuilderSummary has redundant double-wrapping (inner SECTION_STYLES.container) - remove it
- FloorThemeGiftSection uses custom styling instead of PlannerSection - wrap it properly
- Floor sections should use bg-card (match other sections) instead of current bg-muted

**Clickable Summary Pattern**:
- Convert all clickable summaries from `div role="button"` to native `<button type="button">` elements
- Native buttons provide better accessibility (keyboard support, screen reader announcements)
- ComprehensiveGiftSummary already uses button - keep as reference pattern
- Remove manual onKeyDown handlers (native button handles Enter/Space)
- All summaries need: `type="button"` and `className="w-full text-left cursor-pointer hover:opacity-90 transition-opacity"`

**Empty State Pattern**:
- All empty states should use consistent min-h-28 height (ComprehensiveGiftSummary incorrectly uses min-h-24)
- All empty states should have dashed border: `border-2 border-dashed border-muted-foreground/50 rounded-lg`
- Create EMPTY_STATE constant in constants.ts for reuse

**Reset/Done Button Pattern** (MANDATORY):
- All EditPanes MUST have: `Reset | Done` button layout in DialogFooter
- Reset button: LEFT side, `variant="outline"`, calls `onSelectionChange(new Set/Map())`
- Done button: RIGHT side, `variant="default"`, closes dialog (DialogClose)
- StartGiftEditPane and ComprehensiveGiftSelectorPane are missing reset buttons - add them
- Layout pattern:
  ```tsx
  <DialogFooter className="flex justify-between">
    <Button variant="outline" onClick={handleReset}>{t('common.reset')}</Button>
    <DialogClose asChild>
      <Button>{t('common.done')}</Button>
    </DialogClose>
  </DialogFooter>
  ```

**Filter State Pattern**:
- ComprehensiveGiftSelectorPane has a bug: filters don't reset when dialog closes (unlike EGOGiftObservationEditPane)
- Add useEffect to reset filters on dialog close, matching the Observation pattern

**Constraint**: DeckBuilder edit trigger must remain as explicit button (not clickable summary) because the summary itself has click interactions for setting deployment order.

## Research
- Read current PlannerSection implementation for wrapper pattern
- Study ComprehensiveGiftSummary for correct button pattern
- Review EGOGiftObservationEditPane for filter reset pattern
- Check existing SECTION_STYLES usage in constants.ts
- Verify i18n key `pages.plannerMD.floor` exists or needs creation

## Scope
Files to READ for context:
- `frontend/src/components/common/PlannerSection.tsx` - wrapper pattern
- `frontend/src/lib/constants.ts` - existing SECTION_STYLES
- `frontend/src/components/egoGift/ComprehensiveGiftSummary.tsx` - button pattern reference
- `frontend/src/components/egoGift/EGOGiftObservationEditPane.tsx` - filter reset pattern, footer pattern
- `frontend/src/components/startBuff/StartBuffEditPane.tsx` - reset button pattern

## Target Code Area
Files to MODIFY:
- `frontend/src/lib/constants.ts` - Add EMPTY_STATE constant
- `frontend/src/components/deckBuilder/DeckBuilderSummary.tsx` - Remove redundant inner container
- `frontend/src/components/startBuff/StartBuffSection.tsx` - Convert div→button, add dashed border to empty state
- `frontend/src/components/startGift/StartGiftSummary.tsx` - Convert div→button
- `frontend/src/components/startGift/StartGiftEditPane.tsx` - Add reset button (left), keep done button (right)
- `frontend/src/components/egoGift/EGOGiftObservationSummary.tsx` - Convert div→button, add dashed border to empty state
- `frontend/src/components/egoGift/ComprehensiveGiftSummary.tsx` - Add hover feedback, fix min-h-24→min-h-28
- `frontend/src/components/egoGift/ComprehensiveGiftSelectorPane.tsx` - Add reset button (left), keep done button (right), add filter reset on close
- `frontend/src/components/floorTheme/FloorThemeGiftSection.tsx` - Wrap in PlannerSection, convert viewers to buttons

i18n files to ADD keys:
- `static/i18n/EN/common.json` - Add `pages.plannerMD.floor`: "Floor {{number}}"
- `static/i18n/KR/common.json` - Add `pages.plannerMD.floor`: "{{number}}층"
- `static/i18n/JP/common.json` - Add `pages.plannerMD.floor`: "{{number}}階"
- `static/i18n/CN/common.json` - Add `pages.plannerMD.floor`: "第{{number}}层"

Test files to CREATE:
- `frontend/src/components/startBuff/StartBuffSection.test.tsx` - Button accessibility, empty state styling
- `frontend/src/components/startGift/StartGiftSummary.test.tsx` - Button accessibility
- `frontend/src/components/startGift/StartGiftEditPane.test.tsx` - Reset/Done button layout
- `frontend/src/components/egoGift/EGOGiftObservationSummary.test.tsx` - Button accessibility, empty state
- `frontend/src/components/egoGift/ComprehensiveGiftSummary.test.tsx` - Button accessibility, hover, height
- `frontend/src/components/egoGift/ComprehensiveGiftSelectorPane.test.tsx` - Reset/Done layout, filter reset on close
- `frontend/src/components/floorTheme/FloorThemeGiftSection.test.tsx` - PlannerSection wrapper, keyboard accessibility

## System Context (Senior Thinking)
- Feature domain: Planner (MD)
- Core files: `routes/PlannerMDNewPage.tsx`, `hooks/usePlannerStorage.ts`, `components/deckBuilder/*`, `components/startBuff/*`, `components/startGift/*`, `components/egoGift/*`, `components/floorTheme/*`
- Cross-cutting concerns:
  - PlannerSection wrapper (used by all planner sections)
  - SECTION_STYLES constants (styling tokens)
  - i18n for section headers

## Impact Analysis
- Files being modified:
  - `constants.ts` (High impact - used by all components) - adding EMPTY_STATE only, no breaking changes
  - `DeckBuilderSummary.tsx` (Low impact - isolated component)
  - `StartBuffSection.tsx` (Low impact - isolated component)
  - `StartGiftSummary.tsx` (Low impact - isolated component)
  - `StartGiftEditPane.tsx` (Low impact - isolated component)
  - `EGOGiftObservationSummary.tsx` (Low impact - isolated component)
  - `ComprehensiveGiftSummary.tsx` (Low impact - isolated component)
  - `ComprehensiveGiftSelectorPane.tsx` (Low impact - isolated component)
  - `FloorThemeGiftSection.tsx` (Medium impact - 15 floor sections use this)
- What depends on these files: PlannerMDNewPage imports all of these
- Potential ripple effects: FloorThemeGiftSection layout change could affect spacing of floor sections
- High-impact files to watch: FloorThemeGiftSection.tsx has complex flex layout

## Risk Assessment
- Edge cases:
  - FloorThemeGiftSection flex layout must be preserved inside PlannerSection container
  - DeckBuilderSummary spacing may change when removing inner container
  - Button elements may need additional styling to preserve layout
- Performance concerns: None - refactoring only
- Backward compatibility: Visual changes only, no data format changes
- Security considerations: None

## Testing Guidelines

### Manual UI Testing
1. Navigate to /planner (new planner page)
2. Verify all section headers have consistent h2 styling
3. Verify all sections have consistent card background (bg-card, not bg-muted for floors)

**DeckBuilder Section:**
4. Verify DeckBuilder section has single border (not double border from redundant container)
5. Click on sinner cards to set deployment order - verify this still works
6. Click "Edit Deck" button - verify deck pane opens

**StartBuff Section:**
7. Verify StartBuff section has clickable summary (entire section is clickable)
8. Press Tab to navigate to StartBuff summary
9. Press Enter or Space - verify edit pane opens
10. Verify empty state has dashed border
11. In edit pane, verify Reset button (left) and Done button (right) exist
12. Click Reset - verify selection is cleared

**StartGift Section:**
13. Verify StartGift section has clickable summary
14. Press Tab then Enter/Space - verify edit pane opens
15. In edit pane, verify Reset button (left) and Done button (right) exist
16. Select some gifts, click Reset - verify keyword and gifts are cleared
17. Click Done - verify dialog closes

**EGO Gift Observation Section:**
18. Verify Observation section has clickable summary
19. Press Tab then Enter/Space - verify edit pane opens
20. Verify empty state has dashed border
21. Verify Reset button (left) and Done button (right) exist

**Comprehensive Gift Section:**
22. Verify Comprehensive section has clickable summary with hover feedback
23. Verify empty state has consistent height (min-h-28, not shorter)
24. Verify empty state has dashed border
25. In selector pane, verify Reset button (left) and Done button (right) exist
26. Set some filters and select gifts
27. Close the dialog
28. Reopen the dialog - verify filters are reset (clean slate)
29. Click Reset - verify selection is cleared
30. Click Done - verify dialog closes

**Floor Sections (repeat for floor 1):**
31. Verify floor section has h2 header from PlannerSection
32. Verify floor section has bg-card background (not gray bg-muted)
33. Tab to theme pack viewer - press Enter - verify theme pane opens
34. Tab to gift viewer - press Enter - verify gift pane opens

### Automated Functional Verification
- [ ] All summary components use native `<button>` elements (not div with role="button")
- [ ] All buttons have `type="button"` attribute
- [ ] All empty states use EMPTY_STATE.MIN_HEIGHT constant (min-h-28)
- [ ] All empty states have dashed border styling
- [ ] All EditPanes have Reset button (left) and Done button (right) in DialogFooter
- [ ] Keyboard navigation works for all summaries (Enter/Space opens pane)

### Unit Tests Required
Each test file should verify:

**Summary Component Tests** (StartBuffSection, StartGiftSummary, EGOGiftObservationSummary, ComprehensiveGiftSummary):
- [ ] Renders as native `<button>` element with `type="button"`
- [ ] Has `role` attribute removed (native button doesn't need it)
- [ ] Calls onClick when clicked
- [ ] Has hover styles (`hover:opacity-90`)
- [ ] Empty state has min-h-28 height class
- [ ] Empty state has dashed border classes

**EditPane Tests** (StartGiftEditPane, ComprehensiveGiftSelectorPane):
- [ ] DialogFooter contains Reset button on LEFT
- [ ] DialogFooter contains Done button on RIGHT
- [ ] Reset button has `variant="outline"`
- [ ] Reset button clears selection when clicked
- [ ] Done button is wrapped in DialogClose

**ComprehensiveGiftSelectorPane Additional Tests**:
- [ ] Filter state resets when dialog closes (useEffect behavior)
- [ ] selectedKeywords cleared on close
- [ ] searchQuery cleared on close
- [ ] sortMode reset to 'tier-first' on close

**FloorThemeGiftSection Tests**:
- [ ] Wrapped in PlannerSection component
- [ ] PlannerSection receives correct title prop with floor number
- [ ] ThemePackViewer is a `<button>` element
- [ ] FloorGiftViewer is a `<button>` element
- [ ] Uses bg-card styling (not bg-muted)

### Edge Cases
- [ ] DeckBuilder: Sinner card clicks still set deployment order (not intercepted by summary)
- [ ] FloorThemeGiftSection: 4-column flex layout preserved inside PlannerSection
- [ ] ComprehensiveGiftSelectorPane: Filters reset to default when dialog closes
- [ ] StartGiftEditPane: Reset clears both keyword selection AND gift selection
- [ ] Hover state: All clickable summaries show opacity change on hover
- [ ] DialogFooter layout: `flex justify-between` ensures Reset (left) | Done (right) spacing
- [ ] Reset button: Must be `variant="outline"` to visually distinguish from Done
- [ ] Done button: Must be wrapped in `DialogClose` for proper dialog dismissal

### Integration Points
- [ ] PlannerSection wrapper: All sections render with consistent h2 + container
- [ ] EMPTY_STATE constant: Used by all summary components
- [ ] i18n keys: Floor section headers display correctly in all languages (EN/KR/JP/CN)
