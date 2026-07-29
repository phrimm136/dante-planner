# Task: Modular Detail Page Layout with Uptie/Level Selector

## Description

Restructure the Identity detail page (`/identity/{id}`) with a new modular two-column layout that can be reused for EGO, EGO Gift, and future entity detail pages.

### Layout Structure

**Desktop (>= 768px) - Two Column Layout:**
- **Left Column (40%)**: Entity portfolio section
  - Header (image, name, rank/rarity)
  - Status panels (HP, speed, defense)
  - Resistance info
  - Traits display
- **Right Column (60%)**: Skills and details section
  - **Top**: Uptie selector (all buttons visible) + Level slider (1 to MAX_LEVEL)
  - **Below selector**: Scrollable area containing:
    - Skills section (with skill slot tabs)
    - Passives section (battle + support)
    - Sanity section (moved from left to right)

**Mobile (< 768px) - Tab-Based Layout:**
- Uptie/Level selector always visible at top
- Tab navigation below selector: [Info] [Skills] [Passives]
- Each tab shows full-width scrollable content

### Selector Behavior
- Uptie buttons: All levels visible (1, 2, 3, 4 for Identity)
- Level slider: Range from 1 to MAX_LEVEL (55)
- Selector is sticky when scrolling on desktop
- No state persistence (resets to defaults on page refresh)
- Defaults: uptie=4 (max), level=MAX_LEVEL

### Modularity Requirements
- Both containers must be generic components usable across entity types
- Left container accepts slots/children for entity-specific content
- Right container handles sticky selector + scrollable content pattern
- Entity type prop determines selector labels ("Uptie" vs "Threadspin" vs "Enhancement")
- Column ratio: 4:6 (fixed)

### Entity-Specific Adaptations
| Entity | Left Column | Right Column | Selector Type |
|--------|-------------|--------------|---------------|
| Identity | Status, Resistance, Traits | Skills, Passives, Sanity | Uptie (1-4) + Level |
| EGO | EGO info, Resources | Skills, Passives | Threadspin (1-4) + Level |
| EGO Gift | Gift info, Keywords | Effects, Conditions | Enhancement (1-3) |

## Research

### Existing Patterns to Study
- `DetailPageLayout.tsx` - Current layout wrapper (needs refactoring)
- `TierLevelSelector.tsx` - Existing uptie selector in deck builder (adapt for detail pages)
- `EGOGiftEnhancementSelector.tsx` - Enhancement level pattern
- shadcn/ui `Tabs` component - For mobile view
- shadcn/ui `ScrollArea` - For scrollable right panel
- shadcn/ui `Slider` - For level selection

### Constants to Check
- `MAX_LEVEL` in `constants.ts` (currently 55)
- Existing uptie/threadspin max values
- Breakpoint values used elsewhere

### Mobile Patterns
- How other pages handle responsive layouts
- Tab component usage in codebase
- Sticky positioning patterns

## Scope

### Files to READ for Context
- `/frontend/src/routes/IdentityDetailPage.tsx` - Current implementation
- `/frontend/src/routes/EGODetailPage.tsx` - Similar page to verify pattern works
- `/frontend/src/routes/EGOGiftDetailPage.tsx` - Gift page structure
- `/frontend/src/components/common/DetailPageLayout.tsx` - Current layout
- `/frontend/src/components/deckBuilder/TierLevelSelector.tsx` - Selector reference
- `/frontend/src/components/egoGift/EGOGiftEnhancementSelector.tsx` - Enhancement reference
- `/frontend/src/lib/constants.ts` - Existing constants
- `/frontend/src/components/ui/tabs.tsx` - shadcn tabs
- `/frontend/src/components/ui/slider.tsx` - shadcn slider (if exists)

### Data Files to Understand
- `/static/data/identity{id}.json` - Identity data structure
- `/static/data/ego{id}.json` - EGO data structure
- `/static/data/egoGift/{id}.json` - Gift data structure

## Target Code Area

### New Components to Create
- `/frontend/src/components/common/DetailEntitySelector.tsx` - Uptie/level selector
- `/frontend/src/components/common/DetailRightPanel.tsx` - Scrollable right container
- `/frontend/src/components/common/DetailLeftPanel.tsx` - Modular left container
- `/frontend/src/components/common/MobileDetailTabs.tsx` - Tab wrapper for mobile

### Files to Modify
- `/frontend/src/components/common/DetailPageLayout.tsx` - Add 4:6 ratio, mobile support
- `/frontend/src/routes/IdentityDetailPage.tsx` - Use new layout components
- `/frontend/src/lib/constants.ts` - Add detail page constants

### Future Modifications (after Identity works)
- `/frontend/src/routes/EGODetailPage.tsx` - Apply same pattern
- `/frontend/src/routes/EGOGiftDetailPage.tsx` - Apply same pattern

## Testing Guidelines

### Manual UI Testing

#### Desktop Layout Tests
1. Navigate to `/identity/10101` (any valid identity)
2. Verify two-column layout appears (left 40%, right 60%)
3. Verify left column shows: header, status panels, resistance, traits
4. Verify right column shows: selector at top, skills, passives, sanity
5. Verify sanity section is in RIGHT column (not left)

#### Selector Tests
6. Verify all 4 uptie buttons are visible (not in dropdown)
7. Click uptie button 1
8. Verify skills section updates to show uptie 1 data
9. Verify passives section updates to show uptie 1 passives
10. Verify status panels update (speed values change by uptie)
11. Drag level slider to 30
12. Verify HP value updates in status panel
13. Type "45" in level input field
14. Verify level slider position updates

#### Sticky Behavior Tests
15. Scroll down the right column content
16. Verify uptie/level selector stays visible at top (sticky)
17. Verify scrollbar only affects content below selector

#### Mobile Layout Tests
18. Resize browser to < 768px width
19. Verify layout switches to single column
20. Verify uptie/level selector appears at top
21. Verify 3 tabs appear: [Info] [Skills] [Passives]
22. Tap "Info" tab
23. Verify left column content displays (header, status, traits)
24. Tap "Skills" tab
25. Verify skills section displays with skill slot buttons
26. Tap "Passives" tab
27. Verify battle and support passives display
28. Change uptie in selector
29. Verify Skills and Passives tabs reflect the change

#### Cross-Entity Tests
30. Navigate to `/ego/20101` (any valid EGO)
31. Verify same layout structure works
32. Verify selector shows "Threadspin" label (not "Uptie")
33. Navigate to `/ego-gift/9001` (any valid gift)
34. Verify layout adapts for gift (may not have all sections)

### Automated Functional Verification

#### Layout Rendering
- [ ] Desktop (>= 768px): Two columns render with 4:6 ratio
- [ ] Mobile (< 768px): Tabs render instead of columns
- [ ] Column ratio: 4:6 produces 40%/60% split

#### Selector Functionality
- [ ] Uptie buttons: All 4 buttons visible and clickable
- [ ] Level slider: Draggable from 1 to MAX_LEVEL (55)
- [ ] Level input: Accepts numeric input, clamps to valid range
- [ ] State updates: Changing uptie/level updates displayed data
- [ ] No persistence: Refreshing page resets to defaults (uptie=4, level=55)

#### Sticky Behavior
- [ ] Desktop: Selector stays at top when scrolling right panel
- [ ] Mobile: Selector stays at top above tabs
- [ ] Z-index: Selector appears above scrolling content

#### Responsiveness
- [ ] Breakpoint transition: Smooth switch at 768px
- [ ] No layout shift: Content doesn't jump when switching
- [ ] Touch-friendly: Tabs respond to tap on mobile

### Edge Cases

- [ ] Empty passives: Identity with 0 passives at uptie 1 shows appropriate message
- [ ] Max uptie only: Some identities only have uptie 4 data; lower uptie shows empty or message
- [ ] Level boundaries: Level input rejects < 1 and > MAX_LEVEL
- [ ] Invalid level input: Non-numeric input is ignored or cleared
- [ ] Rapid tab switching: Multiple quick taps don't break layout
- [ ] Very long skill descriptions: Text doesn't overflow containers
- [ ] Window resize: Layout adapts dynamically when resizing browser

### Integration Points

- [ ] Data hooks: `useIdentityDetailData` continues to work unchanged
- [ ] Routing: Navigation from list page to detail page works
- [ ] Back navigation: Browser back button works correctly
- [ ] URL params: Identity ID from URL is used correctly
- [ ] i18n: All labels support translation
- [ ] Theme: Components respect light/dark theme

## Constants to Add

```typescript
// In constants.ts
export const DETAIL_PAGE = {
  /** Mobile breakpoint in pixels (768px = md: in Tailwind) */
  BREAKPOINT_MD: 768,
  /** Column ratio: 4:6 (left:right) using 10-column grid */
  COLUMN_LEFT: 'md:col-span-4',
  COLUMN_RIGHT: 'md:col-span-6',
  /** Right panel max height for scroll containment */
  RIGHT_PANEL_MAX_HEIGHT: 'calc(100vh - 12rem)',
} as const

export type DetailEntityType = 'identity' | 'ego' | 'egoGift'

export const MAX_UPTIE: Record<DetailEntityType, number> = {
  identity: 4,
  ego: 4,
  egoGift: 3,
}
```

## Implementation Notes

### Priority Order
1. Add constants to `constants.ts`
2. Create `DetailEntitySelector` component
3. Create `DetailRightPanel` with sticky + scroll
4. Create `DetailLeftPanel` wrapper
5. Refactor `DetailPageLayout` for 4:6 ratio
6. Create `MobileDetailTabs` component
7. Refactor `IdentityDetailPage` to use new components
8. Verify EGO page compatibility
9. Verify EGO Gift page compatibility

### State Management
- Uptie and level state should be lifted to page component level
- Pass down as props to selector and content sections
- No context needed (page-local state only)
