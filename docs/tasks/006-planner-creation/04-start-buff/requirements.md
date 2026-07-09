# Task: Start Buff

## Description
Add start buff section to the planner editor.
The start buff section has ten buff cards. Each buff card consists of `/static/images/UI/MD6/startBuffPane.webp`, star light image in `/static/images/UI/MD6/starLight.webp` and buff cost on the top-right, `/static/images/UI/MD6/StartBuffIcon_10[0-9].webp` on the upper-left, buff name `mirror_dungeon_5_buffs_title_100[0-9]` in `/static/i18n/{lang}/startBuffsMD6.json` on the upper-right, buff description in `/static/i18n/{lang}/startBuffsMD6.json` and `/static/data/startBuffsMD6.json` on the center, and '+' and '++' enhancement button on the bottom.
Hovering or selecting each buff card highlights it with covering by `/static/images/UI/MD6/startBuffHighlight.webp`. Those highlight and pane size must be the same.
Pressing on of the enhancement buttons (mutual exclusive) give the buff enhancement, changing the buff id. No enhancement: `10[0-9]`, one enhancement (+): `20[0-9]`, two enhancement (++): `30[0-9]`.
Those enhancement button is composed with `/static/images/UI/MD6/startBuffEnhancementUnselected.webp` and `/static/images/UI/MD6/startBuffEnhancementIcon.webp`. Note that the two enhancement button has two icons in horizontal order. Clicking the one enhancement button changes the images into `/static/images/UI/MD6/startBuffEnhancement1Selected.webp` and `/static/images/UI/MD6/enhancementIcon1.webp`. Clicking the two one changes the images into `/static/images/UI/MD6/startBuffEnhancement2Selected.webp` and `/static/images/UI/MD6/enhancementIcon2.webp`. Clicking them again rolls back the images.

## Research
- How to handle ids to keep enhancement and selection?
- How to show description in those files? They are separated into `effects` in `/static/data/startBuffsMD6.json` and `{corresponding key}` in `/static/i18n/{lang}/startBuffsMD6.json`. the i18n description is formatted and can accept arguments in values in `effects`. Those argument has two types, one for `value` and `value2`, and another for `referenceData`. It seems its children's order is activeRound, buffKeyword, stack, turn, and limit.
- The enhancement button image can be stretched horizontally.
- Enhanced buffs must use customLocalizeTextId.

## Scope
- 

## Target Code Area
- `/frontend/src/components/`
- `/frontend/src/routes/PlannerMDNewPage.tsx`

## Testing Guidelines