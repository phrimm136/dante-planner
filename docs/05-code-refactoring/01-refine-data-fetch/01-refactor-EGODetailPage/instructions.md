# Task: Refactor EGODetailPage

## Description
Refactor `EGODetailPage.tsx` to make it use `useEntityDetailData`. Erase the data hooks not used anymore.

## Research
- Look at the pattern that `identityDetailPage` loads the data.

## Scope
- `/frontend/src/routes/EGODetailPage.tsx`
- `/frontend/src/routes/identityDetailPage.tsx`
- `/frontend/src/hooks/useEntityDetailData.ts`

## Target Code Area
- `/frontend/src/routes/EGODetailPage.tsx`

## Testing Guidelines
- Data loading logic for ego detail page have to be the same as that of the id detail page.