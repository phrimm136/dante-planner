# Task: Optimize EGO Gift Edit Pane Opening Performance

## Description
Improve the perceived performance when opening EGO gift-related edit panes in the MD planner editor. Currently, when users click to open these panes, there is a noticeable delay (100-500ms freeze) before the dialog animation starts, making the UI feel unresponsive.

The issue occurs on every opening (not just first load), affecting:
- EGO Gift Observation Edit Pane
- Comprehensive Gift Selector Pane

Users should experience:
- Instant visual feedback when clicking to open a pane (dialog animation starts immediately)
- Content populating smoothly during or shortly after the dialog animation
- No visible loading spinners or skeletons (silent deferred loading)
- Maintained functionality of all existing features (selection, filters, cascade logic)

The root cause is synchronous expensive data transforms (merging 500+ gift specs with i18n data via `Object.entries().map()`) that block the first render frame, preventing the dialog animation from starting.

## Research
- Study the proven deferred loading pattern in `DeckBuilderPane.tsx` (lines 66-110)
- Understand `requestAnimationFrame` timing and browser paint cycle coordination
- Review React 19's render behavior with concurrent features
- Analyze the gift merge transform cost (O(n) where n=500+)
- Investigate filter state reset timing in `useEffect([open])` hooks

## Scope
**Files to READ for context:**
- `/frontend/src/components/deckBuilder/DeckBuilderPane.tsx` (working pattern, lines 66-110)
- `/frontend/src/components/egoGift/EGOGiftObservationEditPane.tsx` (needs optimization)
- `/frontend/src/components/egoGift/ComprehensiveGiftSelectorPane.tsx` (needs optimization)
- `/frontend/src/hooks/useEGOGiftListData.ts` (data source)
- `/frontend/src/hooks/useEGOGiftObservationData.ts` (observation data)
- `/docs/architecture-map.md` (Planner feature section, lines 429-537)

## Target Code Area
**Files to MODIFY:**
- `/frontend/src/components/egoGift/EGOGiftObservationEditPane.tsx`
  - Add contentReady state management (lines 43-47, before data hooks)
  - Guard the gifts useMemo transform (lines 50-61)

- `/frontend/src/components/egoGift/ComprehensiveGiftSelectorPane.tsx`
  - Add contentReady state management (lines 44-48, before data hooks)
  - Wrap gifts array in useMemo and guard the transform (lines 66-76)

**Potential extraction (after both panes work):**
- `/frontend/src/hooks/useContentReady.ts` (new shared hook to reduce duplication)

## System Context (Senior Thinking)

### Feature Domain
**Planner (Complex)** - specifically the EGO Gift sections of the MD planner editor

### Core Files in This Domain
From architecture-map.md lines 19-20:
- Main planner page: `routes/PlannerMDNewPage.tsx` (~720 lines)
- Summary+EditPane pattern components:
  - `components/egoGift/EGOGiftObservationSummary.tsx` + `EGOGiftObservationEditPane.tsx`
  - `components/egoGift/ComprehensiveGiftSummary.tsx` + `ComprehensiveGiftSelectorPane.tsx`
- Data hooks: `useEGOGiftListData.ts`, `useEGOGiftObservationData.ts`

### Cross-Cutting Concerns Touched
From architecture-map.md lines 49-84:
- **Data Fetching**: TanStack Query with `useSuspenseQuery` pattern (lines 93-101)
- **i18n**: Gift names from `static/i18n/{lang}/egoGiftNameList.json` (line 779)
- **Validation**: Zod schemas in `schemas/EGOGiftSchemas.ts` (line 562)
- **UI Components**: shadcn/ui Dialog component (line 804)
- **Constants**: `MAX_OBSERVABLE_GIFTS` from `lib/constants.ts` (line 60)

### Data Flow Pattern
From architecture-map.md lines 89-101:
```
Static JSON (egoGiftSpecList.json)
         ↓
useSuspenseQuery (TanStack Query)
         ↓
Zod Schema Validation
         ↓
EditPane Component (BOTTLENECK: Object.entries().map())
         ↓
React render (dialog animation blocked)
```

## Impact Analysis

### Files Being Modified
From architecture-map.md "When Modifying: Impact Analysis":

| File | Impact Level | What Depends On It |
|------|--------------|-------------------|
| `EGOGiftObservationEditPane.tsx` | Low-Medium | Called by PlannerMDNewPage.tsx, renders EGOGiftSelectionList |
| `ComprehensiveGiftSelectorPane.tsx` | Low-Medium | Called by PlannerMDNewPage.tsx, handles cascade selection logic |

Both files are **isolated pane components** (not shared utilities), so changes have limited ripple effects.

### Dependencies (File Dependency Graph)
From architecture-map.md lines 609-645:
```
PlannerMDNewPage.tsx
    └── EGOGiftObservationEditPane.tsx
          ├── hooks/useEGOGiftObservationData.ts
          ├── hooks/useEGOGiftListData.ts (shared with other panes)
          ├── components/egoGift/EGOGiftSelectionList.tsx
          └── components/egoGift/EGOGiftObservationSelection.tsx

    └── ComprehensiveGiftSelectorPane.tsx
          ├── hooks/useEGOGiftListData.ts (shared)
          ├── lib/egoGiftEncoding.ts (cascade logic)
          └── components/egoGift/EGOGiftSelectionList.tsx (shared)
```

### Potential Ripple Effects
- **Shared hook `useEGOGiftListData`**: No changes needed (hook contract unchanged)
- **Child components** (`EGOGiftSelectionList`): Will receive empty array on first frame, must handle gracefully
- **Parent suspense boundaries**: Already in place at PlannerMDNewPage.tsx (lines 810, 824 per architecture-map.md line 469)
- **Filter state reset**: Timing preserved (`useEffect([open])` runs before contentReady)
- **Cascade selection logic**: Preserved (specById map builds after contentReady)

### High-Impact Files to Watch
From architecture-map.md lines 710-731:
- `lib/constants.ts` (High impact - all components) - **Not modified**, only reading `MAX_OBSERVABLE_GIFTS`
- `hooks/useEGOGiftListData.ts` (Medium impact - multiple panes) - **Not modified**, hook contract unchanged
- All other modified files are **Low impact** (isolated components)

## Risk Assessment

### Risks Identified (from /arch-research)

1. **Breaking Suspense Chain**
   - Risk: If contentReady flag is wrong, pane renders without data
   - Mitigation: Keep `useSuspenseQuery` calls unchanged, only defer the transforms
   - Severity: Low (pattern proven in DeckBuilderPane)

2. **Filter State Lost on Reopen**
   - Risk: Users expect filters to reset (current behavior), timing change might break this
   - Mitigation: `useEffect([open])` runs before `requestAnimationFrame`, order preserved
   - Severity: Low (separate lifecycle hooks)

3. **Cascade Breaks in ComprehensiveGiftSelectorPane**
   - Risk: Cascade selection logic depends on `specById` map existing
   - Mitigation: Build `specById` map inside contentReady guard (line 79 in original)
   - Severity: Medium (critical feature, must test thoroughly)

4. **React Compiler Misses Optimizations**
   - Risk: Manual deferral might confuse React 19 Compiler
   - Mitigation: Pattern uses standard hooks (useState, useEffect, useMemo), compiler-friendly
   - Severity: Low (React 19 handles concurrent features well)

### Edge Cases Not Yet Defined

- **User closes dialog before contentReady fires**:
  - Expected: `setContentReady(false)` clears content, `requestAnimationFrame` cancelled in cleanup
  - Needs verification: No memory leaks, proper cleanup

- **User rapidly opens/closes dialog (double-click)**:
  - Expected: Each open schedules new frame, previous frames cancelled
  - Potential issue: Flicker if multiple frames fire
  - Needs verification: Only latest frame takes effect

- **Data fetches while dialog animating**:
  - Expected: New data causes re-render mid-animation, content updates
  - Acceptable: User sees smooth transition, not a bug
  - Needs verification: No layout shift

- **Empty state (no gifts match filter)**:
  - Expected: Grid renders but empty, no error
  - Current behavior: Already handles this gracefully
  - Needs verification: Empty array doesn't break child components

### Performance Concerns

- **First frame render time**: Should be <16ms (one frame @ 60fps)
  - Empty dialog render is cheap (no DOM nodes for gifts)
  - Filter controls render immediately (acceptable)

- **Second frame transform time**: 20-40ms for 500 gifts
  - Still blocks main thread but animation already started
  - User perceives as "loading during animation" not "frozen"

- **useMemo dependency array**: Adding `contentReady` increases re-renders
  - Only re-runs when contentReady changes (once per open)
  - No performance regression

### Backward Compatibility

- **No breaking changes**: Hook APIs unchanged, component props unchanged
- **Behavior change**: Dialog opens faster, content appears slightly later
  - User-facing improvement, not a regression
  - No feature removal

### Security Considerations

- **No security impact**: Client-side UI optimization only
- **No data handling changes**: Data loading, validation unchanged

## Testing Guidelines

### Manual UI Testing

**Test Case 1: EGO Gift Observation Pane Opening**
1. Open browser and navigate to `/planner/md/new` (or existing planner)
2. Scroll to "EGO Gift Observation" section
3. Click the section button to open the edit pane
4. **Verify**: Dialog animates open immediately (no freeze)
5. **Verify**: Gift cards appear during or shortly after animation
6. **Verify**: Cost display shows correct starlight cost
7. Click a gift card to select it
8. **Verify**: Selection works normally, card highlights
9. Click "Done" to close the pane
10. Reopen the pane
11. **Verify**: Filters are reset (keyword filter empty, search cleared)
12. **Verify**: Previous selection is preserved

**Test Case 2: Comprehensive Gift Selector Pane Opening**
1. In the same planner, scroll to "Comprehensive EGO Gift List" section
2. Click the section button to open the comprehensive gift selector
3. **Verify**: Dialog animates open immediately (no freeze)
4. **Verify**: Gift cards appear during or shortly after animation
5. Select a gift that has a recipe (e.g., gift 9088)
6. **Verify**: Cascade selection works - ingredient gifts auto-select
7. Change the enhancement level (0 → 1) on the selected gift
8. **Verify**: Enhancement level updates, ingredients remain selected
9. Click the same enhancement level again
10. **Verify**: Gift deselects (no reverse cascade)
11. Click "Reset" button
12. **Verify**: All selections clear
13. Click "Done" to close the pane

**Test Case 3: Rapid Open/Close (Edge Case)**
1. In "EGO Gift Observation" section
2. Quickly double-click the section button
3. **Verify**: No flicker, no duplicate dialogs
4. **Verify**: Pane opens normally on second click (if first cancelled)
5. Close the pane
6. Open, then immediately close before animation completes
7. **Verify**: Pane closes cleanly, no content flash

**Test Case 4: Slow Device Simulation (Performance)**
1. Open Chrome DevTools → Performance tab
2. Enable CPU throttling (6x slowdown)
3. Open "Comprehensive Gift Selector" pane
4. **Verify**: Dialog animation still starts immediately
5. **Verify**: Content populates shortly after (acceptable delay)
6. Disable throttling and repeat
7. **Verify**: On fast device, content appears almost instantly

### Automated Functional Verification

- [ ] **Dialog animation timing**: Dialog `data-[state=open]` attribute appears within 50ms of button click
- [ ] **Content deferral**: Gift array is empty on first render (frame 1), populated on second render (frame 2+)
- [ ] **Filter reset timing**: Keyword filter, search query, sort mode all reset when `open` changes to `false`
- [ ] **Selection persistence**: `selectedGiftIds` prop remains unchanged across pane open/close
- [ ] **Cascade selection**: Selecting gift with recipe auto-selects all ingredient gifts with enhancement=0
- [ ] **Enhancement toggle**: Clicking same enhancement level deselects gift, different level updates enhancement
- [ ] **Max selection enforcement**: EGOGiftObservation pane prevents selection beyond `MAX_OBSERVABLE_GIFTS` (currently 10)
- [ ] **Cost calculation**: Starlight cost updates correctly based on selected gift count in Observation pane
- [ ] **useMemo dependencies**: `gifts` array only re-computes when `contentReady`, `spec`, or `i18n` changes

### Edge Cases

- [ ] **Empty array handling**: `EGOGiftSelectionList` receives empty array on first frame, renders without error
- [ ] **Rapid open/close**: `requestAnimationFrame` cleanup prevents memory leaks, multiple frames don't stack
- [ ] **Close before contentReady**: Dialog closes cleanly, `setContentReady(false)` resets state, no stale content
- [ ] **Missing i18n data**: Gift name falls back to ID if `i18n[id]` is undefined (existing behavior preserved)
- [ ] **Invalid gift spec**: Cascade logic handles missing `giftSpec` gracefully (line 113-117 in ComprehensiveGiftSelectorPane)
- [ ] **Circular recipe references**: `visited` Set prevents infinite loops in cascade selection (lines 121-128)

### Integration Points

- [ ] **Parent Suspense boundaries**: Panes wrapped in `<Suspense>` at PlannerMDNewPage.tsx lines 810, 824 still work
- [ ] **TanStack Query cache**: Data hooks return cached data on reopen (instant), transforms still deferred
- [ ] **DeckBuilderPane compatibility**: Same contentReady pattern works across all panes, no conflicts
- [ ] **Filter state independence**: Local filter state in each pane doesn't affect other panes
- [ ] **Theme/i18n changes**: Dialog reopens cleanly after language switch or theme change

### Regression Testing

After implementing the optimization, verify these existing features still work:

- [ ] **Keyword filter**: Selecting keywords filters gift list correctly
- [ ] **Search bar**: Typing in search filters gifts by name
- [ ] **Sorter**: "Tier First" and "Alphabetical" modes sort gifts correctly
- [ ] **Enhancement selection UI**: Enhancement level buttons (0, 1, 2, 3) render and respond to clicks
- [ ] **Reset button**: Clears all selections without closing dialog
- [ ] **Done button**: Closes dialog and commits selections to parent state
- [ ] **Mobile layout**: Dialog layout adapts to small screens (grid becomes stacked)

## Implementation Notes

### Pattern to Copy (from DeckBuilderPane.tsx)

**Step 1: Add state and effect (insert before data hooks)**
```typescript
const [contentReady, setContentReady] = useState(false)
useEffect(() => {
  if (open) {
    const frame = requestAnimationFrame(() => setContentReady(true))
    return () => cancelAnimationFrame(frame)
  } else {
    setContentReady(false)
  }
}, [open])
```

**Step 2: Guard the expensive transform**
```typescript
const gifts = useMemo<EGOGiftListItem[]>(() => {
  if (!contentReady) return []
  return Object.entries(spec).map(...)
}, [contentReady, spec, i18n])
```

### ComprehensiveGiftSelectorPane Special Consideration

The `specById` map (line 79) is used by cascade logic. It must also build after contentReady:

```typescript
const specById = useMemo(() => {
  if (!contentReady) return new Map()
  return new Map(Object.entries(spec))
}, [contentReady, spec])
```

### Measurement Strategy

Before and after optimization, measure:
1. **Time to first paint** (dialog border visible): Should be <50ms after
2. **Time to content render** (gifts visible): Can be 100-200ms after, acceptable
3. **Main thread blocking**: Should drop from 100-500ms to <16ms on first frame

Use `performance.mark()` and Chrome DevTools Performance profiler:
```typescript
// In button onClick handler
performance.mark('dialog-open-start')

// In Dialog onOpenChange
if (open) {
  requestAnimationFrame(() => {
    performance.mark('dialog-open-complete')
    performance.measure('dialog-open', 'dialog-open-start', 'dialog-open-complete')
  })
}
```

## Success Criteria

The optimization is successful if:
1. ✅ Dialog animation starts within 50ms of button click (measured)
2. ✅ No visible freeze or lag when opening panes
3. ✅ All existing functionality works (selection, filters, cascade, reset)
4. ✅ No new console errors or warnings
5. ✅ Manual testing passes all test cases
6. ✅ No performance regression on fast or slow devices
