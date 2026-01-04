# Task: Identity Card Level and Name Display

## Description
Add a built-in info panel to Identity cards showing level indicator and identity name at the bottom, matching the in-game UI aesthetic. The info panel should:

- Display level as "Lv. 55" format (using MAX_LEVEL constant)
- Display identity name below the level, preserving line breaks from i18n data
- Names in i18n contain `\n` characters for multi-line display (e.g., "LCB\nSinner", "Lobotomy E.G.O::\nSolemn Lament")
- Use `line-clamp-3` to accommodate all name formats (some names have 3 lines)
- Use a dark gradient background for text readability on varied card images
- Support granular loading via Suspense (card stays visible, only name shows skeleton during language change)
- Use `text-[10px]` font size
- White text with drop shadow, subtle gradient background

## Research
- EGOCard.tsx Layer 5 info panel pattern (lines 100-143)
- EGOName.tsx component structure (suspending name lookup)
- useIdentityListI18n() hook for name data fetching
- MAX_LEVEL constant in lib/constants.ts (value: 55)
- Identity name format in i18n files:
  - Contains `\n` for line breaks
  - Examples: "LCB\nSinner", "W Corp. L3\nCleanup Agent", "LCE E.G.O::\nArdor Blossom\nStar"
  - Names have 1-3 lines

## Scope
Files to READ for context:
- `frontend/src/components/ego/EGOCard.tsx` (pattern source for info panel)
- `frontend/src/components/ego/EGOName.tsx` (pattern source for name component)
- `frontend/src/components/identity/IdentityCard.tsx` (target file)
- `frontend/src/components/identity/IdentityName.tsx` (exists, needs line break rendering)
- `frontend/src/hooks/useIdentityListData.ts` (useIdentityListI18n hook)
- `frontend/src/lib/constants.ts` (MAX_LEVEL)
- `static/i18n/EN/identityNameList.json` (name format reference)

## Target Code Area
Files to be MODIFIED:
- `frontend/src/components/identity/IdentityName.tsx` - Render name with line breaks preserved
- `frontend/src/components/identity/IdentityCard.tsx` - Add Layer 5 info panel

## System Context
- **Feature domain:** Identity Browser
- **Core files in this domain:**
  - `routes/IdentityPage.tsx`, `routes/IdentityDetailPage.tsx`
  - `hooks/useIdentityListData.ts`
  - `components/identity/*`
- **Cross-cutting concerns:**
  - i18n (identity names from useIdentityListI18n)
  - Constants (MAX_LEVEL)
  - Suspense boundaries (granular loading)

## Impact Analysis
- **Files being modified:**
  - `IdentityName.tsx` (Low impact - isolated component)
  - `IdentityCard.tsx` (Medium impact - used by IdentityList, SinnerDeckCard)
- **What depends on IdentityCard:**
  - `IdentityCardLink.tsx` (wraps for navigation)
  - `IdentityList.tsx` (renders in browser)
  - `SinnerDeckCard.tsx` (uses with deployment overlay in planner)
- **Potential ripple effects:**
  - All identity card renderings will show level and name
  - Card dimensions unchanged (160×224px)
  - Overlay prop continues to work (positioned before Layer 5)

## Risk Assessment
- **Edge cases:**
  - 3-line names display fully with `line-clamp-3`
  - Missing i18n data - fallback to ID
  - Language switching - handled by Suspense + skeleton
- **Performance:** Minimal - name data cached via TanStack Query
- **Backward compatibility:** No breaking changes to IdentityCard props

## Testing Guidelines

### Manual UI Testing
1. Navigate to `/identity` (Identity Browser page)
2. Verify identity cards display "Lv. 55" at bottom
3. Verify names display below level with line breaks preserved
4. Check "LCB Sinner" cards - "LCB" on line 1, "Sinner" on line 2
5. Check 3-line names like "LCE E.G.O:: Ardor Blossom Star" - all 3 lines visible
6. Verify text readable on light (1-star) and dark (3-star) backgrounds
7. Change language to Korean (KR)
8. Verify Korean names display with line breaks (e.g., "LCB\n수감자")
9. Navigate to `/planner/md/new`
10. Verify planner identity cards also show level and name
11. Select identity with deployment overlay - verify overlay displays above info panel

### Functional Verification
- [ ] Level display: Shows "Lv. 55" on all identity cards
- [ ] Name display: Localized name with line breaks preserved
- [ ] Line clamp: `line-clamp-3` accommodates all name lengths
- [ ] Suspense: Skeleton shows while name loads on language change
- [ ] Fallback: Shows identity ID if name missing
- [ ] Overlay compatibility: Custom overlays render correctly

### Edge Cases
- [ ] 2-line names: "LCB\nSinner" displays correctly
- [ ] 3-line names: "LCE E.G.O::\nArdor Blossom\nStar" displays all lines
- [ ] Missing translation: Falls back to identity ID
- [ ] Language switch: Card visible, name updates after load
- [ ] Various card backgrounds: Text readable on all rank backgrounds

### Integration Points
- [ ] IdentityList: Browser cards show info panel
- [ ] SinnerDeckCard: Planner cards show info panel under deployment overlay
- [ ] i18n system: Names update on language change
