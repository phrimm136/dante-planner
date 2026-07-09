# Task: Refine EGO Gift Card

## Description
Refine the existing EGO gift card to following one:
- Entire card still links to the corresponding detail page
- The center has 128x128px ego gift icon in `/static/images/egoGift/` - currently no icons; create paths and let them broken
- The upper-left is gift grade icon in `/static/images/icon/egoGift/` - currently no icons; create paths and let them broken
- The lower-right is gift keyword icon in `/static/images/icon/statusEffect/`
- the upper-right is gift enhancement icon in `/static/images/icon/egoGift/` - currently no icons; create paths and let them broken
  - Note that not-enhanced gift have not to show the icons; No enhancements for the cards in the list, but it can be shown in some other places

## Research
- grade icon name is `grade{grade}.webp`
- enhancement icon name is `enhancement{level}.webp`
- The fallback area have to follow the expected area  128x128px

## Scope
- `/frontend/src/components/egoGift/EGOGiftCard.tsx`

## Target Code Area
- `/frontend/src/components/egoGift/EGOGiftCard.tsx`

## Testing Guidelines