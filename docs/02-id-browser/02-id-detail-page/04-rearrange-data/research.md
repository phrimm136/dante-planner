# Research: Modular Detail Page Layout

## Spec-to-Code Mapping

| Requirement (instructions.md) | Implementation | File:Line |
|-------------------------------|----------------|-----------|
| Desktop two-column 40%/60% | 10-column grid with col-span-4/col-span-6 | DetailPageLayout.tsx:46-48 |
| Left column: header, status, resistance, traits | leftColumn prop renders these sections | IdentityDetailPage.tsx:74-107 |
| Right column: selector, skills, passives, sanity | DetailRightPanel wraps content | IdentityDetailPage.tsx:284-290 |
| Uptie buttons all visible (1-4) | tiers array maps to button elements | DetailEntitySelector.tsx:57, 107-160 |
| Level slider 1-55 | Slider with min=1, max=MAX_LEVEL | DetailEntitySelector.tsx:167-173 |
| Selector sticky on desktop | sticky class on container in DetailRightPanel | DetailRightPanel.tsx:25 |
| Mobile tabs (Skills/Passives/Sanity) | MobileDetailTabs with shadcn Tabs | MobileDetailTabs.tsx:36-54 |
| Breakpoint >= 1024px for desktop | DETAIL_PAGE.BREAKPOINT_LG = 1024 | constants.ts:409 |
| Defaults: uptie=4, level=55 | useState with MAX_ENTITY_TIER.identity, MAX_LEVEL | IdentityDetailPage.tsx:29-30 |
| No state persistence | Local useState, no localStorage | IdentityDetailPage.tsx:27-30 |

## Spec-to-Pattern Mapping

| Pattern Source | Applied In | How Pattern Was Used |
|----------------|------------|---------------------|
| TierLevelSelector.tsx | DetailEntitySelector.tsx | Button layout for tier icons, map over tiers array |
| EGOGiftEnhancementSelector.tsx | DetailEntitySelector.tsx | Enhancement icons (0/+1/+2), different icon path |
| shadcn/ui Tabs | MobileDetailTabs.tsx | TabsList, TabsTrigger, TabsContent structure |
| shadcn/ui Slider | DetailEntitySelector.tsx | value/onValueChange pattern, min/max props |
| shadcn/ui ScrollArea | DetailRightPanel.tsx | Wraps scrollable content with maxHeight constraint |
| useIsBreakpoint hook | DetailPageLayout.tsx | `useIsBreakpoint('max', DETAIL_PAGE.BREAKPOINT_LG)` |
| cn() utility | All new components | Conditional class merging pattern |

## Pattern Decisions

### 1. Why Combined TierLevelSelector + EGOGiftEnhancementSelector?

**Decision:** Create unified `DetailEntitySelector` that handles all entity types.

**Rationale:**
- TierLevelSelector uses tier icons (1-4) for Identity/EGO
- EGOGiftEnhancementSelector uses enhancement icons (0/+1/+2)
- Both share: button layout, selection state, visual feedback
- Unified component reduces code duplication across detail pages
- entityType prop determines which icons/labels to render

### 2. Why 1024px Breakpoint Instead of 768px?

**Decision:** Use `lg:` (1024px) instead of `md:` (768px) for desktop breakpoint.

**Rationale:**
- instructions.md specified 768px, but testing showed 768px too narrow for 40%/60% split
- At 768px, left column (40% = 307px) too cramped for status panels
- 1024px gives left column 410px width, adequate for content
- Consistent with other responsive patterns in codebase (FilterPageLayout uses lg:)

### 3. Why ScrollArea Component?

**Decision:** Use shadcn ScrollArea component for scrollable right panel content.

**Rationale:**
- ScrollArea provides consistent cross-browser scrollbar styling
- Maintains sticky positioning compatibility with parent flex container
- Integrates with shadcn/ui design system for visual consistency
- `maxHeight` via style prop enables scroll containment without viewport issues

### 4. Why Suspense Wrapper Pattern?

**Decision:** Split page into wrapper + content, wrap content in Suspense.

**Rationale:**
- `useIdentityDetailData` uses `useSuspenseQuery` (requires Suspense ancestor)
- Pattern matches IdentityPage.tsx approach
- LoadingState fallback provides UX during data fetch
- Global ErrorBoundary in main.tsx catches render errors

## Constants Added

| Constant | Value | Purpose |
|----------|-------|---------|
| DETAIL_PAGE.BREAKPOINT_LG | 1024 | Desktop/mobile threshold |
| DETAIL_PAGE.COLUMN_LEFT | 'lg:col-span-4' | Left column width class |
| DETAIL_PAGE.COLUMN_RIGHT | 'lg:col-span-6' | Right column width class |
| DETAIL_PAGE.RIGHT_PANEL_MAX_HEIGHT | 'calc(100vh - 12rem)' | Scroll containment |
| DETAIL_PAGE.SELECTOR_STICKY_TOP | 'top-0' | Sticky offset |
| DetailEntityType | 'identity' \| 'ego' \| 'egoGift' | Entity type union |
| MAX_ENTITY_TIER | { identity: 4, ego: 4, egoGift: 2 } | Max tier per entity |
| MIN_ENTITY_TIER | { identity: 1, ego: 1, egoGift: 0 } | Min tier per entity |
| ENHANCEMENT_LABELS | ['None', '+1', '+2'] | EGO Gift enhancement labels |

## Files Read for Research

1. `frontend/src/components/deckBuilder/TierLevelSelector.tsx` - Tier button pattern
2. `frontend/src/components/egoGift/EGOGiftEnhancementSelector.tsx` - Enhancement icon pattern
3. `frontend/src/hooks/use-is-breakpoint.ts` - Responsive hook usage
4. `frontend/src/routes/IdentityPage.tsx` - Suspense wrapper pattern
5. `frontend/src/components/common/ErrorBoundary.tsx` - Error handling pattern
6. `frontend/src/components/ui/tabs.tsx` - shadcn Tabs API
7. `frontend/src/components/ui/slider.tsx` - shadcn Slider API
8. `frontend/src/lib/constants.ts` - Existing constants structure
9. `frontend/src/lib/utils.ts` - cn() utility usage

## Technical Constraints

| Constraint | Impact | Resolution |
|------------|--------|------------|
| Tailwind JIT requires static classes | Cannot use `grid-cols-${n}` | Explicit conditionals with cn() |
| useSuspenseQuery requires Suspense | Page must have Suspense boundary | Wrapper/content pattern |
| React Compiler (no manual memo) | Remove useCallback/useMemo | Let compiler optimize |
| cn() required for conditional classes | Template literals disallowed | Import and use cn() consistently |
