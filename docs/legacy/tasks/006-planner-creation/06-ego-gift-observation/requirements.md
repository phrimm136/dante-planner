# Task: EGO Gift Observation

## Description
Append EGO gift observation section to the planner creator/editor. The upper-right is observation cost, the left is ego gift selection list, and the right is "showroom" of the three selected gifts.
The observation cost section consists of starlight image and cost number. The cost differs according to the number of the selection. The cost data is in `/static/data/MD6/egoGiftObservationData.json`.
The gift selection list has the same structure of the ego gift list in `/ego-gft` page. Unlike the existing one, this list takes ego gift id list, only gifts with the corresponding id in the list is shown. Also, clicking each ego gift card do not pass the user to the detail page. Instead, it makes that gift selected/unselected. Hovering on the card shows the related gift's name and base level description. The specified ego gift id list is in `/static/data/MD6/egoGiftObservationData.json`.
The "showroom" shows the selected ego gift. Currently, the selected gift EA is three. When a gift card is clicked, it is removed from the selection. Hovering also shows its name and description.
The list of the selected gift is propagated to the planner editor so that it can be saved in db later.

## Research
- Many of components are implemented already. starlight-cost pair in start buff section, ego gift selection card in start ego gift selection section, ego gift list as shown in `/ego-gift` page, and so on. Extract them for reusability.
- The ego gift selection list will be used later for selecting gifts in the entire pool. Name it to capture universality.
- Reconstruct the ego gift card. It must have background image, tier indicator on the upper-left, enhancement indicator on the upper-right, keyword icno on the lower-right.
  - The background is `/static/images/UI/egoGift/bg.webp` with base level, `/static/images/UI/egoGift/bg.webp` and `/static/images/UI/egoGift/bgEnhanced.webp` with +1, and `/static/images/UI/egoGift/bgEnhanced2.webp` and `/static/images/UI/egoGift/bgEnhanced.webp` with +2.
    - Although the size of bg images are the same, the size of bgEnhanced.webp must be the same as the heptagon in `/bg.webp`. Use python to analyze pixel-level size of the images and determine the scale.
  - The tier indicator is #fcba03-colored text 'I', 'II', 'III', 'IV', 'V' for numerical tier (note that they are not roman number but alphabet representation) or icon in `/static/images/UI/egoGift/tierEX.webp`.
  - The enhancement indicator has no image for the base level, `/static/images/UI/egoGift/enhancement1.webp` for +1, and `/static/images/UI/egoGift/enhancement2.webp` for +2.
  - The keyword indicator is an icon of the gift keyword. If the keyword is null or "None", no icons are displayed.

## Scope
`/frontend/src/components/egoGift/EGOGiftCard.tsx`
`/frontend/src/components/startBuff/StartBuffSection.tsx`
`/frontend/src/components/startGift/StartGiftRow.tsx`
`/frontend/src/routes/PlannerMDNewPage.tsx`

## Target Code Area
`/frontend/src/components/`
`/frontend/src/routes/PlannerMDNewPage.tsx`