# Task: StartBuff Summary/Edit Mode Separation

## Description

Refactor the StartBuff section to use a Summary + EditPane pattern instead of the current viewMode dual-purpose approach. The main planner page shows a compact summary view (selected buffs only), while the edit dialog shows the full interactive cards.

### Summary View (Main Section)
- Display only **selected buffs** as compact mini cards
- Each mini card uses `startBuffMini.webp` as background (with centered cyan separator line)
- On hover, overlay `startBuffMiniHighlight.webp` for visual feedback
- Card size: `w-24 h-24` (96px, adjustable after visual review)
- Card layout (based on game UI):
  - **Upper half (above separator)**: Buff icon centered
  - **Lower half (below separator)**: Buff name with enhancement suffix (e.g., "시작의 별++")
  - **Top-right corner**: Reuse `EGOGiftEnhancementIndicator` component (shows +1/+2 icon)
- Text color: Cyan (`MD6_ACCENT_COLOR` constant for forward compatibility)
- Entire section is clickable to open the edit pane
- Empty state: Show placeholder text prompting user to click and select buffs

### Constants Addition
Add to `lib/constants.ts`:
```typescript
// Mirror Dungeon 6 UI colors (may change in future dungeon versions)
export const MD6_ACCENT_COLOR = '#00ffcc'  // Cyan accent for MD6 UI elements
```

### Edit Pane (Dialog)
- Continue using existing full `StartBuffCard` components
- Remove `viewMode` prop from `StartBuffCard` - it becomes edit-only
- Show all 10 buffs with full descriptions and enhancement buttons
- Selection/deselection happens here

### UX Rationale
- Follows game UX: selection screen (full cards) vs result screen (compact summary)
- Reduces visual noise on planner page
- StartBuff was the only section using viewMode pattern - now consistent with others
- Eliminates SRP violation in StartBuffCard (was serving dual purposes)

## Research
- Existing `StartBuffCard.tsx` to understand current viewMode implementation
- `startBuffMini.webp` and `startBuffMiniHighlight.webp` asset dimensions
- `getStartBuffIconPath()` in `assetPaths.ts` for icon paths
- `getEnhancementSuffix()` in `StartBuffTypes.ts` for "++" suffix
- `EGOGiftEnhancementIndicator.tsx` for enhancement level display pattern (reuse)
- `CompactIconFilter.tsx` for similar compact card styling patterns
- `PlannerSection.tsx` for section wrapper pattern
- `constants.ts` for existing color/style constants structure

## Scope
Files to READ for context:
- `frontend/src/components/startBuff/StartBuffCard.tsx`
- `frontend/src/components/startBuff/StartBuffSection.tsx`
- `frontend/src/components/startBuff/StartBuffEditPane.tsx`
- `frontend/src/hooks/useStartBuffSelection.ts`
- `frontend/src/types/StartBuffTypes.ts`
- `frontend/src/lib/assetPaths.ts`
- `frontend/src/lib/constants.ts`
- `frontend/src/components/egoGift/EGOGiftEnhancementIndicator.tsx`
- `static/images/UI/MD6/startBuffMini.webp`
- `static/images/UI/MD6/startBuffMiniHighlight.webp`

## Target Code Area

### New Files
- `frontend/src/components/startBuff/StartBuffMiniCard.tsx` - Compact summary card component

### Modified Files
- `frontend/src/components/startBuff/StartBuffSection.tsx` - Render mini cards for selected buffs only
- `frontend/src/components/startBuff/StartBuffCard.tsx` - Remove viewMode prop and conditionals
- `frontend/src/routes/PlannerMDNewPage.tsx` - Remove viewMode prop from StartBuffSection
- `frontend/src/lib/assetPaths.ts` - Add helper for mini card assets
- `frontend/src/lib/constants.ts` - Add `MD6_ACCENT_COLOR` constant

## Testing Guidelines

### Manual UI Testing
1. Navigate to `/planner/md/new`
2. Verify StartBuff section shows empty state placeholder
3. Click anywhere in the StartBuff section
4. Verify edit dialog opens with all 10 buff cards
5. Select a buff by clicking its card
6. Verify selection highlight appears
7. Click enhancement button (+ or ++)
8. Verify enhancement level updates
9. Click "Done" to close dialog
10. Verify selected buff appears as mini card in main section
11. Verify mini card shows: icon (top), name with suffix (bottom), level indicator (top-right)
12. Hover over mini card in main section
13. Verify highlight overlay appears
14. Select multiple buffs in edit pane
15. Verify all selected buffs appear as mini cards (flex-wrap if needed)
16. Deselect all buffs
17. Verify empty state placeholder reappears
18. Refresh page and verify selections persist (autosave)

### Automated Functional Verification
- [ ] Mini card renders with correct background image (`startBuffMini.webp`)
- [ ] Mini card hover shows highlight overlay (`startBuffMiniHighlight.webp`)
- [ ] Mini card displays buff icon in upper half
- [ ] Mini card displays buff name with enhancement suffix in lower half
- [ ] Mini card displays enhancement level indicator in top-right
- [ ] Section click triggers edit pane open
- [ ] Keyboard navigation (Enter/Space) opens edit pane
- [ ] StartBuffCard no longer has viewMode prop
- [ ] Edit pane shows all 10 buffs with interactive controls

### Edge Cases
- [ ] Empty selection: Shows placeholder text, entire section clickable
- [ ] Single buff selected: Displays one mini card
- [ ] Many buffs selected: Mini cards wrap to next line gracefully
- [ ] Long buff name: Text truncates appropriately within card bounds
- [ ] Enhancement level 0: No suffix shown, no indicator icon (EGOGiftEnhancementIndicator returns null)
- [ ] Enhancement level 1: "+" suffix, +1 icon via EGOGiftEnhancementIndicator
- [ ] Enhancement level 2: "++" suffix, +2 icon via EGOGiftEnhancementIndicator

### Integration Points
- [ ] Autosave: Selection changes trigger usePlannerAutosave
- [ ] Draft recovery: Mini cards display correctly after draft load
- [ ] Edit pane sync: Changes in edit pane immediately reflect in summary
