# Task: Comprehensive EGO Gift List

## Description
Append a comprehensive ego gift list to the planner editor.
The comprehensive ego gift list has the same structure and components as the ego gift observation list. The difference is that, it takes empty gift list so that every possible ego gift can be shown, and on hover every ego gift, a user can select enhancement level.
- With no selection, clicking the hovered enhancement level makes the gift selected and change the level.
- With selection, clicking the same level make the gift unselected, resetting the level to the base.
- With selection, clicking the different level changes the enhancement level.
Note that the selected ego gift must be saved for the later use.

## Research
- The selected ego gift is represented as a numeric string containing enhancement level and the gift id. Ex) 19001 = 1+ 9001 ego gift, 9002 = 9002 ego gift with no enhancement
- The selected ego gift list can be mutated by other sections. let the list track the selection array and highlight the gift / show the corresponding enhancement level.
- To support showing enhancement level for the selected gift, you may need to modify EGOGiftSelectionList.
- To refer how to show enhancement level hover, see TierLevelSelector.
- We want to integrate EGOGiftCard and EGOGiftMiniCard. To do so, extract view-only components into EGOGiftCard, wrap it to be EGOGiftCardLink or EGOGiftCardSelection. The size must be w-24 and h-24.

## Scope
- `/frontend/src/components/egoGift/EGOGiftSelectionList.tsx`
- `/frontend/src/routes/PlannerMDNewPage.tsx`
- `/frontend/src/components/deckBuilder/TierLevelSelector.tsx`
- `/frontend/src/components/egoGift/EGOGiftCard.tsx`

## Target Code Area
- `/frontend/src/components/egoGift/EGOGiftSelectionList.tsx`
- `/frontend/src/routes/PlannerMDNewPage.tsx`
- `/frontend/src/components/egoGift/`

## Testing Guidelines
Run playwright.
- With no selection, clicking the hovered enhancement level makes the gift selected and change the level.
- With selection, clicking the same level make the gift unselected, resetting the level to the base.
- With selection, clicking the different level changes the enhancement level.
- Filtering, sorting, and search must work well. Click Combustion, shift the sorting criteria, and put Charge in query form to see the query mechanism works well.