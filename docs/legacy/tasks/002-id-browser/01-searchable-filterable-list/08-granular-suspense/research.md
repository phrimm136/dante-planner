# Research: Granular Suspense for Identity and EGO Gift List Pages

## Spec Ambiguities
**NONE FOUND** - Specification is clear with reference implementation (EGO page).

---

## Spec-to-Code Mapping

| Requirement | Files | Change |
|---|---|---|
| Deferred i18n hooks | `useIdentityListData.ts`, `useEGOGiftListData.ts` | Add deferred hook variants |
| Name components | NEW: `IdentityName.tsx`, `EGOGiftName.tsx` | Create from EGOName pattern |
| Card-level Suspense | `IdentityCard.tsx`, `EGOGiftCard.tsx` | Wrap name in Suspense |
| Spec-only types | `IdentityTypes.ts`, `EGOGiftTypes.ts` | Add types without name field |
| List deferred search | `IdentityList.tsx`, `EGOGiftList.tsx` | Use deferred hook for name search |
| Page data flow | `IdentityPage.tsx`, `EGOGiftPage.tsx` | Remove inner Suspense, delete CardGridSkeleton |
| CardLink updates | `IdentityCardLink.tsx`, `EGOGiftCardLink.tsx` | Update to spec-only type |

---

## Pattern Enforcement (MANDATORY)

| New/Modified File | MUST Read First | Pattern to Copy |
|---|---|---|
| `IdentityName.tsx` | `components/ego/EGOName.tsx` | Hook call, single prop, ID fallback |
| `EGOGiftName.tsx` | `components/ego/EGOName.tsx` | Hook call, single prop, ID fallback |
| `useIdentityListI18nDeferred()` | `hooks/useEGOListData.ts:78-94` | useQuery, EMPTY constant, ?? operator |
| `useEGOGiftListI18nDeferred()` | `hooks/useEGOListData.ts:78-94` | useQuery, EMPTY constant, ?? operator |
| `IdentityCard.tsx` | `components/ego/EGOCard.tsx:123-130` | Suspense + Skeleton fallback |
| `EGOGiftCard.tsx` | `components/ego/EGOCard.tsx:123-130` | Suspense + Skeleton fallback |
| `IdentityList.tsx` | `components/ego/EGOList.tsx:40-42,88-106` | Deferred hook, name lookup |
| `EGOGiftList.tsx` | `components/ego/EGOList.tsx:40-42,88-106` | Deferred hook, name lookup |
| `IdentityPage.tsx` | `routes/EGOPage.tsx` | No inner Suspense, spec-only CardGrid |
| `EGOGiftPage.tsx` | `routes/EGOPage.tsx` | No inner Suspense, spec-only CardGrid |

---

## Existing Utilities (Reuse)

| Category | Location | Functions |
|---|---|---|
| Query factories | `hooks/use*ListData.ts` | `queryOptions()` pattern |
| Query keys | `hooks/useEGOListData.ts` | `egoListQueryKeys` factory |
| Suspense patterns | `components/ego/EGOName.tsx`, `EGOCard.tsx` | Already implemented |
| Skeletons | `components/ui/skeleton.tsx` | Imported in all pages |
| Constants | `lib/constants.ts` | `CARD_GRID.WIDTH.*` |

---

## Gap Analysis

**Create:**
- `IdentityName.tsx`, `EGOGiftName.tsx` (new components)
- `useIdentityListI18nDeferred()`, `useEGOGiftListI18nDeferred()` (new hooks)
- `IdentityListItem`, `EGOGiftListItemSpec` (new types)

**Modify:**
- Cards: Add internal Suspense around name
- Lists: Use deferred hook, update type
- Pages: Remove CardGridSkeleton, remove inner Suspense
- CardLinks: Update to spec-only type
- Hooks: Add useQuery import (EGOGift), add deferred export

**Reuse:**
- Suspense/Skeleton pattern from EGOCard
- Query options factory pattern
- Search logic pattern from EGOList
- CardGrid structure from EGOPage

---

## Testing Requirements

### Manual UI Tests
1. Identity page: Change language with Slow 3G throttling
   - Grid stays visible, names show skeletons, search empty during load
2. EGO Gift page: Same verification
3. Compare with EGO page (reference) - all three should match

### Automated Verification
- Deferred hooks return `{}` while loading, populated after
- Name components trigger Suspense
- Grid stays mounted during language change
- `yarn tsc` and `yarn build` pass

### Edge Cases
- Initial load suspends at outer boundary (correct)
- Rapid language switching doesn't cause errors
- ID fallback when i18n missing
- Filters work independently of name loading

---

## Technical Constraints

- Grid NOT wrapped in Suspense (stays visible)
- Deferred hooks use `useQuery` (not `useSuspenseQuery`)
- Search returns empty during i18n load (acceptable UX)
- Each card has own Suspense boundary for name
- TypeScript compliance required
