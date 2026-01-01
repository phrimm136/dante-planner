# Research: StartBuff Summary/Edit Mode Separation

## Spec Ambiguities

**None blocking.** Spec is well-detailed.

Minor (non-blocking): Empty state placeholder text uses i18n key (not hardcoded)

---

## Spec-to-Code Mapping

| Requirement | Files | Action |
|-------------|-------|--------|
| Mini card component | `StartBuffMiniCard.tsx` | New file |
| Summary view rendering | `StartBuffSection.tsx` | Modify - render mini cards for selected only |
| Remove viewMode | `StartBuffCard.tsx` | Modify - remove prop and conditionals |
| MD6 color constant | `lib/constants.ts` | Add `MD6_ACCENT_COLOR` |
| Mini asset helpers | `lib/assetPaths.ts` | Add `getStartBuffMiniPath`, `getStartBuffMiniHighlightPath` |
| Consumer update | `PlannerMDNewPage.tsx` | Remove `viewMode={true}` prop |

---

## Spec-to-Pattern Mapping

| Requirement | Pattern Source | Usage |
|-------------|----------------|-------|
| Mini card layout | `EGOGiftCard.tsx`, `CompactIconFilter.tsx` | Card styling, image backgrounds, overlay |
| Enhancement indicator | `EGOGiftEnhancementIndicator.tsx` | Import and place in top-right |
| Section wrapper | `PlannerSection.tsx` | Already used, no changes |
| Empty state | `KeywordSelector` in PlannerMDNewPage | Conditional placeholder text |

---

## Pattern Enforcement

| New File | MUST Read First | Pattern to Copy |
|----------|-----------------|-----------------|
| StartBuffMiniCard.tsx | EGOGiftCard.tsx | Card styling (w-24 h-24), image background, overlay positioning |
| StartBuffMiniCard.tsx | EGOGiftEnhancementIndicator.tsx | Enhancement icon placement (returns null for 0) |
| StartBuffMiniCard.tsx | StartBuffCard.tsx | getStartBuffIconPath, getEnhancementSuffix usage |

---

## Existing Utilities

| Category | Location | Functions |
|----------|----------|-----------|
| Buff icon paths | assetPaths.ts | `getStartBuffIconPath(baseId)` |
| Enhancement suffix | StartBuffTypes.ts | `getEnhancementSuffix(level)` |
| Enhancement from ID | StartBuffTypes.ts | `getEnhancementFromBuffId(buffId)` |
| Selection hook | useStartBuffSelection.ts | `displayBuffs`, `handleSelect`, `i18n` |
| Enhancement indicator | EGOGiftEnhancementIndicator.tsx | Reusable component |

**MISSING (must add):**
- `MD6_ACCENT_COLOR` in constants.ts
- `getStartBuffMiniPath()` in assetPaths.ts
- `getStartBuffMiniHighlightPath()` in assetPaths.ts

---

## Gap Analysis

**Missing:**
- `MD6_ACCENT_COLOR = '#00ffcc'` constant
- Mini card asset path helpers
- `StartBuffMiniCard.tsx` component

**Needs modification:**
- `StartBuffSection.tsx` - render mini cards, handle empty state
- `StartBuffCard.tsx` - remove viewMode (lines 23, 48, 60, 65, 85, 155)
- `PlannerMDNewPage.tsx` - remove viewMode prop (line 590)

**Can reuse:**
- `EGOGiftEnhancementIndicator` (import directly)
- `useStartBuffSelection` hook (unchanged)
- `StartBuffEditPane` (no changes needed)

---

## Testing Requirements

### Manual UI Tests
1. Empty state shows placeholder in StartBuff section
2. Click section opens edit dialog
3. Select buff shows highlight in dialog
4. Enhancement buttons work in dialog
5. Mini card appears after Done
6. Mini card shows: icon (top), name+suffix (bottom), indicator (top-right)
7. Hover shows highlight overlay
8. Multiple buffs wrap correctly
9. Text color is cyan (MD6_ACCENT_COLOR)
10. Deselect removes mini card
11. Refresh preserves selections (autosave)

### Automated Tests
- StartBuffMiniCard renders correct background
- Hover shows overlay
- Enhancement indicator shows for levels 1-2, null for 0
- StartBuffCard has no viewMode prop
- Section renders placeholder when empty
- Section click triggers edit pane

### Edge Cases
- Enhancement 0: no suffix, no indicator
- Enhancement 1: "+" suffix, +1 icon
- Enhancement 2: "++" suffix, +2 icon
- Long names truncate
- Empty selection shows placeholder
- Multiple buffs flex-wrap

---

## Technical Constraints

- React 19 (no manual memo)
- Tailwind styling (use constants for colors)
- Single responsibility per component
- No viewMode dual-purpose pattern

**Assets verified:**
- `static/images/UI/MD6/startBuffMini.webp` exists
- `static/images/UI/MD6/startBuffMiniHighlight.webp` exists
