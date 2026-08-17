# Execution Status: EGO Detail Page Refactoring

Last Updated: 2026-01-08 (Phase 4 Complete)
Current Step: 9/14
Current Phase: Phase 4 (Page Refactor)

---

## Milestones

- [x] M1: Phase 1-2 Complete (Data + Component layers)
- [x] M2: Phase 3 Complete (Shared component extension)
- [x] M3: Phase 4 Complete (Page refactor)
- [ ] M4: Phase 5 Complete (Verification passed)
- [ ] M5: Code Review Passed

---

## Step Log

- Step 1: ✅ done - Split useEGODetailData hook (useEGODetailSpec + useEGODetailI18n)
- Step 2: ⏭️ skipped - Unit tests (deferred to Step 10)
- Step 3: ✅ done - Create EGOHeaderI18n.tsx
- Step 4: ✅ done - Create SkillI18n.tsx (SkillsSectionI18n)
- Step 5: ✅ done - Create PassiveI18n.tsx (PassiveCardWithSuspense)
- Step 6: ⏭️ skipped - Unit tests (deferred to Step 10)
- Step 7: ✅ done - Extend MobileDetailTabs (thirdTabContent prop rename)
- Step 8: ✅ done - Update IdentityDetailPage props
- Step 9: ✅ done - Refactor EGODetailPage (shell pattern, state, passive logic, i18n components)
- Step 10: ⏳ pending - Integration tests for EGO detail
- Step 11: ⏳ pending - Manual UI: Threadspin buttons
- Step 12: ⏳ pending - Manual UI: Language stability
- Step 13: ⏳ pending - Manual UI: Passive locking
- Step 14: ⏳ pending - Manual UI: Erosion tab disabled

---

## Feature Status

### Core Features
- [ ] F1: Language change only suspends text elements - Verify: Switch language, threadspin state persists
- [ ] F2: Granular Suspense boundaries - Verify: Only text shows skeleton on language change
- [ ] F3: Erosion tab disabled when no erosion skills - Verify: Tab visible but dimmed, non-clickable
- [ ] F4: Skills merge data based on threadspin level - Verify: Click threadspin 1, skills show level 1 data
- [ ] F5: Passives show locked preview from higher threadspins - Verify: Threadspin 1 shows locked passive
- [ ] F6: Threadspin state management with tier buttons (1-4) - Verify: Click buttons, state updates
- [ ] F7: Skill type tabs persist on language change - Verify: Select Erosion, switch language, tab persists
- [ ] F8: Mobile tabs (Skills, Passives) - Verify: Resize to mobile, 2-tab layout works

### Edge Cases
- [ ] E1: Empty erosion array doesn't crash - Verify: Load EGO without erosion, page renders
- [ ] E2: Passive inheritance works - Verify: Threadspin 3 inherits from threadspin 2
- [ ] E3: String passive IDs handled - Verify: No type errors, IDs render

### Integration
- [ ] I1: DetailPageLayout integration - Verify: Left/right column ratio correct
- [ ] I2: DetailEntitySelector integration - Verify: 4 tier buttons render, no level slider

### Dependency Verification
- [ ] D1: IdentityDetailPage works after MobileDetailTabs change - Verify: 3 tabs render
- [ ] D2: Existing EGO components work - Verify: EGOHeader, SinCostPanel render

---

## Testing Checklist

### Automated Tests

**Unit Tests (Step 2):**
- [ ] UT1: useEGODetailSpec - Query key structure (no language)
- [ ] UT2: useEGODetailI18n - Query key includes language
- [ ] UT3: useEGODetailData - Backward compatible
- [ ] UT4: Spec query staleTime is 7 days
- [ ] UT5: I18n query staleTime is 7 days

**Component Tests (Step 6):**
- [ ] CT1: EGOHeaderI18n - Suspends while loading
- [ ] CT2: EGOSkillCardI18n - Suspense per skill
- [ ] CT3: EGOPassiveCardI18n - Two-tier Suspense
- [ ] CT4: EGOPassiveCardI18n - isLocked applies opacity-50
- [ ] CT5: EGOPassiveCardI18n - Lock icon when isLocked=true

**Integration Tests (Step 10):**
- [ ] IT1: Threadspin state updates on button click
- [ ] IT2: Skill type state switches correctly
- [ ] IT3: getEffectivePassives walks backwards
- [ ] IT4: getLockedPassives finds higher-tier passives
- [ ] IT5: hasErosion boolean detects empty array

### Manual Verification

**Threadspin Button Selection (Step 11):**
- [ ] MV1: 4 tier buttons render (1, 2, 3, 4)
- [ ] MV2: Default threadspin 4 highlighted
- [ ] MV3: Click button 1 - highlight moves, content updates
- [ ] MV4: Click through 1→2→3→4 - state updates

**Language Change Stability (Step 12):**
- [ ] MV5: Set threadspin 2, select Erosion tab
- [ ] MV6: Switch EN → KR - only name shows skeleton
- [ ] MV7: Header image, sin panels stay visible
- [ ] MV8: Threadspin 2 persists after language change
- [ ] MV9: Erosion tab selection persists
- [ ] MV10: Switch to JP, then EN - threadspin still at 2

**Passive Locking Logic (Step 13):**
- [ ] MV11: EGO 20101, threadspin 1 - passive in Locked section
- [ ] MV12: Locked passive has opacity-50 and lock icon
- [ ] MV13: Locked passive name and description visible
- [ ] MV14: Threadspin 2 - passive moves to Effective section
- [ ] MV15: Lock icon disappears, opacity returns to full
- [ ] MV16: Threadspin 3 - passive in Effective (inherited)
- [ ] MV17: Threadspin 4 - passive in Effective (inherited)

**Erosion Tab Disabled (Step 14):**
- [ ] MV18: EGO without erosion - load successfully
- [ ] MV19: Awakening tab active by default
- [ ] MV20: Erosion tab visible but dimmed (opacity-50)
- [ ] MV21: Erosion tab non-clickable
- [ ] MV22: No hover effect (cursor-not-allowed)

---

## Summary

**Progress:**
- Steps: 0/14 complete (0%)
- Features: 0/8 verified (0%)
- Edge Cases: 0/3 verified (0%)
- Integration: 0/2 verified (0%)
- Dependencies: 0/2 verified (0%)
- Automated Tests: 0/15 passed (0%)
- Manual Tests: 0/22 passed (0%)

**Overall: 0% complete**
