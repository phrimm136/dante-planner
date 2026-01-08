# Execution Plan: EGO Detail Page Refactoring

## Planning Gaps
**NONE** - Research is complete. All patterns, files, and dependencies are mapped.

---

## Execution Overview

Refactor EGO detail page from inline monolithic structure to component-based architecture with granular Suspense boundaries. Split data hook into spec (stable) and i18n (suspending) variants. Add threadspin state management with tier button selection. Implement passive locking logic with inheritance. Extend mobile tabs to support disabled states.

**Result:** Language changes only suspend text elements while layout, images, and state persist.

**Total estimated changes:**
- 3 new components (~220 lines)
- 2 hooks split (~90 lines)
- 1 page refactor (~280 lines)
- 1 shared component extension (~25 lines)
- 1 page prop update (~5 lines)

---

## Dependency Analysis

### Files Being Modified

| File | Impact Level | Depends On | Used By |
|------|--------------|------------|---------|
| `useEGODetailData.ts` | Medium | `EGODataSchema`, `EGOI18nSchema`, `queryOptions` | `EGODetailPage.tsx` (current), new i18n components (after) |
| `EGODetailPage.tsx` | High (isolated) | All new components, hooks, layout components | Router only |
| `MobileDetailTabs.tsx` | Medium (shared) | `shadcn/ui Tabs` | `IdentityDetailPage.tsx`, `EGODetailPage.tsx` |
| `IdentityDetailPage.tsx` | Low | `MobileDetailTabs` (prop rename) | Router only |
| `EGOHeaderI18n.tsx` (new) | Low | `useEGODetailI18n`, `EGOHeader` | `EGODetailPage.tsx` |
| `EGOSkillCardI18n.tsx` (new) | Low | `useEGODetailI18n`, `EGOSkillCard` | `EGODetailPage.tsx` |
| `EGOPassiveCardI18n.tsx` (new) | Low | `useEGODetailI18n`, `FormattedDescription` | `EGODetailPage.tsx` |

### Ripple Effect Map

**Hook split ripple:**
- If `useEGODetailData` changes → backward compat preserved with deprecated combined hook
- If query keys change → no cache collision (different key structures)

**MobileDetailTabs ripple:**
- If prop names change (`sanityContent` → `thirdTabContent`) → `IdentityDetailPage` MUST be updated in same commit
- If disabled support added → no breaking change (optional prop)

**DetailEntitySelector ripple:**
- Already supports `entityType="ego"` → no changes needed
- Tier button behavior stable → no modifications required

### High-Risk Modifications

**`MobileDetailTabs.tsx` - Prop rename is BREAKING:**
- Risk: IdentityDetailPage breaks if not updated
- Mitigation: Update IdentityDetailPage in same commit (Step 8)
- Verification: Test Identity detail page after update

**`EGODetailPage.tsx` - Complete page refactor:**
- Risk: Existing routing/URL params break
- Mitigation: Keep route structure identical, only change internal implementation
- Verification: Test page load with existing EGO IDs

**Passive logic implementation:**
- Risk: Inheritance walk-back logic incorrect, passives missing/duplicated
- Mitigation: Copy proven Identity implementation, add test cases
- Verification: Manual test threadspin transitions 1→2→3→4

---

## Execution Order

### Phase 1: Data Layer Foundation

**1. Split `useEGODetailData` hook into spec + i18n variants**
- Depends on: None
- Enables: F1 (Language change stability), F2 (Granular Suspense)
- Action: Create `useEGODetailSpec()` and `useEGODetailI18n()` hooks
- Pattern: Copy from `useIdentityDetailData.ts` lines 1-98
- Keep deprecated `useEGODetailData()` for backward compatibility

**2. Write unit tests for hook split**
- Depends on: Step 1
- Enables: Test coverage for data layer
- Action: Test query key structure, stale time, validation
- Verify: Spec query has no language key, i18n query includes language

### Phase 2: Component Layer

**3. Create `EGOHeaderI18n.tsx` component**
- Depends on: Step 1 (needs `useEGODetailI18n`)
- Enables: F2 (Granular Suspense for header name)
- Action: Wrapper component that fetches i18n and passes name to `EGOHeader`
- Pattern: Copy from `IdentityHeaderI18n.tsx`

**4. Create `EGOSkillCardI18n.tsx` component**
- Depends on: Step 1 (needs `useEGODetailI18n`)
- Enables: F2 (Granular Suspense for skill text), F4 (Skill rendering)
- Action: Wrapper that maps skills, fetches i18n, renders with Suspense
- Pattern: Copy from `SkillI18n.tsx` (Identity)

**5. Create `EGOPassiveCardI18n.tsx` component**
- Depends on: Step 1 (needs `useEGODetailI18n`)
- Enables: F2 (Granular Suspense for passive text), F5 (Passive locking)
- Action: Two-tier Suspense (name + desc), accept `isLocked` prop, string IDs
- Pattern: Copy from `PassiveI18n.tsx` lines 87-136, simplify (no conditions, no variant logic)

**6. Write unit tests for i18n wrapper components**
- Depends on: Steps 3, 4, 5
- Enables: Test coverage for component layer
- Action: Test Suspense boundaries, prop passing, fallback states
- Verify: Each component suspends independently

### Phase 3: Shared Component Extension

**7. Extend `MobileDetailTabs.tsx` with disabled tabs support**
- Depends on: None
- Enables: F3 (Erosion tab disabled state)
- Action: Add `disabled` prop array, rename `sanityContent` → `thirdTabContent`
- Note: Breaking change - must update Identity in next step

**8. Update `IdentityDetailPage.tsx` prop names**
- Depends on: Step 7 (MobileDetailTabs prop rename)
- Enables: Identity page compatibility
- Action: Change `sanityContent` → `thirdTabContent` in MobileDetailTabs usage
- Verify: Identity detail page still renders correctly

### Phase 4: Page Refactor

**9. Refactor `EGODetailPage.tsx` to shell pattern with state management**
- Depends on: Steps 1, 3, 4, 5, 7 (all data hooks and components ready)
- Enables: F1, F6, F7 (Language stability, Threadspin state, Skill type tabs)
- Action:
  - Replace `useEGODetailData()` with `useEGODetailSpec()` in shell
  - Add `threadspin` state (1-4, default 4)
  - Add `skillType` state ('awaken' | 'erosion', default 'awaken')
  - Implement `getEffectivePassives()` - walk backwards from current threadspin
  - Implement `getLockedPassives()` - find passives in higher threadspins (no variant filtering)
  - Use DetailEntitySelector with `entityType="ego"`, tier button callbacks
  - Use SkillTabButton with `isLocked={!hasErosion}` for erosion tab
  - Replace inline skill/passive JSX with i18n wrapper components
  - Add MobileDetailTabs with 2 tabs (Skills, Passives)
- Pattern: Copy structure from `IdentityDetailPage.tsx` lines 34-411
- Critical: Passive logic must handle string IDs (not number like Identity)

**10. Write integration tests for EGO detail page**
- Depends on: Step 9
- Enables: End-to-end verification
- Action: Test threadspin button clicks, skill type switching, passive locking transitions
- Verify: State persists on language change, locked passives move to effective

### Phase 5: Verification & Testing

**11. Manual UI verification - Threadspin button selection**
- Depends on: Step 9
- Action: Click threadspin buttons 1-4, verify highlight and content updates
- Verify: Default is button 4, clicking 1 updates passives/skills

**12. Manual UI verification - Language change stability**
- Depends on: Step 9
- Action: Set threadspin 2, switch language EN→KR→JP
- Verify: Only name shows skeleton, tier buttons/sin panels stay visible, threadspin 2 persists

**13. Manual UI verification - Passive locking logic**
- Depends on: Step 9
- Action: Test EGO 20101 (passive unlocks at threadspin 2)
- Verify: Threadspin 1 shows locked passive with lock icon + opacity-50
- Verify: Threadspin 2 moves passive to effective section
- Verify: Threadspin 3-4 keeps passive in effective (inheritance)

**14. Manual UI verification - Erosion tab disabled state**
- Depends on: Step 9
- Action: Test EGO without erosion skills
- Verify: Erosion tab visible but dimmed (opacity-50), non-clickable, cursor-not-allowed

---

## Verification Checkpoints

**After Step 1 (Hook split):**
- Verify F1 partial: Spec query key has no language component
- Method: Inspect query key structure, trigger language change, verify no re-fetch

**After Step 6 (i18n components ready):**
- Verify F2 partial: Components suspend independently
- Method: Test each component in isolation with Suspense boundary

**After Step 8 (MobileDetailTabs extended):**
- Verify: Identity detail page still works correctly
- Method: Load Identity detail page, check 3-tab layout, switch tabs

**After Step 9 (Page refactor complete):**
- Verify F1: Language change doesn't re-suspend shell (threadspin state persists)
- Verify F2: Only text elements show loading skeletons on language change
- Verify F3: Erosion tab disabled when no erosion skills
- Verify F4: Skills merge data correctly based on threadspin
- Verify F5: Passives show locked previews from higher threadspins
- Verify F6: Threadspin buttons (1-4) clickable, state updates correctly
- Verify F7: Skill type tabs switchable, state persists on language change
- Verify F8: Mobile tabs render 2 tabs (Skills, Passives)
- Method: Manual UI tests (Steps 11-14)

**After Step 14 (All verification complete):**
- Verify E1: Empty erosion array doesn't crash
- Verify E2: Passive inheritance works for all edge cases
- Verify I1: DetailPageLayout integration correct
- Verify I2: DetailEntitySelector integration correct
- Verify D1: Identity detail page still works after MobileDetailTabs change
- Method: Regression testing

---

## Risk Mitigation

| Risk | Step Affected | Mitigation |
|------|---------------|------------|
| MobileDetailTabs breaking change | Step 7, 8 | Update IdentityDetailPage in same commit, test Identity page immediately |
| Passive inheritance logic incorrect | Step 9 | Copy proven Identity implementation, test with EGO 20101, verify inheritance |
| Passive ID type mismatch (string vs number) | Step 5, 9 | Accept `passiveId: string` throughout EGO components, no type coercion |
| Empty erosion skills crash | Step 9 | Add `hasErosion` boolean check, use `isLocked` prop on SkillTabButton |
| Threadspin state reset on language change | Step 9 | Use `useEGODetailSpec()` (no language key) in shell component, verify with logging |
| Query cache collision | Step 1 | Use different query keys: `['ego', id]` vs `['ego', id, 'i18n', language]` |
| Locked passives showing in both sections | Step 9 | Check `effectivePassives` Set before adding to locked |

---

## Dependency Verification Steps

**After Step 8 (MobileDetailTabs modified):**
- Test Identity detail page: Load page, verify 3 tabs render, switch between tabs
- Test mobile view: Resize to <768px, verify tabs work

**After Step 9 (EGODetailPage refactor):**
- Test DetailPageLayout: Verify left column and right column render correctly
- Test DetailEntitySelector: Verify 4 tier buttons render, clicking updates state
- Test MobileDetailTabs: Verify 2 tabs render on mobile, content switches correctly

---

## Rollback Strategy

**Safe stopping points:**
- After Phase 1: Hook split complete, old hook still works - safe to deploy
- After Phase 2: New components ready but not used yet - safe to deploy
- After Phase 3: MobileDetailTabs extended, Identity updated - safe to deploy
- After Phase 4 Step 9: Full refactor complete - test thoroughly before deploy

**If Step 9 fails (page refactor):**
1. Keep Step 1 (hook split) - backward compatible
2. Keep Steps 3-5 (new components) - unused, no harm
3. Keep Steps 7-8 (MobileDetailTabs extension) - Identity still works
4. Revert EGODetailPage.tsx to previous version
5. Debug offline, re-attempt refactor

**If Step 8 fails (Identity breaks):**
1. Revert Step 7 (MobileDetailTabs) to restore `sanityContent` prop
2. Fix Identity page issue
3. Re-apply Step 7 and 8 together

**If tests fail in Phase 5:**
- Do NOT deploy to production
- Fix issues identified in manual verification
- Re-run all verification checkpoints
