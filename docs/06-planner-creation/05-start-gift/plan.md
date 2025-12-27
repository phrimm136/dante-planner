# Implementation Plan: Start Gift Selection

## Clarifications Needed

No clarifications needed - requirements are clear.

## Task Overview

Implement a start gift selection section for the Mirror Dungeon planner. The section displays 10 keyword rows in a horizontal grid layout. Each row has a keyword icon on the left and 3 corresponding EGO gift cards on the right. Users select one keyword row, then choose gifts from that row. Selection count is 1 by default, increased by selected start buffs with `ADDITIONAL_START_EGO_GIFT_SELECT` effects. Gift hover shows name (colored by attributeType) and base description. When selection EA changes, reset all gift selections.

## Steps to Implementation

1. **Create data loading hook**: Create `useStartGiftData` hook to load startEgoGiftPools.json, egoGiftSpecList.json, egoGiftNameList.json, and colorCode.json using React Query pattern from useStartBuffData
2. **Create gift description hook**: Create `useEgoGiftDescription` hook for lazy-loading individual gift i18n files on demand (for hover tooltips)
3. **Create description parser utility**: Parse `<style="upgradeHighlight">` tags and render text inside with #f8c200 color
4. **Create StartGiftCard component**: Small gift card component displaying icon and name, with shadcn/ui Tooltip showing colored name + description (using descs[0])
5. **Create StartGiftRow component**: Horizontal row with keyword icon (left) and 3 StartGiftCards (right) in flex/grid layout, handles per-row gift selection state
6. **Create StartGiftSection component**: Container managing keyword selection (single-select), gift selection state, and EA calculation from selected buffs; resets gift selection when EA changes
7. **Add EA calculation utility**: Function to sum `ADDITIONAL_START_EGO_GIFT_SELECT` effect values from selected buffs
8. **Integrate into PlannerMDNewPage**: Add StartGiftSection after StartBuffSection, pass selectedBuffIds for EA calculation
9. **Add asset path helpers**: Add `getStartGiftKeywordIconPath` if needed, verify existing paths work

## Success Criteria

- 10 keyword rows displayed vertically, each row horizontal: keyword icon left, 3 gift cards right
- Only one keyword row can be selected at a time (clicking another deselects previous)
- Gifts cannot be selected until their keyword row is selected
- Number of selectable gifts = 1 + sum of ADDITIONAL_START_EGO_GIFT_SELECT from selected buffs
- Gift selection resets when EA changes (buff selection changes)
- Gift hover shows shadcn/ui Tooltip with name colored by attributeType and base description (descs[0])
- `<style="upgradeHighlight">` tags in descriptions render text inside with #f8c200 color
- Data loading uses React Query with appropriate caching
- Component accepts mdVersion prop for future version compatibility

## Assumptions Made

- **Keyword icons**: Will reuse existing `getStatusEffectIconPath` for keyword icons (Combustion, Slash, etc.)
- **Selection persistence**: Gift selection resets when keyword changes (selecting new keyword clears previous gifts)
- **EA change behavior**: Reset all gift selections when EA changes due to buff selection changes
- **upgradeHighlight style**: Text wrapped in `<style="upgradeHighlight">` tags will be colored #f8c200 (gold/yellow)
