# Task: Start Gift

## Description
Implement a start gift selection section and append it to the planner editor.
This section consists of ten selectable elements with keyword and corresponding gifts, in grid. Note that only one element can be selected at once. Clicking another one changes selection.
Each element has a keyword icon on the left and the corresponding ego gifts on the right. The ego gifts are represented as the gift card.
When a keyword is selected, then the user can select the corresponding gift. The default number of selection is one, and the selected start buffs can increase the gift selection EA.
When a gift is on hover, show its name colored by its attribute type and zero enhancement description.

## Research
- Users cannot select the start ego gift until the keyword category is selected.
- The keyword and keyword-specific ego gift list is in `/static/data/MD6/startEgoGiftPools.json`.
- The implementation must consider version control of the mirror dungeon so that users still can utilize the info of the previous version after a mirror dungeon is introduced.
- The size of the pool for each keyword is fixed - three.
- The mirror dungeon-specific data's naming convention is changed from `data/{name}MD{version}.json` to `data/MD{version}/{name}.json`.
- Since we need to display the gift description, we must load ego gift description json file in `/static/i18n/{lang}/egoGift/{gift_id}/json`. For now, the number of gift is 30. However, since we will implement the comprehensive ego gift list with 300+ gifts, we must build an architecture that handles those separate json files wisely.
- The additional ego gift selection is determined by selected start buff's `ADDITIONAL_START_EGO_GIFT_SELECT` effects.

## Scope
- `/frontend/src/components/egoGift/EGOGiftCard.tsx`
- `/frontend/src/routes/PlannerMDNewPage.tsx`

## Target Code Area
- `/frontend/src/components/`
- `/frontend/src/routes/PlannerMDNewPage.tsx`

## Testing Guidelines