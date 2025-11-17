# Task: Status and Image

## Description
Refine the upper-left section in id detail page. The grade and id name have to be in the same line. The grade is described as an icon, in `/static/images/UI/identity/rarity{rarity}.webp`. The name is in `name` `/static/i18n/{language}/identity/{id}.json`. The image change and expand icon is in `/static/images/UI/common/{buttonSwapImage|buttonExpandImage}.webp`, with a background button image in `/static/images/UI/common/button.webp`. Clicking the swap button swaps the identity image between `gacksung.webp` and `normal.webp`. The default is gacksung one, but notice that the 1 start has only nornal image. Implement fallback mechanism and disable the swap button on the 1 start identity. The expand button opens a new tab with the image file.
For the status section, set the `Status`, `Resistance`, and `Stagger` title to the middle. In `Status`, set HP, Speed, and Defense as images in `/static/images/UI/identity/{hp|speed|defense}.webp`. Enumerate hp, speed, and defense horizontally. In `Resistance`, enumerate the contents horizontally. Set slash, pierce, and blunt as images in `/static/images/UI/identity/{slash|pierce|blunt}.webp`. Each resistance content is consisted of name and the real value, in vertical order, where `Fatal` describes (1.5, 2] (red color), `Weak` for (1.0, 1.5] (peach color), `Normal` for 1.0 (ivory color), `Endure` for [0.75, 1) (right gray color), and `Ineff.` for (0, 0.75) (gray color). Note that the name differs for the language setting. For `Stagger`, enumerate the contents horizontally. each content is percentage value and the real HP value (calculated from the max HP and the stagger value, truncating decimal point), in vertical order.
Traits are from `traits` in `/static/data/identity/{id}.json`, converting the value in the language-specific text.

## Research
- Refactor the `/frontend/src/routes/IdentityDetailPage.tsx`

## Scope
`/static/images/UI/common/`
`/static/data/identity/10114.json` for the real example
`/static/i18n/EN/identity/10114.json` for the real example
`/static/i18n/EN/traitMatch.json`
`/frontend/src/components/identity/`
`/frontend/src/routes/IdentityDetailPage.tsx/`

## Target Code Area
- `/frontend/src/components/`
- `/frontend/src/routes/IdentityDetailPage.tsx`

## Testing Guidelines