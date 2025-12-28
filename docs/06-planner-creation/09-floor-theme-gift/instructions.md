# Task: Floors, Themes, and Gifts

## Description
Create floor sections and append them to planner editor. Each section is composed with theme pack selector on the left and floor-specific ego gift viewer on the right.
The theme pack selector consists of theme pack difficulty indicator on the top and the theme pack viewer-selector on the bottom.
The theme pack difficulty indicator shows the theme pack difficulty (NORMAL, HARD, INFINITY MIRROR, EXTREME MIRROR - not i18n) It is empty when no theme pack is selected.
The color of normal is yellow, hard #2596be, infinity #dc070c, extreme #2596be, infinity #ffffff
The theme pack viewer-selector shows a selected theme pack structure. It shows place holder when empty. When users click the theme pack, the page opens the theme pack selector pane.
The pane shows difficulty selector (normal, hard, extreme, if condition is met) and theme pack list in vertical order. In the theme pack list, there are theme pack which satisfies difficulty and floor which is in exceptionConditions field in themePackList.json.
The ego gift viewer shows the floor-specific selected ego gift. If empty, it shows a i18n-compatible placeholder text like "Click here to select E.G.O gifts". When Clicked, it shows the ego gift selector pane. The floor-wise selection state is defined in planner editor and modified by the selector pane. The viewer just reads the state and shows the gift.

## Research
- Theme pack structure
  - Layer 1: id-specific theme pack image read by themePackConfig.packSpriteId
  - Layer 2: optional: boss image read by themePackConfig.bossSpriteId
  - Layer 3: alert icon (warning.webp, baton.webp) regarding themePackConfig.isShowWarning or themePackConfig.isShowBaton
  - Layer 4: Limbus Company logo: hidden when themePackConfig.isHideLogo is true
  - Layer 5: Theme pack name in /static/i18n/{lang}/themePack.json, colored by themePackConfig.textColor
    - Some theme pack has specialName in the i18n json file with color tag, so ignore the textColor and use the tag.
    - The position of the name is different between normal packs and extreme packs
  - Layer 6: attackType, attributeType on the lower-left. Attribute first, and then attack type
  - Layer 7: Theme pack frame
    - frame.webp for normal theme packs, extremeFrame.webp for extreme theme packs
    - The extreme theme pack has only "dungeonIdx: 3" in exceptionConditions field
  - Layer 8: Theme pack highlight frame
    - {onHover|onSelect}.webp for normal theme packs, extremeHighlight.webp for extreme theme packs
  - The position of the layer 3-5 must be configurable by user.
- Note that the size of theme pack, frame, and highlight must be the same
- Five sections for 5F, ten sections for 10F, and 15 sections for 15F
- The floor-wise theme pack, difficulty, and ego gift data must be handled by the planner editor.
- In exceptionConditions field, dungeonIDx means difficulty (0 for normal, 1 for hard, 3 for extreme. No 2 or infinity)
  - The selectableFloors field means the floors where the theme pack appears. 0 for 1F, 1 for 2F, ..., and 4 for 5-10F.
  - Note that there is no selectableFloors in extreme mode. 11-15F is only for extreme mode.
- Difficulty rules for the selector
  - Show normal when the floor is 1F or the previous floor's difficulty is normal
  - Show hard when the floor is 1-10F
  - Show extreme when the floor is 11-15F
- Difficulty indicator rules
  - Show NORMAL or HARD in 1-5F, following user's difficulty selection
  - Show INFINITY MIRROR in 6-10F
  - Show EXTREME MIRROR in 11-15F
- Use EGOGiftSelectorList for the pane of the floor-wise gift selector
- The EGOGiftSelectorList shows the theme-specific gift first and then the common gift (a gift with empty theme pack value - []). Read egoGiftSpecList.json and get themePack field.

## Scope
`/frontend/src/components/egoGift/EGOGiftSelectionList.tsx`
`/frontend/src/routes/PlannerMDNewPage.tsx`
`/static/images/UI/themePack/`

## Target Code Area
`/frontend/src/components/`
`/frontend/src/routes/PlannerMDNewPage.tsx`

## Testing Guidelines