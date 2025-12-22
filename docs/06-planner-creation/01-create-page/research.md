# Research: Create Planner Page

## Overview of Codebase

- Pages use standard layout: container → title (h1) → bg-background card → filters → content
- TanStack Router with code-based routing in `frontend/src/lib/router.tsx`
- TanStack Query for data fetching via `useEntityListData` and `useSuspenseQuery`
- shadcn/ui Button with variants (default, outline, ghost) and sizes (sm, lg, icon)
- i18n via `useTranslation()` hook; keys in `static/i18n/{lang}/common.json`
- Loading/error states use `LoadingState` and `ErrorState` components
- IconFilter component exists for multi-select with icons (used by keyword filters)
- STATUS_EFFECTS: Combustion, Laceration, Vibration, Burst, Sinking, Breath, Charge
- KEYWORD_ORDER adds attack types: Slash, Penetration, Hit, None
- SINS: Wrath, Lust, Sloth, Gluttony, Gloom, Pride, Envy
- Asset path helpers: getSinIconPath(), getStatusEffectIconPath() in assetPaths.ts
- Icons in static/images/icon/{statusEffect,sin,sinners}/ directories
- Set<string> pattern used for multi-select state management
- SearchBar component has debounced text input pattern (reusable for name input)
- Responsive layouts with Tailwind (flex, gap-4, justify-between)
- Page components in `frontend/src/routes/`; default export at file bottom

## Codebase Structure

- Routes: `frontend/src/lib/router.tsx` - add createRoute() for /planner/md/new
- Page files: `frontend/src/routes/{PageName}.tsx`
- Components: `frontend/src/components/{domain}/{Component}.tsx`
- Common components: `frontend/src/components/common/` (IconFilter, SearchBar)
- UI primitives: `frontend/src/components/ui/` (button, dropdown-menu)
- Constants: `frontend/src/lib/constants.ts` (SINS, STATUS_EFFECTS, KEYWORD_ORDER)
- Asset paths: `frontend/src/lib/assetPaths.ts`
- i18n files: `static/i18n/{EN,JP,KR,CN}/common.json`
- Icons: `static/images/icon/{statusEffect,sin,sinners}/`

## Gotchas and Pitfalls

- For full keyword coverage, merge KEYWORD_ORDER + SINS (17 total keywords)
- getSinIconPath and getStatusEffectIconPath use different directories - IconFilter needs custom getIconPath
- IconFilter expects getIconPath function that handles both sin and status effect icons
- Route must be added to routeTree children array in router.tsx
- i18n keys must be added to ALL language files (EN, JP, KR, CN)
- Category (5F/10F/15F) has no constant - define locally or add to constants.ts
- Avoid early return loading patterns - use proper Suspense boundaries
- Check if icons exist for new keywords (Burst, Breath, Penetration, Hit, None)
- SearchBar has built-in debounce - for name input, use plain input without debounce
