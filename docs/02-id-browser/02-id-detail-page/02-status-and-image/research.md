# Research Document: Status and Image Section

## Overview of Codebase

- Current IdentityDetailPage uses mock data and placeholder layout with 2-column grid (left: header/status, right: skills/passives)
- Identity data in `/static/data/identity/{id}.json`: grade, HP, speed, defense, resist[3], stagger[2], traits[], skills, passives
- Resistance values are decimals: <0.75 (Ineff), [0.75,1) (Endure), 1.0 (Normal), (1,1.5] (Weak), (1.5,2] (Fatal)
- Stagger values are decimal percentages (0.3 = 30%) requiring HP calculation: `Math.floor(HP * stagger)`
- i18n structure: identity names in `/static/i18n/{lang}/identity/{id}.json`, traits in `traitMatch.json`
- Trait data uses bracket notation `[HCorp.]` requiring `parseBracketNotation()` utility
- 1-star identities have only `normal.webp` images, higher grades have both `gacksung.webp` and `normal.webp`
- Image paths: rarity icons at `/static/images/UI/identity/rarity{1|2|3}.webp`
- Status icons: hp.png, speed.png, defense.png, slash.png, pierce.png, blunt.png in `/static/images/UI/identity/`
- Button UI assets: button.png, buttonSwapImage.webp, buttonExpandImage.webp in `/static/images/UI/common/`
- Identity images: `/static/images/identity/{id}/{gacksung|normal}.webp`
- React 19 with TanStack Router (useParams for ID), TanStack Query for data fetching
- i18n via react-i18next: `useTranslation()` hook, language detection, fallback to EN
- Tailwind CSS 4.x with custom theme, CSS variables for light/dark mode, OKLCH color space
- Existing utilities in `identityUtils.ts`: parseBracketNotation, image path generators

## Codebase Structure

- Main file: `/frontend/src/routes/IdentityDetailPage.tsx` - current implementation with 4-quadrant layout
- Components directory: `/frontend/src/components/identity/` contains IdentityCard, IdentityList, filters
- Utility functions: `/frontend/src/lib/identityUtils.ts` for bracket parsing and image paths
- Custom hooks: `/frontend/src/hooks/useIdentityData.ts` for data loading with i18n integration
- Shadcn UI components: `/frontend/src/components/ui/button.tsx` with variants (default, outline, ghost) and sizes (icon-sm, icon, icon-lg)
- Static data: `/static/data/identity/`, `/static/i18n/{EN|JP|KR|CN}/`, `/static/images/`
- Path aliases: `@/` → src, `@static/` → ../static (TypeScript config)
- Component naming: PascalCase for all files, hooks use `use{Capability}.ts` pattern
- Layout patterns: container + card structure, responsive grids, panel styling with border/rounded/padding
- State management: useState for local UI, useMemo for derived data, TanStack Query for API
- Existing calculation functions: formatResistance, renderCoinIcons, renderAttackWeight in IdentityDetailPage

## Gotchas and Pitfalls

- 1-star identities lack gacksung variant - MUST implement fallback and disable swap button (check grade === 1)
- Resistance color mapping requires exact ranges: use conditionals for (1.5,2], (1,1.5], 1.0, [0.75,1), (0,0.75)
- Stagger threshold calculation must truncate: `Math.floor(HP * stagger)` NOT round
- Trait bracket notation `[HCorp.]` must be converted via `parseBracketNotation()` before display
- i18n files change with language - use `useMemo` with `i18n.language` dependency
- Image paths use `/images/` prefix (Vite mapping from `/static/images/`)
- Current formatResistance returns string, needs refactor for category + color object
- Mock data path is temporary - will switch to real data loading hook
- WebP format for identity images, PNG for UI elements/icons
- Expand button opens new tab: use `window.open(imagePath, '_blank')`
- Grid layouts responsive: mobile-first with lg: breakpoint for 2-column
- CSS class merging via `cn()` utility (clsx + twMerge) to prevent conflicts
- TypeScript strict mode - all props need proper interfaces
- Resistance values are 3-element array [slash, pierce, blunt] - order matters
- Skills can have multiple entries per slot (skill3 array) - handle properly
- Defense value has string format "+5" not number - display as-is
- Sin colors defined in requirements but not in codebase - need to add constants

