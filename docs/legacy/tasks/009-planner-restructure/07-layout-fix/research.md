# Research: Planner Editor UI Standardization

## Clarifications Resolved
- **Filter Reset Timing**: ComprehensiveGiftSelectorPane resets filters on CLOSE only (`if (!open)`), matching EGOGiftObservationEditPane pattern

---

## Spec-to-Code Mapping

| Requirement | Target File | Modification |
|-------------|-------------|--------------|
| Remove double-wrap | `DeckBuilderSummary.tsx` | Remove inner `SECTION_STYLES.container` div |
| Wrap floor sections | `FloorThemeGiftSection.tsx` | Wrap in `PlannerSection`, convert viewers to buttons |
| Convert div→button | `StartBuffSection.tsx` | Native `<button type="button">`, remove onKeyDown |
| Convert div→button | `StartGiftSummary.tsx` | Native `<button type="button">`, remove onKeyDown |
| Convert div→button | `EGOGiftObservationSummary.tsx` | Native `<button type="button">`, remove onKeyDown |
| Fix button styling | `ComprehensiveGiftSummary.tsx` | Add hover feedback, fix min-h-24→min-h-28 |
| Add EMPTY_STATE | `constants.ts` | New constant: MIN_HEIGHT + DASHED_BORDER |
| Add empty border | `StartBuffSection.tsx`, `EGOGiftObservationSummary.tsx` | Use EMPTY_STATE constant |
| Add reset button | `StartGiftEditPane.tsx` | Reset (left, outline) + Done (right) |
| Add reset button | `ComprehensiveGiftSelectorPane.tsx` | Reset (left, outline) + Done (right) |
| Add filter reset | `ComprehensiveGiftSelectorPane.tsx` | useEffect to reset on `!open` |
| Add i18n keys | `static/i18n/*/common.json` | `pages.plannerMD.floor` in EN/KR/JP/CN |

---

## Pattern Enforcement (MANDATORY)

| Modified File | MUST Read First | Pattern to Copy |
|---------------|-----------------|-----------------|
| `StartBuffSection.tsx` | `ComprehensiveGiftSummary.tsx` | Native button with type="button", className |
| `StartGiftSummary.tsx` | `ComprehensiveGiftSummary.tsx` | Same button pattern |
| `EGOGiftObservationSummary.tsx` | `ComprehensiveGiftSummary.tsx` | Same button pattern |
| `StartGiftEditPane.tsx` | `StartBuffEditPane.tsx` | DialogFooter with Reset/Done layout |
| `ComprehensiveGiftSelectorPane.tsx` | `StartBuffEditPane.tsx` | DialogFooter with Reset/Done layout |
| `ComprehensiveGiftSelectorPane.tsx` | `EGOGiftObservationEditPane.tsx` | useEffect filter reset (lines 70-76) |
| `FloorThemeGiftSection.tsx` | `PlannerSection.tsx` | Section wrapper with title prop |
| `DeckBuilderSummary.tsx` | Self-check | Remove redundant inner container |

---

## Existing Utilities

| Category | Location | Found |
|----------|----------|-------|
| Section styling | `lib/constants.ts` | `SECTION_STYLES` (TEXT, SPACING, container, grid) |
| i18n keys | `static/i18n/EN/common.json` | `common.reset`, `common.done` exist |
| Utility function | `lib/utils.ts` | `cn()` for className merging |
| Dialog components | `@/components/ui/dialog` | DialogFooter, DialogClose exist |

**Needs Creation:**
- `EMPTY_STATE` constant in `constants.ts`

---

## Gap Analysis

**Missing:**
- `EMPTY_STATE` constant (MIN_HEIGHT: 'min-h-28', DASHED_BORDER: 'border-2 border-dashed...')
- i18n key `pages.plannerMD.floor` (4 languages)
- Reset buttons in StartGiftEditPane, ComprehensiveGiftSelectorPane
- Filter reset useEffect in ComprehensiveGiftSelectorPane

**Needs Modification:**
- 3 summary components: div→button conversion
- 1 summary component: height + hover fix
- 2 EditPanes: add Reset button
- 1 EditPane: add filter reset
- 1 section: remove double-wrap
- 1 section: wrap in PlannerSection

**Can Reuse:**
- PlannerSection wrapper
- SECTION_STYLES tokens
- Button/DialogFooter/DialogClose from shadcn/ui
- Filter reset pattern from EGOGiftObservationEditPane
- Hover opacity pattern (existing in codebase)

---

## Testing Requirements

### Manual UI Tests
1. Navigate to /planner
2. Tab through summaries → Enter/Space opens dialogs
3. Verify Reset (left) | Done (right) layout in all EditPanes
4. Verify floor sections have h2 header, bg-card background
5. Verify empty states show dashed border, consistent height
6. Verify DeckBuilder has single border (no double-wrap)
7. Close/reopen ComprehensiveGiftSelectorPane → filters reset

### Unit Tests
- Button has `type="button"` attribute
- Button does NOT have `role="button"` (native button)
- Empty states use `min-h-28` and dashed border classes
- Reset button calls `onSelectionChange(new Set/Map())`
- Done button wrapped in DialogClose
- Filter state resets when `open` changes to false

---

## Technical Constraints

**Button Pattern:**
- `type="button"` (prevent form submission)
- `className="w-full text-left cursor-pointer hover:opacity-90 transition-opacity"`
- Remove manual `onKeyDown` (native button handles Enter/Space)

**Empty State Pattern:**
- Use `EMPTY_STATE.MIN_HEIGHT` (min-h-28)
- Use `EMPTY_STATE.DASHED_BORDER` (border-2 border-dashed border-muted-foreground/50 rounded-lg)

**DialogFooter Pattern:**
- `className="flex justify-between"`
- Reset: LEFT, `variant="outline"`
- Done: RIGHT, `variant="default"`, wrapped in DialogClose

**Filter Reset Pattern:**
- useEffect with `[open]` dependency
- Reset only when `!open` (on close)
- Clear: selectedKeywords, searchQuery, sortMode
