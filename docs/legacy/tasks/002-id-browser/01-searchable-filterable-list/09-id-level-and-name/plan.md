# Execution Plan: Identity Card Level and Name Display

## Planning Gaps
**NONE FOUND** - Research is complete and all dependencies are clear.

---

## Execution Overview

Add built-in info panel to IdentityCard displaying level ("Lv. 55") and identity name with preserved line breaks. Implementation follows EGOCard Layer 5 pattern with Suspense-wrapped name component.

**Strategy:** Modify two files in sequence - first IdentityName for line breaks, then IdentityCard for info panel.

---

## Dependency Analysis

### Files Being Modified

| File | Impact Level | Depends On | Used By |
|------|--------------|------------|---------|
| `IdentityName.tsx` | Low | `useIdentityListI18n` | IdentityCard |
| `IdentityCard.tsx` | Medium | `IdentityName`, `MAX_LEVEL`, `Skeleton` | IdentityCardLink, IdentityList, SinnerDeckCard |

### Ripple Effect Map

- `IdentityName.tsx` changes → Only IdentityCard uses it
- `IdentityCard.tsx` changes → All card consumers see new info panel:
  - `IdentityCardLink.tsx` - no code change needed
  - `IdentityList.tsx` - no code change needed
  - `SinnerDeckCard.tsx` - overlay renders ABOVE info panel, no change needed

### High-Risk Modifications

| File | Risk | Mitigation |
|------|------|------------|
| `IdentityCard.tsx` | SinnerDeckCard overlay positioning | Overlay prop renders before Layer 5, verify manually |
| `IdentityName.tsx` | Low - simple addition | Pattern from EGOName clear, fallback preserved |

---

## Execution Order

1. **IdentityName.tsx**: Add line break rendering for `\n` characters
   - Depends on: none
   - Enables: F2 (name display with line breaks)
   - Pattern: EGOName.tsx + `\n` splitting via JSX
   - Changes: Split name by `\n`, map to elements with line breaks

2. **IdentityCard.tsx**: Add Layer 5 info panel with level and name
   - Depends on: Step 1
   - Enables: F1 (level display), F3 (Suspense skeleton)
   - Pattern: EGOCard.tsx lines 100-143
   - Changes:
     - Import Suspense, Skeleton, IdentityName, MAX_LEVEL
     - Add Layer 5 div after Layer 4
     - Gradient background, "Lv. {MAX_LEVEL}", Suspense-wrapped IdentityName
     - Apply text-[10px], line-clamp-3, white text, drop shadow

---

## Verification Checkpoints

- After step 1: IdentityName renders name with line breaks
- After step 2:
  - F1: Level shows "Lv. 55" at `/identity`
  - F2: Names display with line breaks
  - F3: Skeleton appears during language change
  - Overlay compatibility at `/planner/md/new`

---

## Risk Mitigation

| Risk | Step | Mitigation |
|------|------|------------|
| Line breaks not rendering | 1 | Use JSX elements, not CSS |
| Overlay hidden by info panel | 2 | Overlay renders before Layer 5 |
| Text unreadable | 2 | Dark gradient + drop shadow |
| Name too long | 2 | line-clamp-3 |
| Missing i18n during load | 2 | Suspense fallback shows Skeleton |
| Missing translation | 1 | Fallback to ID preserved |

---

## Rollback Strategy

- **Safe stop:** After Step 1 - IdentityName works independently
- **Full rollback:** Revert both files
- **Partial:** Remove Layer 5, keep IdentityName improvements
