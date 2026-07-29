# Research: Start Gift Selection

## Overview of Codebase

- **Stack**: React 19.2 + TypeScript 5.9, TanStack Router/Query, Tailwind CSS, shadcn/ui
- **Build**: Vite with `@static` alias pointing to `/static` directory
- **Data fetching**: React Query with 7-day staleTime, Zod validation on JSON imports
- **i18n**: react-i18next with JSON files in `/static/i18n/{lang}/`
- **StartBuffSection**: Closest existing pattern - container with multi-select, grid layout (5 cols)
- **Selection pattern**: `useState<Set<number>>` for multi-select, callbacks with positive/negative IDs
- **Parent page**: `PlannerMDNewPage.tsx` at line 139 manages all state: keywords, buffs, deck, title
- **Start buff integration**: Lines 227-231 show section integration via props (mdVersion, selectedIds, onSelectionChange)
- **EGOGiftCard**: Link-based card component (lines 18-79), displays icon, name, tier badge
- **Gift spec data**: `egoGiftSpecList.json` contains all gift metadata (tier, keyword, attributeType)
- **Gift names**: `egoGiftNameList.json` has ID-to-name mappings in each language folder
- **Gift descriptions**: `i18n/{lang}/egoGift/{id}.json` has name, descs array (3 levels), obtain field
- **Description format**: `descs[0]` = base, `descs[1]` = +1, `descs[2]` = +2; use index 0 per instructions
- **Style tags**: Descriptions use `<style="upgradeHighlight">text</style>` for enhanced portions
- **Color coding**: `colorCode.json` maps attributeType (CRIMSON=#A0392B, SCARLET=#BB521F, etc.)
- **Version control**: MD6 folder structure (`/static/data/MD6/`) supports future MD version separation

## Codebase Structure

- **Page**: `frontend/src/routes/PlannerMDNewPage.tsx` - main planner page
- **Start buff components**: `frontend/src/components/startBuff/` - StartBuffSection, StartBuffCard
- **EGO gift components**: `frontend/src/components/egoGift/` - EGOGiftCard, EGOGiftList, GiftName, GiftImage
- **Asset paths**: `frontend/src/lib/assetPaths.ts` - all image path helpers (~80 functions)
- **Constants**: `frontend/src/lib/constants.ts` - STATUS_EFFECTS, AFFINITIES, KEYWORD_ORDER arrays
- **Types**: `frontend/src/types/StartBuffTypes.ts` - BuffEffect interface with `type` and `value` fields
- **Hooks**: `frontend/src/hooks/useStartBuffData.ts` - React Query pattern for buff data loading
- **Gift pool data**: `static/data/MD6/startEgoGiftPools.json` - 10 keywords, 3 gift IDs each
- **Gift specs**: `static/data/egoGiftSpecList.json` - tier, keyword, attributeType per gift
- **Gift names**: `static/i18n/{lang}/egoGiftNameList.json` - localized names
- **Gift descriptions**: `static/i18n/{lang}/egoGift/{id}.json` - 200+ files with name, descs[], obtain
- **Individual gift data**: `static/data/egoGift/{id}.json` - attributeType, tag, keyword, price
- **Color codes**: `static/data/colorCode.json` - attributeType to hex color mapping
- **Gift images**: `static/images/egoGift/{id}.webp` - gift icons
- **Status effect icons**: `static/images/icon/statusEffect/{keyword}.webp` - keyword icons

## Gotchas and Pitfalls

- **Gift selection EA**: Default=1, increased by selected buffs with `ADDITIONAL_START_EGO_GIFT_SELECT` effect
- **ADDITIONAL_START_EGO_GIFT_SELECT effect**: Found in startBuffs.json effects array; `value` field = extra gifts
- **Effect sum logic**: Only count from SELECTED buffs (those in selectedBuffIds Set), not all buffs
- **200+ gift files**: i18n folder has 200+ egoGift files - must lazy-load descriptions on hover
- **Description index**: Use `descs[0]` for zero enhancement per instructions
- **Style tags**: Must parse `<style="upgradeHighlight">` tags - differs from color tags in buff descriptions
- **attributeType colors**: Use `colorCode.json` to get hex from attributeType for gift name coloring
- **Single keyword selection**: "Only one element can be selected at once" - single-select for keyword row
- **Keyword gating**: Users cannot select gifts until keyword category is selected first
- **Pool size fixed**: Each keyword has exactly 3 gifts - from startEgoGiftPools.json
- **Icon paths**: Use `getEGOGiftIconPath(id)` for gifts, `getStatusEffectIconPath(keyword)` for keywords
- **Version awareness**: Data path must include MD version for future compatibility
- **Two-tier loading**: SpecList/NameList for list view; individual files for descriptions on hover
- **Hover tooltip**: Need custom tooltip component - current cards use simple `title` attribute
