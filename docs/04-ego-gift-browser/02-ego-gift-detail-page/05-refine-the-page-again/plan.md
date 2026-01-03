# Execution Plan: EGO Gift Detail Page Refinement

## Execution Overview

Refactor `EGOGiftDetailPage.tsx` to use `DetailPageLayout` with 4:6 ratio, replacing parallel `EnhancementLevels` with click-to-reveal pattern. Create vertical metadata component for left column.

- **Files to Create:** 1 (`EGOGiftMetadata.tsx`)
- **Files to Modify:** 1 (`EGOGiftDetailPage.tsx` - complete rewrite)
- **Files Unchanged:** All reusable components

---

## Execution Order

### Phase 1: Component Layer

**Step 1.1: Create `EGOGiftMetadata.tsx`**
- Location: `frontend/src/components/egoGift/EGOGiftMetadata.tsx`
- Pattern Source: `StatusPanel.tsx` (vertical label-value structure)
- Props: `keyword`, `price`, `themePack[]`, `themePackNames`, `hardOnly`, `extremeOnly`
- Logic:
  - If `themePack.length === 0`, display "General"
  - Otherwise map pack IDs to names, join with comma
  - Render keyword with `FormattedKeyword` if not null, hide row if null
  - Render price with `CostDisplay`
  - Render difficulty badge if `hardOnly` or `extremeOnly`
- Depends on: None
- Enables: F2 (vertical metadata), E1 (empty theme pack), E2 (null keyword)

### Phase 2: Page Layer

**Step 2.1: Rewrite `EGOGiftDetailPage.tsx`**
- Location: `frontend/src/routes/EGOGiftDetailPage.tsx`
- Pattern Source: `IdentityDetailPage.tsx`
- Changes:
  1. Add inner content component with Suspense wrapper
  2. Add `useEGOGiftListData` to get `themePack`, `hardOnly`, `extremeOnly`
  3. Add `useThemePackListData` for theme pack name resolution
  4. Add `useState<number>(0)` for enhancement level
  5. Build leftColumn: Header row (GiftImage + GiftName) + EGOGiftMetadata
  6. Build rightColumn: DetailRightPanel with DetailEntitySelector + description
  7. Set mobileTabsContent = null (single scroll)
  8. Return DetailPageLayout with all props
- Depends on: Step 1.1
- Enables: F1 (4:6 layout), F3 (enhancement selector), F4 (click-to-reveal), F5 (mobile scroll)

### Phase 3: Integration

**Step 3.1: Wire Enhancement State** (within Step 2.1)
- useState for enhancementLevel (0-2)
- Pass to DetailEntitySelector via `tier` prop
- Pass `descs[enhancementLevel]` to description display
- Enables: F4 (click-to-reveal description)

**Step 3.2: Wire Theme Pack Data** (within Step 2.1)
- Lookup gift in specList to get `themePack` array
- Resolve pack names via themePackI18n
- Pass to EGOGiftMetadata
- Enables: F2 (theme pack display)

### Phase 4: Tests

**Step 4.1: Manual UI Verification**
- Follow testing checklist from instructions.md
- Enables: All features verified

---

## Verification Checkpoints

| After Step | Verify |
|------------|--------|
| 1.1 | Component compiles, TypeScript passes |
| 2.1 | Page renders with 4:6 layout on desktop |
| 2.1 | Enhancement selector shows 3 levels |
| 2.1 | Clicking enhancement updates description |
| 2.1 | Mobile shows single column (no tabs) |
| 2.1 | Theme pack shows names or "General" |

---

## Rollback Strategy

- **Step 1.1 failure:** Delete new component, no impact on existing code
- **Step 2.1 failure:** Restore `EGOGiftDetailPage.tsx` from git
- **Safe stopping points:** After any step - changes isolated to 2 files
