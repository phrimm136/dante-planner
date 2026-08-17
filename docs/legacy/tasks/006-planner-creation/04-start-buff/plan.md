# Implementation Plan: Start Buff Section

## Clarifications Resolved
- **Selection behavior**: Multiple buffs can be selected simultaneously
- **Default state**: No pre-selected buffs on page load
- **Buff card layout**: 2-row grid layout (5 cards per row)
- **Enhanced cost display**: Enhanced buffs show cost with gold color (#f8c200)
- **Buff name display**: Fixed width container; text must auto-resize to fit; append "+" or "++" suffix for enhancement levels
- **Data loading**: Use TanStack Query function for loading JSON files
- **State storage**: Store selected buffs as full IDs (e.g., 100, 201, 302) which encode both baseId and enhancement level

## Task Overview
Add a Start Buff section to the planner editor that displays 10 buff cards in a 2-row grid. Each card shows the buff icon, name (auto-sized with +/++ suffix), cost, description (with dynamic values from effects data), and enhancement buttons (+/++). Users can select multiple buffs and toggle enhancement levels independently.

## Steps to Implementation

1. **Add asset path helpers**: Create functions in `assetPaths.ts` for buff icons, pane, highlight, star, and enhancement button images

2. **Create types and constants**: Define TypeScript types for StartBuff, BuffEffect; add BASE_BUFF_IDS constant and utility to extract baseId/level from full ID

3. **Create TanStack Query hook**: Build `useStartBuffData` hook to load `/static/data/startBuffsMD6.json` and `/static/i18n/{lang}/startBuffsMD6.json` with proper caching

4. **Build description formatter utility**: Create function to parse i18n strings with `{0}`, `{1}` placeholders and `<color=...>` tags, mapping effect values/referenceData to correct positions

5. **Create auto-sizing text component**: Build utility to measure and scale text to fit within fixed width container for buff names

6. **Create StartBuffCard component**: Build card with pane background, icon (upper-left), auto-sized name with +/++ suffix (upper-right), cost with star and gold color when enhanced (top-right), description (center), enhancement buttons (bottom)

7. **Implement enhancement button sub-component**: Handle mutually exclusive toggle state, show correct background/icon images based on selected level (0, 1, 2)

8. **Add hover/selection highlight**: Overlay `startBuffHighlight.webp` on hover or when card is selected, matching pane dimensions exactly

9. **Create StartBuffSection container**: Manage selection state as `Set<number>` of full buff IDs, use query hook for data, render as 2-row grid

10. **Integrate into PlannerMDNewPage**: Add StartBuffSection below existing sections with appropriate label and styling

## Success Criteria
- All 10 buff cards render in a 2-row grid (5 per row)
- Buff names auto-resize to fit fixed width, with "+" or "++" suffix for enhanced buffs
- Multiple buffs can be selected simultaneously
- Enhancement buttons toggle correctly (mutually exclusive within a card, can deselect)
- Enhanced buffs display cost in gold color (#f8c200)
- Description text updates dynamically using `customLocalizeTextId` when present
- Color codes from i18n render as styled spans
- Hover and selection states show highlight overlay
- Data loaded via TanStack Query with proper caching and language support
- Selection state stored as full buff IDs (100-109, 200-209, 300-309)

## Assumptions Made
- **ID utilities**: Will create helper functions to extract baseId and enhancement level from full ID
- **Text scaling**: Use CSS transform scale or font-size adjustment to fit text in container
- **referenceData placeholder order**: {0}=activeRound, {1}=buffKeyword, {2}=stack, {3}=turn, {4}=limit
- **Grid responsiveness**: 2-row layout fixed; cards may scale or wrap on smaller screens
