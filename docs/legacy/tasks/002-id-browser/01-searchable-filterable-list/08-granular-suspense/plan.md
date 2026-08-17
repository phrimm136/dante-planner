# Execution Plan: Granular Suspense for Identity and EGO Gift List Pages

## Planning Gaps
**NONE** - Research complete with clear reference implementation (EGO page).

---

## Execution Overview

Apply EGO page's granular Suspense pattern to Identity and EGO Gift pages:
1. **Deferred hooks** - `useQuery` instead of `useSuspenseQuery` for i18n in lists
2. **Name components** - Dedicated component with Suspense wrapper
3. **Card-level Suspense** - Each card wraps name in its own Suspense
4. **Spec-only types** - List items without `name` field
5. **No grid-level Suspense** - Remove Suspense around CardGrid

---

## Execution Order

### Phase 1: Data Layer (Types and Hooks)

1. **`types/IdentityTypes.ts`**: Add `IdentityListItem` type (spec-only)
   - Depends on: none
   - Enables: F3
   - Pattern: `EGOListItem` in `EGOTypes.ts:17-26`

2. **`types/EGOGiftTypes.ts`**: Add `EGOGiftListItemSpec` type (spec-only)
   - Depends on: none
   - Enables: F3
   - Pattern: `EGOListItem` in `EGOTypes.ts`

3. **`hooks/useIdentityListData.ts`**: Add `useIdentityListI18nDeferred()` hook
   - Depends on: none
   - Enables: F1
   - Pattern: `useEGOListData.ts:78-94`

4. **`hooks/useEGOGiftListData.ts`**: Add `useQuery` import + `useEGOGiftListI18nDeferred()` hook
   - Depends on: none
   - Enables: F1
   - Pattern: `useEGOListData.ts:78-94`

### Phase 2: Component Layer (Name Components)

5. **`components/identity/IdentityName.tsx`**: Create new (copy EGOName pattern)
   - Depends on: Step 3
   - Enables: F2
   - Pattern: `EGOName.tsx` (entire file)

6. **`components/egoGift/EGOGiftName.tsx`**: Create new (copy EGOName pattern)
   - Depends on: Step 4
   - Enables: F2
   - Pattern: `EGOName.tsx` (entire file)

### Phase 3: Card Layer (Add Internal Suspense)

7. **`components/identity/IdentityCard.tsx`**: Add IdentityName with internal Suspense
   - Depends on: Steps 1, 5
   - Enables: F2
   - Pattern: `EGOCard.tsx:123-130`

8. **`components/identity/IdentityCardLink.tsx`**: Update type to `IdentityListItem`
   - Depends on: Step 1
   - Enables: F3

9. **`components/egoGift/EGOGiftCard.tsx`**: Add EGOGiftName with internal Suspense
   - Depends on: Steps 2, 6
   - Enables: F2
   - Pattern: `EGOCard.tsx:123-130`

10. **`components/egoGift/EGOGiftCardLink.tsx`**: Update type to `EGOGiftListItemSpec`
    - Depends on: Step 2
    - Enables: F3

### Phase 4: List Layer (Use Deferred Hooks)

11. **`components/identity/IdentityList.tsx`**: Use deferred hook for name search
    - Depends on: Steps 1, 3, 8
    - Enables: F1, F4
    - Pattern: `EGOList.tsx:40-42,89-106`

12. **`components/egoGift/EGOGiftList.tsx`**: Use deferred hook for name search
    - Depends on: Steps 2, 4, 10
    - Enables: F1, F4
    - Pattern: `EGOList.tsx:40-42,89-106`

### Phase 5: Page Layer (Remove Inner Suspense)

13. **`routes/IdentityPage.tsx`**: Remove inner Suspense, delete CardGridSkeleton
    - Depends on: Steps 7, 8, 11
    - Enables: F4
    - Pattern: `EGOPage.tsx:22-69,209-222`

14. **`routes/EGOGiftPage.tsx`**: Remove inner Suspense, delete CardGridSkeleton
    - Depends on: Steps 9, 10, 12
    - Enables: F4
    - Pattern: `EGOPage.tsx:22-69,209-222`

### Phase 6: Verification

15. **TypeScript verification**: Run `yarn tsc`
    - Depends on: All previous steps
    - Enables: E1

16. **Build verification**: Run `yarn build`
    - Depends on: Step 15
    - Enables: E1

---

## Verification Checkpoints

- **After Step 4**: Deferred hooks compile (`yarn tsc`)
- **After Step 6**: Name components created
- **After Step 10**: Cards compile with new types
- **After Step 12**: Lists use deferred hooks
- **After Step 14**: Pages render without inner Suspense
- **After Step 16**: Full build succeeds

---

## Rollback Strategy

- **Safe stopping points**: End of each phase
- **Phase 1 fails**: Revert type/hook additions
- **Phase 2 fails**: Delete new files
- **Phase 3 fails**: Revert card changes
- **Phase 4 fails**: Revert list changes
- **Phase 5 fails**: Revert page changes only

**Critical**: Steps 13-14 depend on ALL prior steps. Complete Phase 4 before Phase 5.
