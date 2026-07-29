# Research: Start Buff Section

## Overview of Codebase

### Existing Patterns and Practices
- Uses React with TypeScript, functional components with hooks (`useState`, `useCallback`, `useMemo`)
- i18n via `react-i18next` with `useTranslation()` hook for multi-language support
- TailwindCSS for styling with shadcn/ui components (`Button`, `DropdownMenu`, etc.)
- Asset paths centralized in `/frontend/src/lib/assetPaths.ts` with typed helper functions
- Sub-components extracted as reusable pieces (e.g., `KeywordSelector` in PlannerMDNewPage)
- Constants centralized in `/frontend/src/lib/constants.ts` (e.g., `PLANNER_KEYWORDS`)
- State managed locally with React hooks; no global state store for planner

### Component Patterns from PlannerMDNewPage
- Labeled sections with `<label className="text-sm font-medium">` pattern
- Card-based panels with `bg-card border border-border rounded-md` styling
- Selection states tracked with `Set<string>` for multi-select, single value for exclusive select
- Toggle behavior: click to select/deselect, visual feedback via border and background color
- Hover states: `hover:border-primary/50 transition-colors` for interactive elements

### Enhancement/Level Patterns from EGO Gifts
- Enhancement icons overlay positioned absolutely on cards (`absolute -top-2 -right-2`)
- Grade/tier shown with separate icon image
- Enhancement state as numeric level (0, 1, 2)
- `getEGOGiftEnhancementIconPath(level)` pattern for level-specific icons

## Codebase Structure

### Data Files
- `/static/data/startBuffsMD6.json` - Contains 30 buff entries (10 base x 3 levels)
  - Keys: "100"-"109" (level 1), "200"-"209" (level 2), "300"-"309" (level 3)
  - Each entry has: `level`, `baseId`, `cost`, `localizeId`, `effects[]`, `uiConfig.iconSpriteId`
  - Effects contain: `type`, `value`, `value2`, `isTypoExist`, optional `customLocalizeTextId`, optional `referenceData`

### i18n Files
- `/static/i18n/{lang}/startBuffsMD6.json` - Contains buff titles and effect descriptions
  - Buff names: `mirror_dungeon_5_buffs_title_10X` where X is 0-9
  - Effect descriptions: use `{0}`, `{1}` placeholders for values
  - Color codes embedded: `<color=#5bffde>` (cyan highlight), `<color=#f8c200>` (gold highlight)
  - Enhanced versions use `_TEXT_HIGHLIGHT` suffix keys for different coloring

### Asset Files (all in `/static/images/UI/MD6/`)
- Buff icons: `StartBuffIcon_100.webp` through `StartBuffIcon_109.webp` (10 icons)
- Card background: `startBuffPane.webp`
- Selection highlight overlay: `startBuffHighlight.webp`
- Star decoration: `starLight.webp`
- Enhancement unselected: `startBuffEnhancementUnselected.webp`, `startBuffEnhancementIcon.webp`
- Enhancement +1 selected: `startBuffEnhancement1Selected.webp`, `enhancementIcon1.webp`
- Enhancement +2 selected: `startBuffEnhancement2Selected.webp`, `enhancementIcon2.webp`

### Target Code Areas
- Main page: `/frontend/src/routes/PlannerMDNewPage.tsx`
- New components should go in: `/frontend/src/components/planner/` or `/frontend/src/components/startBuff/`
- Asset helpers: `/frontend/src/lib/assetPaths.ts` (add new helper functions)

## Gotchas and Pitfalls

### ID Structure Complexity
- Buff ID encodes both base type AND enhancement level: 10X (base), 20X (+1), 30X (+2)
- Icon uses baseId only: `StartBuffIcon_100.webp` not `StartBuffIcon_200.webp`
- Selection should track which base buff is chosen AND which enhancement level
- Consider storing as `{ baseId: number, enhancementLevel: 0 | 1 | 2 }` rather than full ID

### i18n Description Formatting
- Placeholders use numbered format: `{0}` for `value`, `{1}` for `value2`
- Some effects have `referenceData` with: `buffKeyword`, `stack`, `turn`, `activeRound`, `limit`
- For `referenceData`, placeholder order: `{1}` = buffKeyword, `{2}` = stack, `{3}` = turn, `{4}` = limit
- Color codes must be preserved/rendered as styled spans in React
- Use `customLocalizeTextId` when present, otherwise fall back to `type` as translation key

### Enhancement Button Behavior
- Buttons are mutually exclusive: selecting +1 deselects +2 and vice versa
- Can toggle off: clicking selected enhancement returns to base (no enhancement)
- Visual states: unselected shows `startBuffEnhancementUnselected.webp`, selected shows level-specific image
- +2 button shows TWO enhancement icons horizontally

### Asset Loading Notes
- All assets are webp format for performance
- Enhancement button background can be stretched horizontally (as per instructions)
- Highlight overlay (`startBuffHighlight.webp`) must match pane size exactly

### State Management Considerations
- Only 10 base buffs exist, each with 3 enhancement levels (none, +1, +2)
- Selection and enhancement are independent states per buff
- Consider if multiple buffs can be selected or only one at a time
- Enhancement changes the buff cost (higher level = higher cost)

### Translation Key Mapping
- Some effect types have inconsistent key naming (e.g., `ACQUIRE_3_TIER_RANDOM_MATERIAL_EGO_GIFT_ON_ENTER_SHOP_2`)
- Must check if `customLocalizeTextId` exists before using `type` as fallback
- Buff names always use format `mirror_dungeon_5_buffs_title_{baseId}`
