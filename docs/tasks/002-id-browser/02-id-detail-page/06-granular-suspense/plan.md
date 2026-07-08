# Plan: Granular Suspense for Identity Detail Page

## Planning Gaps
**NONE** - Research is complete. All patterns are well-documented.

## Execution Overview

Bottom-up approach:
1. **Data Layer First**: Split hook to enable spec-only queries
2. **Components Layer**: Create suspending components for each i18n section
3. **Integration Layer**: Refactor page with new hooks and Suspense boundaries
4. **Tests Last**: Update existing tests and add new tests

---

## Dependency Analysis

### Files Being Modified

| File | Impact Level | Depends On | Used By |
|------|--------------|------------|---------|
| `useIdentityDetailData.ts` | Medium | Schemas, useTranslation | IdentityDetailPage only |
| `IdentityDetailPage.tsx` | Low (isolated) | useIdentityDetailData, components | Route only |
| `TraitsDisplay.tsx` | Low | useTranslation | IdentityDetailPage only |

### Ripple Effect Map
- `useIdentityDetailData.ts` adds new exports → No breaking change (additive)
- `TraitsDisplay.tsx` internal change → No external impact (same props)
- Page moves i18n into child components → Ensure identical output

### High-Risk Modifications

| File | Risk | Mitigation |
|------|------|------------|
| `useIdentityDetailData.ts` | Backward compat | Keep original hook unchanged |
| `IdentityDetailPage.tsx` | Large refactor | Test each section independently |
| `usePanicInfo.ts` | Already suspends | Wrap in Suspense, don't modify |

---

## Execution Order

### Phase 1: Data Layer (Hooks)

**Step 1. Modify `useIdentityDetailData.ts`**
- Add `useIdentityDetailSpec(id)` - spec only, no language key
- Add `useIdentityDetailI18n(id)` - i18n only, suspends on language change
- Keep `useIdentityDetailData()` unchanged
- Depends on: none
- Enables: F1

**Step 2. Create `hooks/useTraitsI18n.ts`**
- New hook for `unitKeywords.json` with language in query key
- Pattern: `useIdentityListData.ts`
- Depends on: none
- Enables: F5

### Phase 2: Components Layer (Suspending Components)

**Step 3. Create `components/identity/IdentityHeaderI18n.tsx`**
- Suspending component for identity name in header
- Pattern: `IdentityName.tsx`
- Depends on: Step 1
- Enables: F2

**Step 4. Create `components/identity/SkillI18n.tsx`**
- Suspending component for skill names and descriptions
- Pattern: `IdentityName.tsx` + `FormattedDescription`
- Depends on: Step 1
- Enables: F3

**Step 5. Create `components/identity/PassiveI18n.tsx`**
- Suspending component for passive names and descriptions
- Pattern: `IdentityName.tsx` + `FormattedDescription`
- Depends on: Step 1
- Enables: F4

**Step 6. Create `components/identity/SanityI18n.tsx`**
- Suspending wrapper for panic info (usePanicInfo already suspends)
- Depends on: none
- Enables: F6

**Step 7. Create `components/identity/TraitsI18n.tsx`**
- Suspending component for trait names
- Depends on: Step 2
- Enables: F5

**Step 8. Refactor `components/identity/TraitsDisplay.tsx`**
- Convert from useEffect+useState to composition pattern
- Keep static container, delegate i18n to TraitsI18n
- Wrap TraitsI18n in Suspense boundary
- Depends on: Step 7
- Enables: F5

### Phase 3: Integration Layer (Page Refactoring)

**Step 9. Refactor `IdentityDetailPage.tsx`**
- Use `useIdentityDetailSpec` in shell (non-suspending)
- Replace inline i18n with Suspense-wrapped components:
  - Header → IdentityHeaderI18n
  - Skills → SkillI18n per skill
  - Passives → PassiveI18n per passive
  - Sanity → SanityI18n
- Depends on: Steps 1, 3, 4, 5, 6, 8
- Enables: All features (F1-F6)

### Phase 4: Tests

**Step 10. Update `IdentityDetailPage.test.tsx`**
- Update mock structure for split hooks
- Add test for granular loading behavior
- Depends on: Step 9

**Step 11. Create `useIdentityDetailData.test.ts`**
- Test spec query key has no language
- Test i18n query key has language
- Test backward compatibility
- Depends on: Step 1

**Step 12. Create `TraitsDisplay.test.tsx`**
- Test Suspense boundary behavior
- Test skeleton display during load
- Depends on: Step 8

---

## Verification Checkpoints

| After Step | Verify |
|------------|--------|
| Step 1 | Run `yarn build` - no type errors |
| Step 2 | Run `yarn build` - hook compiles |
| Step 8 | TraitsDisplay renders same output |
| Step 9 | Manual test: Language change shows inline skeletons |
| Step 12 | Run `yarn test` - all tests pass |

---

## Risk Mitigation

| Risk | Step | Mitigation |
|------|------|------------|
| Initial load shows full skeleton | Step 9 | Keep outer Suspense unchanged |
| Language change mid-render | Step 9 | React Suspense handles concurrent |
| Network failure on i18n | Steps 3-7 | Error boundary at route level |
| Multiple Suspense overhead | Steps 3-7 | Acceptable for UX; queries cached |
| Backward compatibility | Step 1 | Keep original hook unchanged |

---

## Rollback Strategy

**Safe stopping points:**
- After Step 1: Hook is additive, no breaking changes
- After Step 8: Components work independently
- After Step 9: Full integration complete

**If step N fails:**
- Step 1 fails: Stop - foundation issue
- Steps 2-8 fail: Continue with other components
- Step 9 fails: Revert page, keep new hooks/components
