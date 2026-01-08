# Research: EGO Gift Edit Pane Performance Optimization

## Spec Ambiguities

**None found.** Spec is complete with:
- Clear root cause (synchronous Object.entries().map() blocking render)
- Proven pattern identified (DeckBuilderPane.tsx lines 66-110)
- Exact line numbers for modifications
- Success criteria defined (50ms animation start)

---

## Spec-to-Code Mapping

**EGOGiftObservationEditPane.tsx (lines 43-61)**
- Add contentReady state before line 43
- Guard useMemo at lines 50-61 with `if (!contentReady) return []`
- Add contentReady to dependency array

**ComprehensiveGiftSelectorPane.tsx (lines 44-79)**
- Add contentReady state before line 44
- Guard gifts array useMemo (lines 66-76)
- Guard specById map useMemo (line 79) - required for cascade logic
- Add contentReady to both dependency arrays

**hooks/useContentReady.ts (optional future)**
- Extract pattern after both panes verified working
- Reduces duplication across DeckBuilderPane + 2 gift panes

---

## Spec-to-Pattern Mapping

**contentReady Pattern Source**: `DeckBuilderPane.tsx` lines 67-76

Pattern structure:
- useState(false) for contentReady flag
- useEffect with [open] dependency
- requestAnimationFrame schedules setContentReady(true)
- Cleanup cancels frame ID
- Reset to false when dialog closes

**useMemo Guard Pattern**: `DeckBuilderPane.tsx` lines 83-96, 98-110

Guard structure:
- Check `if (!contentReady) return []` or `return new Map()` at start
- Add contentReady as first dependency
- Original dependencies follow (spec, i18n)

**Filter Reset Timing**: Already correct in both panes
- useEffect([open]) runs before requestAnimationFrame
- Timing preserved, no modification needed

---

## Pattern Enforcement

| Modified File | MUST Read First | Pattern to Copy |
|---------------|-----------------|-----------------|
| EGOGiftObservationEditPane.tsx | DeckBuilderPane.tsx lines 66-110 | contentReady state + RAF + useMemo guard (single transform) |
| ComprehensiveGiftSelectorPane.tsx | DeckBuilderPane.tsx lines 66-110 | contentReady state + RAF + dual useMemo guards (gifts + specById) |

**Special consideration**: ComprehensiveGiftSelectorPane's specById map (line 79) must also be guarded because cascade selection logic depends on it.

---

## Existing Utilities

**Hooks checked**:
- useEGOGiftListData() - returns spec/i18n, NOT modifying (hook contract unchanged)
- useEGOGiftObservationData() - returns observation costs, NOT modifying
- useContentReady() - does NOT exist yet (can extract post-optimization)

**Constants checked**:
- MAX_OBSERVABLE_GIFTS - already used, no changes needed

**UI Components checked**:
- Dialog primitives from shadcn/ui - unchanged

---

## Gap Analysis

**Currently missing**:
- useContentReady hook (optional extraction target)
- contentReady pattern in EGOGiftObservationEditPane
- contentReady pattern in ComprehensiveGiftSelectorPane

**Needs modification**:
- EGOGiftObservationEditPane: +5 lines state/effect, 1 line useMemo guard logic
- ComprehensiveGiftSelectorPane: +5 lines state/effect, 2 useMemo guards

**Can reuse**:
- DeckBuilderPane pattern (exact copy)
- Data hooks unchanged (useEGOGiftListData, useEGOGiftObservationData)
- Child components (EGOGiftSelectionList likely handles empty array gracefully)

**No changes needed**:
- Parent Suspense boundaries (already in place)
- Filter reset logic (timing already correct)
- Selection state management (unchanged)
- Cascade selection logic (only specById needs guard)

---

## Testing Requirements

### Manual UI Tests

**Test 1: EGO Gift Observation Pane Opening**
- Navigate to /planner/md/new
- Click "EGO Gift Observation" section
- Verify dialog animates instantly (no freeze)
- Verify gifts appear during/after animation
- Select gifts, verify selection works
- Close and reopen, verify filters reset
- Verify previous selections preserved

**Test 2: Comprehensive Gift Selector Opening**
- Click "Comprehensive EGO Gift List" section
- Verify instant animation
- Select gift with recipe (e.g., 9088)
- Verify cascade auto-selects ingredients
- Change enhancement level, verify update
- Click same level again, verify deselect
- Click Reset, verify all clear

**Test 3: Rapid Open/Close Edge Case**
- Double-click section button quickly
- Open then close before animation completes
- Verify no flicker, no duplicate dialogs, no content flash

**Test 4: Slow Device Simulation**
- Chrome DevTools: Enable 6x CPU throttle
- Open Comprehensive Gift Selector
- Verify animation starts <50ms (immediate)
- Verify content appears with acceptable delay (100-200ms OK on slow device)

### Automated Functional Verification

**Timing tests**:
- Dialog animation starts within 50ms of button click (performance.mark measurement)
- Gifts array empty on first render frame
- Gifts array populated on second render frame

**State tests**:
- Filter reset fires before contentReady becomes true
- Selection state persists across pane open/close
- contentReady resets to false when dialog closes

**Feature tests**:
- Cascade selection works (ingredients auto-added)
- Enhancement toggle works (click twice = deselect)
- Max selection enforcement (10 gifts in Observation pane)
- useMemo re-computes only when dependencies change

### Edge Cases

**Close before contentReady fires**:
- Mock slow frame timing, close dialog immediately
- Verify setContentReady(false) cleans up, no stale content

**Rapid open/close stacking**:
- Trigger multiple open/close cycles quickly
- Verify only latest frame takes effect
- Verify frame cleanup prevents memory leaks

**Data fetch during animation**:
- Simulate new data arriving mid-animation
- Verify content updates smoothly, no layout shift

**Empty array handling**:
- Verify EGOGiftSelectionList renders gracefully with empty array

**Missing i18n data**:
- Verify gift name falls back to ID (existing behavior)

**Invalid gift spec**:
- Verify cascade logic handles missing specById entries gracefully

**Circular recipe references**:
- Verify visited Set prevents infinite loops (existing protection)

### Integration Points

**Suspense boundaries**:
- Verify PlannerMDNewPage Suspense wrapping still works
- Verify contentReady doesn't break useSuspenseQuery contract

**TanStack Query cache**:
- Verify cached data on reopen still defers transforms

**DeckBuilderPane compatibility**:
- Verify all three panes (DeckBuilder + 2 gift panes) work together

**Filter state independence**:
- Verify each pane's local filter state doesn't affect others

**Language switch**:
- Verify dialog reopens cleanly after i18n change

### Regression Testing

**Must still work**:
- Keyword filter updates gift list dynamically
- Search bar filters by name
- Sorter (Tier First / Alphabetical) sorts correctly
- Enhancement level buttons (0, 1, 2, 3) respond
- Reset button clears selections without closing
- Done button closes and commits
- Mobile layout adapts (grid becomes stacked)

---

## Technical Constraints

**React 19 Compiler compatibility**:
- Pattern uses standard hooks (useState, useEffect, useMemo)
- Compiler-friendly, proven in DeckBuilderPane

**requestAnimationFrame timing**:
- Frame ID must be cancelled in cleanup
- Pattern: `return () => cancelAnimationFrame(frame)`

**Suspense chain integrity**:
- useSuspenseQuery calls unchanged
- Only data transforms deferred (below Suspense boundary)

**Re-render cost**:
- Adding contentReady to useMemo increases renders by 1 per open
- Acceptable trade-off for 100-500ms performance gain

**Browser paint cycle coordination**:
- requestAnimationFrame runs before next paint (guaranteed timing)

**Cascade logic dependency**:
- specById map must also be guarded by contentReady
- Otherwise cascade selection fails on first frame

---

## Implementation Steps

**Step 1: EGOGiftObservationEditPane**
1. Read DeckBuilderPane.tsx lines 66-110
2. Add contentReady state + useEffect before data hooks
3. Modify gifts useMemo: add guard + contentReady dependency
4. Test manually (dialog animation timing)
5. Run automated tests (edge cases)

**Step 2: ComprehensiveGiftSelectorPane**
1. Apply same contentReady state + useEffect
2. Guard gifts array useMemo (line 66-76)
3. Guard specById useMemo (line 79) - critical for cascade
4. Test manually (animation + cascade selection)
5. Run automated tests (cascade edge cases)

**Step 3: Extract useContentReady Hook (Optional)**
1. After both panes verified working
2. Create hooks/useContentReady.ts
3. Extract useState + useEffect pattern
4. Replace inline implementations in all 3 panes
5. Verify no regressions

---

## Performance Measurement

**Before optimization (baseline)**:
- Time to dialog animation start: 100-500ms
- Main thread blocking on first frame: 100-500ms
- User perceives: freeze/lag

**After optimization (expected)**:
- Time to dialog animation start: <50ms
- Main thread blocking on first frame: <16ms (single frame @ 60fps)
- Time to content render: 100-200ms (acceptable, animation already started)
- User perceives: instant response

**Measurement approach**:
```
performance.mark('dialog-open-start') in button onClick
performance.mark('dialog-open-complete') in requestAnimationFrame callback
performance.measure('dialog-open', 'dialog-open-start', 'dialog-open-complete')
```

Use Chrome DevTools Performance profiler to verify:
- First frame paints dialog border (empty)
- Second frame paints gift cards
- Animation runs smoothly (60fps)

---

## Risk Assessment

**Low risk** - Pattern proven in DeckBuilderPane:
- No hook API changes (contract unchanged)
- No Suspense chain modifications
- No data flow changes
- No child component breaking changes (empty array handling already exists)

**Mitigation strategies**:
- Keep useSuspenseQuery calls unchanged (data loads in background)
- Build specById map after contentReady (cascade dependency)
- Verify filter reset timing preserved (useEffect([open]) runs first)
- Test edge cases (close before contentReady, rapid open/close)

**Rollback plan**:
- Remove contentReady state + useEffect (5 lines)
- Remove useMemo guards (2 lines)
- Revert to synchronous transforms
