# Implementation Plan: Refine Keyword Filter & Sorter

## Clarifications Received

- **Common button design**: Text-only button (no icon)
- **Common button placement**: Right after Blunt button (last position) within the keyword filters
- **Common display name**: Use i18n text "Common" (not treated as keyword mapping)
- **Common button styling**: Follow Button component variant (not inline styles)
- **Component consolidation scope**: Move all /components/gift/ files to egoGift/ and update all import references
- **Selection interaction**: OR logic applies - Common works like any other filter button

## Task Overview

Refactor the EGO gift filtering system to use the single `category` field instead of the `keywords` array. This simplifies the data model by treating each gift as having one primary category. Add a text-based "Common" filter button to handle gifts without specific keywords. Update gift cards to display only the single category icon. Consolidate component directories for better organization.

## Steps to Implementation

1. **Verify data consistency**: Check EGOGiftSpecList.json to ensure category field values align with KEYWORD_ORDER and identify any data inconsistencies
2. **Add Common i18n text**: Add "Common" translation key to i18n files (not keywordMatch.json since it's not a keyword)
3. **Update keyword filter component**: Modify EGOGiftKeywordFilter to include "Common" as text-only Button component after icon filters
4. **Refactor filtering logic**: Update EGOGiftList to filter based on category field instead of keywords array
5. **Refactor sorting logic**: Update egoGiftSort.ts to use category field for keyword-based sorting instead of getKeywordIndex
6. **Update gift card display**: Modify EGOGiftCard to show single category icon from category field instead of mapping over keywords array
7. **Update TypeScript types**: Modify EGOGiftTypes.ts to reflect that category is the primary field (consider deprecating keywords)
8. **Consolidate component directories**: Move all files from /components/gift/ to /components/egoGift/ and update all import references throughout codebase
9. **Verify icon assets**: Ensure Common.webp exists or implement appropriate text fallback for Common category
10. **Test all filter combinations**: Verify filtering, sorting, and display work correctly with Common category included

## Success Criteria

- Filtering operates on single category field instead of keywords array
- "Common" appears as filterable option in keyword filter UI
- Gift cards display exactly one category icon in lower-right corner based on category field
- Sorting correctly prioritizes by category field using KEYWORD_ORDER
- All components from /components/gift/ successfully consolidated into /components/egoGift/
- No import errors or broken references after component consolidation
- Filter UI maintains h-14 height standard and visual consistency
- Dark mode support works correctly for new Common button

## Assumptions Made

- Common button will integrate into EGOGiftKeywordFilter component as part of the same filter group
- Common button will use Button component's default or outline variant for consistency with UI library patterns
- i18n key for "Common" will be added to appropriate translation files following existing conventions
- Existing JSON data already has valid category values that match KEYWORD_ORDER entries
- Gift card display will show text fallback for Common category if Common.webp icon doesn't exist
- TypeScript types can be updated without breaking existing code that references keywords field
