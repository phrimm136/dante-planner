# Task: Copy UI From the Identity and Refactor Components

## Description
Import the entire UI from the identity detail page. Drop the sanity section that the EGO does not have. Instead of skill1/2/3/def, there are awakening/corrosion skills. The skill image is from the `awaken_profile.webp` and `erosion_profile.webp` in `/static/images/EGO/{id}/`. Note that some egos do not have corrosion skills. Add sanity cost after the attack weight. Instead of status, attack type resistance, stagger, and traits, there are sin resistance and sin cost where its values are described in `/static/data/EGO/{id}.json`. There are no support passives; remove the passive category indicator and just show the one passive. Instead of the rarity/star, show the ego rank in `/static/images/UI/EGO/`. Since the EGO has no alternative images, drop the image toggle button.

## Research
- How to get the UI of the identity detail and extract the common parts

## Scope
- `/static/images/UI/EGO/`
- `/static/images/EGO/{id}/` (20101 for implementation example)
- `/static/data/EGO/{id}.json` (20101 for implementation example)
- `/frontend/src/routes/IdentityDetailPage.tsx`
- `/frontend/src/components/common/`
- `/frontend/src/components/identity/`

## Target Code Area
- `/frontend/src/routes/EGODetailPage.tsx`
- `/frontend/src/components/common/`
- `/frontend/src/components/ego/`

## Testing Guidelines