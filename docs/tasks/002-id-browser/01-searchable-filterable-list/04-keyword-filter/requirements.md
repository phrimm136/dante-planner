# Task: Keyword Filter

## Description
Replace the keyword filter placeholder to a working component. The keywords for filter are burn, bleed, tremor, rupture, sinking, poise, and charge. Each of those have the corresponding icon in `/static/images/statusEffect/`. Its mechanism is same as the sinner filter, except the filtering is conducted with `keyword` field in the merged json.

## Research
- Generalize the sinner filter to be used for the keyword filter

## Scope
- `/frontend/src/components/identity/IdentitySinnerFilter.tsx`
- `/frontend/src/components/identity/IdentityKeywordFilter.tsx`
- `/static/images/statusEffect/`

## Target Code Area
- `/frontend/src/components/identity/IdentitySinnerFilter.tsx`
- `/frontend/src/components/identity/IdentityKeywordFilter.tsx`
- `/frontend/src/routes/IdentityPage.tsx`

## Testing Guidelines