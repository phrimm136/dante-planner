# Research: Identity Card Level and Name Display

## Spec Ambiguities
**NONE FOUND** - Specification is clear and actionable.

---

## Spec-to-Code Mapping

| Requirement | File | Modification |
|-------------|------|--------------|
| Display level "Lv. 55" | IdentityCard.tsx | Add Layer 5 info panel with level text |
| Display name with line breaks | IdentityName.tsx | Render `\n` as line breaks |
| Render name inside card | IdentityCard.tsx | Add Suspense + IdentityName in info panel |
| Use MAX_LEVEL constant | constants.ts | No change - already exists (line 12) |
| Gradient background | IdentityCard.tsx | Add gradient div to info panel |
| text-[10px], white, drop shadow | IdentityCard.tsx | Apply styling to info panel text |
| line-clamp-3 | IdentityCard.tsx | Add class to name container |

---

## Spec-to-Pattern Mapping

| Requirement | Pattern Source | Application |
|-------------|----------------|-------------|
| Layer 5 info panel structure | EGOCard.tsx lines 100-143 | Copy gradient + text container pattern |
| Suspense + Skeleton | EGOCard.tsx lines 124-129 | Wrap IdentityName in Suspense |
| Name component with i18n | EGOName.tsx | IdentityName already follows pattern |
| Line break rendering | Custom approach | Split by `\n`, render with JSX elements |
| Overlay positioning | IdentityCard.tsx line 79 | Already correct, Layer 5 goes after |

---

## Pattern Enforcement

| File to Modify | MUST Read First | Pattern to Copy |
|----------------|-----------------|-----------------|
| IdentityName.tsx | EGOName.tsx | Name lookup + add line break rendering |
| IdentityCard.tsx | EGOCard.tsx | Layer 5: gradient bg + flex layout + Suspense |

---

## Existing Utilities (Verified)

| Category | Location | Found |
|----------|----------|-------|
| Constants | lib/constants.ts | MAX_LEVEL = 55 ✓ |
| Hooks | hooks/useIdentityListData.ts | useIdentityListI18n() ✓ |
| Components | components/ui/skeleton.tsx | Skeleton ✓ |

---

## Gap Analysis

**Missing:**
- Identity info panel (no equivalent to EGOCard Layer 5)
- Line break rendering for multi-line names

**Needs Modification:**
- IdentityName.tsx - Add `\n` to line break conversion
- IdentityCard.tsx - Add Layer 5 info panel

**Can Reuse:**
- MAX_LEVEL constant
- useIdentityListI18n hook
- Skeleton component
- Suspense pattern from EGOCard

---

## Testing Requirements

### Manual UI Tests
- Level shows "Lv. 55" on all cards at `/identity`
- 2-line names: "LCB" / "Sinner" on separate lines
- 3-line names: All lines visible with line-clamp-3
- Text readable on 1-star (light) and 3-star (dark) backgrounds
- Language switch: Names update with line breaks preserved
- Skeleton appears briefly during language change
- Planner overlay appears above info panel at `/planner/md/new`

### Automated Tests
- IdentityName renders `\n` as line breaks
- IdentityName fallback renders ID when translation missing
- IdentityCard info panel has correct layer structure
- Skeleton displays during Suspense

---

## Technical Constraints

**Pattern Compliance:**
- Layer 5 must mirror EGOCard structure (gradient, flex, centered)
- IdentityName must be wrapped in Suspense
- Line breaks via JSX elements (not CSS white-space)
- Use text-[10px] Tailwind class

**Visual Stacking Order:**
1. Layer 1: Identity image
2. Custom Overlay (deployment badge)
3. Layer 2: Uptie frame
4. Layer 3: Sinner BG
5. Layer 4: Sinner icon
6. **Layer 5 (NEW): Info panel** (bottom)

**Performance:** No impact - name data cached via TanStack Query (7-day staleTime)
