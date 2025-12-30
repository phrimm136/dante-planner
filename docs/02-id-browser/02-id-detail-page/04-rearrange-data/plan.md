# Execution Plan: Modular Detail Page Layout

## Planning Gaps (STOP if any)

**No critical gaps.** All required information is present:
- Instructions.md provides clear requirements and priority order
- Existing patterns documented (TierLevelSelector, EGOGiftEnhancementSelector)
- shadcn/ui Tabs component exists

**Minor clarifications (not blocking):**
- shadcn/ui Slider may need installation via CLI
- ScrollArea may need installation or can use native CSS overflow

---

## Execution Overview

Restructure Identity detail page with modular two-column layout (4:6 ratio):
1. Desktop: Two-column layout with sticky uptie/level selector
2. Mobile: Tab-based navigation (Info/Skills/Passives)
3. Reusable components for EGO and EGO Gift pages

**Strategy:** Foundation first, then components, then integration, then tests.

---

## Execution Order

### Phase 1: Foundation (Constants, Types)

1. **[constants.ts]**: Add DETAIL_PAGE constants and DetailEntityType
   - Depends on: none
   - Enables: F1 (column ratio), F4 (selector defaults)

2. **[shadcn CLI - slider]**: Install shadcn/ui Slider component
   - Depends on: none
   - Enables: F3 (level slider)
   - Command: `npx shadcn@latest add slider`

3. **[shadcn CLI - scroll-area]**: Install shadcn/ui ScrollArea (if needed)
   - Depends on: none
   - Enables: F5 (scrollable right panel)

### Phase 2: Core Components

4. **[DetailEntitySelector.tsx]**: Create uptie/level selector
   - Depends on: Step 1, 2
   - Enables: F2 (uptie buttons), F3 (level slider), F4 (sticky)
   - Location: `/frontend/src/components/common/DetailEntitySelector.tsx`
   - Pattern: TierLevelSelector.tsx, EGOGiftEnhancementSelector.tsx

5. **[DetailRightPanel.tsx]**: Create scrollable right container
   - Depends on: Step 4
   - Enables: F5 (scrollable), F4 (sticky selector)
   - Location: `/frontend/src/components/common/DetailRightPanel.tsx`

6. **[DetailLeftPanel.tsx]**: Create modular left container
   - Depends on: Step 1
   - Enables: F6 (entity-specific slots)
   - Location: `/frontend/src/components/common/DetailLeftPanel.tsx`

7. **[MobileDetailTabs.tsx]**: Create tab wrapper for mobile
   - Depends on: Step 1, existing tabs.tsx
   - Enables: F7 (mobile tabs), F8 (tab switching)
   - Location: `/frontend/src/components/common/MobileDetailTabs.tsx`

### Phase 3: Layout Integration

8. **[DetailPageLayout.tsx]**: Refactor for 4:6 ratio and mobile
   - Depends on: Steps 5, 6, 7
   - Enables: F1 (40%/60%), F9 (responsive)
   - File: `/frontend/src/components/common/DetailPageLayout.tsx`

### Phase 4: Page Refactoring

9. **[IdentityDetailPage.tsx]**: Refactor to use new components
   - Depends on: Step 8
   - Enables: All features (F1-F10)
   - Key changes:
     - Add useState for uptie (default 4) and level (default MAX_LEVEL)
     - Move Sanity section from left to right column
     - Use DetailEntitySelector for controls

### Phase 5: Tests

10. **[DetailEntitySelector.test.tsx]**: Unit tests for selector
    - Depends on: Step 4
    - Enables: UT1-UT3

11. **[DetailPageLayout.test.tsx]**: Unit tests for layout
    - Depends on: Step 8
    - Enables: UT4-UT5

12. **[IdentityDetailPage.test.tsx]**: Integration tests
    - Depends on: Step 9
    - Enables: UT6-UT7

### Phase 6: Verification

13. **[Manual Verification]**: Run through all test cases
    - Depends on: Steps 9-12

---

## Verification Checkpoints

| After Step | Verify | Method |
|------------|--------|--------|
| Step 1 | Constants compile | `yarn tsc --noEmit` |
| Step 2-3 | Components installed | Check `/components/ui/` |
| Step 4 | Selector renders | Mount in test, verify 4 buttons |
| Step 5 | Sticky selector | Visual test in browser |
| Step 8 | 40%/60% split | Dev tools measurement |
| Step 9 | Full page works | Navigate to `/identity/10101` |
| Step 10-12 | Tests pass | `yarn test` |
| Step 13 | Manual tests pass | Follow checklist |

---

## Rollback Strategy

| Step | If Fails | Action |
|------|----------|--------|
| 1 | Constants break imports | Revert constants.ts |
| 2-3 | CLI fails | Use native HTML elements |
| 4-7 | Component crashes | Delete new file |
| 8 | Layout breaks pages | `git checkout DetailPageLayout.tsx` |
| 9 | Page crashes | `git checkout IdentityDetailPage.tsx` |

**Safe stopping points:**
- After Step 3: Foundation complete, no UI changes
- After Step 7: Components ready but not integrated
- After Step 8: Layout ready but page not refactored

---

## Critical Files Reference

| Purpose | File |
|---------|------|
| Add constants | `frontend/src/lib/constants.ts` |
| Pattern source | `frontend/src/components/deckBuilder/TierLevelSelector.tsx` |
| Breakpoint hook | `frontend/src/hooks/use-is-breakpoint.ts` |
| Refactor target | `frontend/src/routes/IdentityDetailPage.tsx` |
| Layout wrapper | `frontend/src/components/common/DetailPageLayout.tsx` |
