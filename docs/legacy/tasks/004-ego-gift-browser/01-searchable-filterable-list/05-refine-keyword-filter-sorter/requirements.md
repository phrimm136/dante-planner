# Task: Refine Keyword Filter & Sorter

## Describe
The filtering and sorting now works by reading `category` field in `EGOGiftSpecList.json`.

## Research
- Add common button (text) to the filter
- Change the filtering and sorting mechanism to use the `category` field
- The keyword icon on the lower-right of the gift card now shows only one from `category`
- Consolidate `/frontend/src/components/gift/` to `/frontend/src/components/egoGift/`

## Scope
- `/static/data/EGOGiftSpecList.json`
- `/frontend/src/components/egoGift/`
- `/frontend/src/components/gift/`
- `/frontend/src/routes/EGOGiftPage.tsx`

## Target Code Area
- `/frontend/src/components/egoGift/`
- `/frontend/src/components/gift/`
- `/frontend/src/routes/EGOGiftPage.tsx`

## Testing Guidelines
