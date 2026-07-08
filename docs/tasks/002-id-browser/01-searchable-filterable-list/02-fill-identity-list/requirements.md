# Task: Fill Identity List

## Description
Fill the identity list with identities. Read `/static/data/identitySpecList.json` and `/static/i18n/{languagePreference}/identityNameList.json`, merge them, load the corresponding images and put them together.

## Research
- The image combination is, from the back, identity image, uptie frame, sinner bg on the upper-right, and thn sinner icon. Each of them are in `/static/images/identity/{id}/gacksung_info.png`, `/static/images/formation/{star}Star4UptieFrame.png`, `/static/images/formation/{star}StarSinnerBG.png`, and `/static/images/sinners/{sinner}.png`. The placeholders can be found in the merged json file. See `DeckIDList.png` for the reference of the correct layout.
- List the identities from the left, but the full list have to be in middle.
- Set gaps so that users do not mis-click/touch.
- Clicking each identity leads to the details page, `/identity/{id}`.

## Scope
- `DeckIDList.png`
- `/frontend/src/components/`

## Target Code Areas
- `/frontend/src/components/`