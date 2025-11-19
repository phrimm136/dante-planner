# Task: useEntityListData

## Description
Create a {spec|i18n} list data fetch function for id, ego, and ego-gift, similar to `useEntityDetailData`, and rewrite the list page functions to use them.

## Research
- See the pattern of `useEntityDetailData`.
- The data fetch pattern between page and its list component have to be consistent among identity/ego/egoGift.

## Scope
- `/frontend/src/hooks/useEntityDetailData.ts`

## Target Code Area
- `/frontend/src/hooks/`
- `/frontend/src/components/identity/IdentityList.tsx`
- `/frontend/src/components/ego/EGOList.tsx`
- `/frontend/src/components/egoGift/EGOGiftList.tsx`
- `/frontend/src/routes/EGOGiftPage.tsx`