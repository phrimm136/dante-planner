# Execution Plan: EGO Gift Edit Pane Performance Optimization

## Planning Gaps

**No critical gaps found.** All necessary information is available from research.

**Minor clarifications (non-blocking):**
1. Test file creation for ComprehensiveGiftSelectorPane - Relying on manual verification (none exist currently)
2. useContentReady hook extraction timing - Deferred to future task (prevents scope creep)

---

## Execution Overview

**Strategy:** Apply the proven deferred loading pattern from DeckBuilderPane.tsx to two EGO Gift panes in sequence. Each pane follows a simple 3-step modification: (1) add contentReady state management, (2) guard expensive transforms in useMemo, (3) verify timing and functionality.

**High-Level Phases:**
1. **Phase 1: EGO Gift Observation Pane** - Apply pattern to simpler pane first (single transform)
2. **Phase 2: Comprehensive Gift Selector Pane** - Apply pattern to complex pane (dual transforms + cascade logic)
3. **Phase 3: Testing & Verification** - Manual UI testing, automated functional tests, performance measurement
4. **Phase 4: Optional Extraction** - Create useContentReady hook to reduce duplication (future task)

**Risk Level:** Low (proven pattern, isolated components, existing tests, child components handle empty arrays)

---

## Dependency Analysis (Senior Thinking)

### Files Being Modified

| File | Impact Level | Depends On | Used By |
|------|--------------|------------|---------|
| `EGOGiftObservationEditPane.tsx` | Low-Medium | `useEGOGiftListData` (unchanged), `useEGOGiftObservationData` (unchanged) | `PlannerMDNewPage.tsx` (Suspense boundary at line 810), `EGOGiftSelectionList` (child - handles empty array) |
| `ComprehensiveGiftSelectorPane.tsx` | Low-Medium | `useEGOGiftListData` (unchanged) | `PlannerMDNewPage.tsx` (Suspense boundary at line 824), `EGOGiftSelectionList` (child - handles empty array) |

### Ripple Effect Map

**Direct consumers (LOW risk):**
- `PlannerMDNewPage.tsx`: Passes `open` prop and selection handlers. No changes needed - panes remain controlled components.
- `EGOGiftSelectionList.tsx`: Receives `gifts` array as prop. Already handles empty array (lines 88-96) with "No gifts match" message.
- `EGOGiftObservationSelection.tsx`, `ComprehensiveGiftSummary.tsx`: Summary components, don't interact with panes directly.

**Shared dependencies (NO changes needed):**
- `useEGOGiftListData`: Hook contract unchanged, returns same `{ spec, i18n }` structure. Consumers (both panes) defer transforms, not the hook.
- `useEGOGiftObservationData`: Hook contract unchanged, returns observation costs. No modifications.
- `EGOGiftSelectionList`: Receives `gifts` array. Empty array on first frame is acceptable (renders "No gifts" message, then populates on second frame).

**Parent Suspense boundaries (NO impact):**
- `PlannerMDNewPage.tsx` lines 810, 824: Suspense boundaries wrap panes. `useSuspenseQuery` calls remain unchanged, so Suspense contract preserved.

**Filter state reset timing (NO impact):**
- `useEffect([open])` runs synchronously before `requestAnimationFrame` callback.
- Filter reset happens before contentReady=true, timing preserved.

### High-Risk Modifications

**None.** Both files are isolated pane components with:
- No shared utility functions extracted
- No prop interface changes
- No hook contract changes
- Proven pattern from DeckBuilderPane.tsx (already in production)

**ComprehensiveGiftSelectorPane special consideration:**
- `specById` map (line 79) must also be guarded by contentReady
- Cascade selection logic depends on this map existing
- Mitigation: Guard `specById` useMemo same as `gifts` useMemo

---

## Execution Order

### Phase 1: EGO Gift Observation Pane (Simpler, Single Transform)

1. **Read DeckBuilderPane.tsx pattern**: Lines 66-110 (contentReady state + useMemo guards)
   - Depends on: none
   - Enables: F1 (understanding proven pattern)

2. **Modify EGOGiftObservationEditPane.tsx - Add contentReady state**: Insert before line 43
   - Depends on: Step 1
   - Enables: F2 (deferred rendering infrastructure)
   - Code: Add useState(false) + useEffect with requestAnimationFrame

3. **Modify EGOGiftObservationEditPane.tsx - Guard gifts useMemo**: Lines 50-61
   - Depends on: Step 2
   - Enables: F3 (deferred transform execution)
   - Code: Add `if (!contentReady) return []` guard, add contentReady to deps

4. **Manual Test - Observation Pane Timing**: Dialog animation starts <50ms
   - Depends on: Step 3
   - Enables: F4 (verification of Phase 1 success)
   - Verify: No freeze, gifts appear during/after animation

5. **Manual Test - Observation Pane Functionality**: Selection, filters, reset work
   - Depends on: Step 4
   - Enables: F5 (regression prevention)
   - Verify: Select gifts, reopen, filters reset, selection persists

### Phase 2: Comprehensive Gift Selector Pane (Complex, Dual Transforms + Cascade)

6. **Modify ComprehensiveGiftSelectorPane.tsx - Add contentReady state**: Insert before line 44
   - Depends on: Step 5 (Phase 1 complete)
   - Enables: F6 (deferred rendering for complex pane)
   - Code: Same useState + useEffect pattern

7. **Modify ComprehensiveGiftSelectorPane.tsx - Guard gifts transform**: Lines 66-76
   - Depends on: Step 6
   - Enables: F7 (deferred gifts array build)
   - Code: Wrap in useMemo with `if (!contentReady) return []`

8. **Modify ComprehensiveGiftSelectorPane.tsx - Guard specById map**: Line 79
   - Depends on: Step 7
   - Enables: F8 (cascade selection dependency)
   - Code: Add `if (!contentReady) return new Map()` guard, add contentReady to deps

9. **Manual Test - Comprehensive Pane Timing**: Dialog animation starts <50ms
   - Depends on: Step 8
   - Enables: F9 (verification of Phase 2 timing)
   - Verify: Instant animation, no freeze

10. **Manual Test - Comprehensive Pane Cascade Selection**: Recipe auto-selects ingredients
    - Depends on: Step 9
    - Enables: F10 (critical feature verification)
    - Verify: Select gift 9088, ingredients auto-add, enhancement toggle works

### Phase 3: Testing & Verification

11. **Run Existing Automated Tests**: EGOGiftObservationEditPane.test.tsx
    - Depends on: Steps 3, 5
    - Enables: F11 (automated regression detection)
    - Verify: All existing tests pass

12. **Manual Test - Edge Cases**: Rapid open/close, close before contentReady
    - Depends on: Steps 5, 10
    - Enables: F12 (edge case coverage)
    - Verify: No flicker, no memory leaks, cleanup works

13. **Performance Measurement**: Chrome DevTools profiling
    - Depends on: Steps 4, 9
    - Enables: F13 (quantified performance gain)
    - Measure: Frame timing before/after, verify <50ms start

14. **Manual Test - Slow Device Simulation**: 6x CPU throttle
    - Depends on: Steps 4, 9
    - Enables: F14 (low-end device verification)
    - Verify: Animation still starts instantly, content loads with acceptable delay

### Phase 4: Optional Future Extraction (Deferred)

15. **Create useContentReady Hook** (optional, not in current scope)
    - Depends on: Steps 3, 8 (both panes working)
    - Enables: F15 (DRY principle, code reuse)
    - Code: Extract useState + useEffect pattern to hooks/useContentReady.ts

16. **Replace Inline Implementations** (optional, not in current scope)
    - Depends on: Step 15
    - Enables: F16 (centralized pattern maintenance)
    - Refactor: DeckBuilderPane + 2 gift panes use hook

---

## Test Steps (MANDATORY)

### Unit Tests (Automated)

**Step 11: Run existing EGOGiftObservationEditPane.test.tsx**
- Location: `frontend/src/components/egoGift/EGOGiftObservationEditPane.test.tsx`
- Coverage: Dialog visibility, filter controls, selection, max limit enforcement
- Expected: All existing tests pass (no regressions)

**Future tests to add (optional, post-implementation):**
- Test contentReady timing (mock requestAnimationFrame)
- Test gifts array empty on first render
- Test cleanup on rapid open/close

### Integration Tests (Manual)

**Step 4-5: Observation Pane Tests**
- Open pane, verify instant animation (<50ms)
- Select gifts, verify selection works
- Close and reopen, verify filters reset
- Verify previous selection persists

**Step 9-10: Comprehensive Pane Tests**
- Open pane, verify instant animation
- Select gift with recipe (9088), verify cascade
- Change enhancement level, verify update
- Click same level, verify deselect
- Click Reset, verify all clear

**Step 12: Edge Case Tests**
- Double-click pane button (rapid open)
- Open then close before animation completes
- Verify no flicker, no duplicate dialogs

**Step 14: Performance Tests**
- Enable 6x CPU throttle in DevTools
- Open both panes, verify animation starts immediately
- Verify content appears with acceptable delay (100-200ms OK)

---

## Verification Checkpoints

**After Step 3 (Observation pane modified):**
- Verify F2, F3: Dialog opens instantly, gifts populate after animation
- Method: Manual click test, Chrome DevTools Performance tab

**After Step 5 (Observation pane functionality verified):**
- Verify F4, F5: No regressions, all features work
- Method: Manual test (select, filter, reset, reopen)

**After Step 8 (Comprehensive pane transforms guarded):**
- Verify F6, F7, F8: All three transforms deferred (gifts, specById)
- Method: Code review, check useMemo guards present

**After Step 10 (Comprehensive pane cascade verified):**
- Verify F9, F10: Instant animation, cascade selection works
- Method: Manual test (select gift 9088, verify ingredients)

**After Step 11 (Automated tests pass):**
- Verify F11: No test regressions
- Method: Run `npm test` or vitest command

**After Step 13 (Performance measured):**
- Verify F13: Quantified improvement (baseline 100-500ms → <50ms)
- Method: performance.mark/measure in code, DevTools profiler

---

## Risk Mitigation (from instructions.md Risk Assessment)

| Risk | Step Affected | Mitigation |
|------|---------------|------------|
| **Breaking Suspense Chain** | Steps 2-3, 6-8 | Keep useSuspenseQuery calls unchanged, only defer transforms below Suspense boundary. Verify parent Suspense boundaries still work (PlannerMDNewPage lines 810, 824). |
| **Filter State Lost on Reopen** | Steps 2, 6 | useEffect([open]) runs before requestAnimationFrame, timing preserved. Verify filter reset behavior in Steps 5, 10. |
| **Cascade Breaks in Comprehensive Pane** | Step 8 | Guard specById map with contentReady. Build map after contentReady=true so cascade logic has dependency. Test cascade selection in Step 10. |
| **React Compiler Misses Optimizations** | All steps | Pattern uses standard hooks (useState, useEffect, useMemo) - compiler-friendly. No manual memo/useCallback, React 19 handles concurrent features. |
| **User Closes Before contentReady** | Steps 2, 6 | useEffect cleanup cancels requestAnimationFrame. setContentReady(false) resets state. Test in Step 12 (edge cases). |
| **Rapid Open/Close Stacking** | Steps 2, 6 | cancelAnimationFrame in cleanup prevents memory leaks. Only latest frame takes effect. Test in Step 12 (rapid double-click). |
| **Empty Array Breaks Child Components** | Steps 3, 7 | EGOGiftSelectionList already handles empty array gracefully (lines 88-96) - renders "No gifts match" message. Verified in code review. |

---

## Dependency Verification Steps

**After Step 3 (Observation pane modified):**
- Verify: `useEGOGiftListData` hook still returns { spec, i18n } unchanged
- Verify: `EGOGiftSelectionList` receives empty array on first frame, renders "No gifts" message
- Verify: Parent `PlannerMDNewPage.tsx` Suspense boundary (line 810) still suspends on initial load

**After Step 8 (Comprehensive pane modified):**
- Verify: `useEGOGiftListData` hook still returns { spec, i18n } unchanged (same hook as Observation pane)
- Verify: `specById` map builds correctly after contentReady (check in Step 10 cascade test)
- Verify: Parent `PlannerMDNewPage.tsx` Suspense boundary (line 824) still works

**After Step 11 (Tests pass):**
- Verify: Existing test mocks still work (useEGOGiftListData, useEGOGiftObservationData)
- Verify: Test expectations unchanged (selection, filters, reset behavior)

---

## Rollback Strategy

**If Step 3 fails (Observation pane broken):**
- Revert: Remove contentReady state + useEffect (5 lines)
- Revert: Remove useMemo guard (1 line change)
- Safe state: Original synchronous behavior restored

**If Step 8 fails (Comprehensive pane broken):**
- Revert: Remove contentReady state + useEffect (5 lines)
- Revert: Remove useMemo guards on gifts and specById (2 line changes)
- Safe state: Original synchronous behavior restored

**If Step 10 fails (Cascade selection broken):**
- Root cause: specById map not built correctly
- Debug: Check contentReady guard on line 79, verify dependency array
- Fallback: Revert Step 8 changes, investigate specById map timing

**If Step 11 fails (Tests broken):**
- Root cause: Test expectations assume synchronous rendering
- Fix: Update tests to handle deferred rendering (mock requestAnimationFrame)
- Fallback: Revert Steps 2-3, keep synchronous for tests

**Safe stopping points:**
- After Step 5: Observation pane optimized, Comprehensive pane unchanged
- After Step 10: Both panes optimized, tests pending
- After Step 14: All verification complete, extraction deferred

---

## Critical Files for Implementation

- `/frontend/src/components/deckBuilder/DeckBuilderPane.tsx` - **Pattern source**: Lines 66-110 contain proven contentReady pattern to copy
- `/frontend/src/components/egoGift/EGOGiftObservationEditPane.tsx` - **Primary target**: Lines 43-61 need contentReady state + useMemo guard (simpler case, single transform)
- `/frontend/src/components/egoGift/ComprehensiveGiftSelectorPane.tsx` - **Primary target**: Lines 44-79 need contentReady state + dual useMemo guards (complex case, gifts + specById)
- `/frontend/src/components/egoGift/EGOGiftSelectionList.tsx` - **Child component verification**: Lines 88-96 show empty array handling (ensures compatibility)
- `/frontend/src/components/egoGift/EGOGiftObservationEditPane.test.tsx` - **Test pattern reference**: Existing unit tests to run for regression detection (Step 11)
