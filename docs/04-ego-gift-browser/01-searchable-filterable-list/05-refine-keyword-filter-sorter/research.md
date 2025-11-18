# Research: Refine Keyword Filter & Sorter

## Overview of Codebase

- Data source uses `category` (single string) and `keywords` (array) fields in EGOGiftSpecList.json
- Current implementation filters on `keywords` array, not `category` field
- KEYWORD_ORDER constant defines filter order and sorting priority for 11 keywords including "Common"
- IconFilter component (common/IconFilter.tsx) is reusable generic icon-based filter used across multiple pages
- Keyword filter currently excludes "Common" with explicit TODO comment to add it
- Gift cards display ALL keywords as icons in lower-right corner with fallback to text badges for missing icons
- Filter logic uses OR semantics: gifts matching ANY selected keyword are shown
- State management uses React hooks with Set<string> for O(1) membership checking
- TailwindCSS v4 with inline classes, shadcn/ui-style components, no CSS modules
- Button component uses CVA for variants but Sorter uses inline styles for consistency
- i18n support via keywordMatch.json maps internal PascalCase to display names
- Icon paths follow pattern: /images/icon/statusEffect/${keyword}.webp
- Sorting logic has two modes: tier-first and keyword-first with ID as tiebreaker
- EGOGiftPage unique in having Sorter component alongside filter (Identity/EGO pages don't)
- All filter components standardized at h-14 height for visual consistency

## Codebase Structure

- /static/data/EGOGiftSpecList.json contains gift data with category and keywords fields
- /frontend/src/components/egoGift/ houses EGOGiftKeywordFilter, EGOGiftList, EGOGiftCard, EGOGiftSearchBar
- /frontend/src/components/gift/ contains display-only components (CostDisplay, GiftName, etc.) unrelated to filtering
- /frontend/src/components/common/ provides reusable IconFilter, Sorter, SearchBar components
- /frontend/src/routes/EGOGiftPage.tsx manages page-level state and layout composition
- /frontend/src/lib/egoGiftSort.ts contains sorting logic and SortMode type
- /frontend/src/lib/constants.ts defines KEYWORD_ORDER and Keyword type
- /frontend/src/lib/assetPaths.ts provides getStatusEffectIconPath helper
- /frontend/src/lib/utils.ts provides getKeywordDisplayName for i18n
- /frontend/src/types/EGOGiftTypes.ts defines EGOGiftSpec and EGOGift interfaces
- /static/i18n/EN/keywordMatch.json maps keywords to display names (no "Common" entry)
- Layout: filters and sorter on left with flex gap-4, search bar right-aligned with shrink-0

## Gotchas and Pitfalls

- "Common" keyword exists in KEYWORD_ORDER but has no keywordMatch.json entry for display name
- Must verify Common.webp icon exists before adding to filter UI
- Gifts with empty keywords array treated as "Common" in sorting (lowest priority)
- Multiple keywords per gift: only FIRST matching KEYWORD_ORDER determines sort position
- EGOGiftCard has error fallback creating text badges for missing icons dynamically
- KEYWORD_ORDER is "as const" type - maintain type safety when adding/modifying
- IconFilter has horizontal scroll for many icons - ensure "Common" doesn't overflow unexpectedly
- Task involves consolidating /components/gift/ to /components/egoGift/ - check for import dependencies
- Recent commits show ongoing work on EGO gift features - coordinate with existing changes
- Current branch name must start with 'claude/' and end with session ID for push permissions
- SearchBar uses 100ms debounce delay - consider UX when adding new filter interactions
- Dark mode support via .dark class - test new filter elements in both themes
- Sorter uses inline Tailwind not Button component - maintain consistency if modifying
- Set<string> state for selected keywords - must convert to/from arrays when needed
- Filter height h-14 standard - new Common button must match for alignment
