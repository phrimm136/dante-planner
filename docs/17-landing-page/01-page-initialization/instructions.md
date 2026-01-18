# Task: Home Page Implementation

## Description

Create a home page (`/`) for LimbusPlanner that serves as the main landing and navigation hub. The page combines quick access to plan creation with content discovery (recent releases and community plans).

### Layout Structure

**Top Section:**
- Punchy tagline on the left (e.g., "Build your perfect MD deck")
- Single "Create Plan" CTA button on the right
- Compact, minimal vertical space

**Two-Column Lower Section (side-by-side on desktop):**

**Left Column - Recently Released:**
- Section header with "Recently Released" title
- Top-right corner: separate [Identity →] and [E.G.O →] browse links
- Content: Mixed Identity and EGO cards grouped by release date
- Cards use uniform square thumbnails for clean grid alignment
- Date headers separate groups (e.g., "Jan 9, 2025", "Dec 26, 2024")
- Limit: 2-3 most recent date groups, approximately 16 items max
- Card click navigates to detail page (`/identity/$id` or `/ego/$id`)

**Right Column - Community Plans:**
- Section header with "Community Plans" title
- Top-right corner: [Browse All →] link to Gesellschaft page
- Tab switcher: [Latest] | [Recommended]
- Content: 3-5 PlannerCards based on selected tab
- Latest = newest published plans
- Recommended = highest voted plans

### Responsive Behavior
- Desktop (≥1024px): Side-by-side columns, ~50/50 split
- Tablet (768-1023px): Side-by-side with narrower cards
- Mobile (<768px): Stack vertically - Recently Released above Community Plans

### Visual Design
- Follow existing shadcn/ui patterns
- Use PlannerSection or similar container styling for each column
- Square entity cards for uniform grid in left column
- Existing PlannerCard component for right column

## Research

- Existing card components: `IdentityCard.tsx`, `EGOCard.tsx`, `PlannerCard.tsx`
- Square thumbnail asset paths for Identity/EGO
- Data hooks: `useIdentityListSpec()`, `useEGOListSpec()`
- Community data: `useMDGesellschaftData()` hook patterns
- Sorting utilities: `lib/entitySort.ts` (sortByReleaseDate, sortEGOByDate)
- Tab component from shadcn/ui
- Responsive grid patterns in existing list pages

## Scope

Files/folders to READ for context:
- `frontend/src/routes/IdentityPage.tsx` - list page pattern
- `frontend/src/routes/PlannerMDGesellschaftPage.tsx` - community plans pattern
- `frontend/src/components/identity/IdentityCard.tsx` - card component
- `frontend/src/components/ego/EGOCard.tsx` - card component
- `frontend/src/components/plannerList/PlannerCard.tsx` - planner card
- `frontend/src/hooks/useIdentityListData.ts` - identity data hook
- `frontend/src/hooks/useEGOListData.ts` - EGO data hook
- `frontend/src/hooks/useMDGesellschaftData.ts` - community plans hook
- `frontend/src/lib/entitySort.ts` - sorting utilities
- `frontend/src/lib/constants.ts` - card grid constants
- `frontend/src/lib/router.tsx` - routing configuration
- `static/i18n/EN/common.json` - i18n patterns

## Target Code Area

Files/folders to CREATE or MODIFY:
- `frontend/src/routes/HomePage.tsx` - main page component (new or modify existing)
- `frontend/src/hooks/useHomePageData.ts` - composite data hook (new)
- `frontend/src/components/home/RecentlyReleasedSection.tsx` - left column (new)
- `frontend/src/components/home/CommunityPlansSection.tsx` - right column (new)
- `frontend/src/components/home/HomeEntityCard.tsx` - square card variant (new, if needed)
- `frontend/src/lib/router.tsx` - ensure `/` route exists
- `static/i18n/{lang}/common.json` - add home page strings

## System Context (Senior Thinking)

- **Feature domain**: New top-level page, combines Identity Browser + EGO Browser + Planner List patterns
- **Core files in related domains**:
  - Identity: `routes/IdentityPage.tsx`, `hooks/useIdentityListData.ts`
  - EGO: `routes/EGOPage.tsx`, `hooks/useEGOListData.ts`
  - Planner List: `routes/PlannerMDGesellschaftPage.tsx`, `hooks/useMDGesellschaftData.ts`
- **Cross-cutting concerns**:
  - i18n: Page title, section headers, tagline need translations
  - Routing: New or updated `/` route
  - Data fetching: Composite of existing hooks
  - Theme: Uses existing theme context
  - Suspense: Need boundaries for each data section

## Impact Analysis

- **Files being modified**:
  - `lib/router.tsx` (High impact - all navigation) - minimal change, just route definition
  - `common.json` (Medium impact - i18n) - additive only
- **What depends on these files**:
  - Router: All page navigation
  - i18n: All translated text display
- **Potential ripple effects**: Minimal - mostly new files
- **High-impact files to watch**: `lib/router.tsx` - keep changes minimal

## Risk Assessment

- **Edge cases not yet defined**:
  - Empty community plans (no published plans exist)
  - No recent releases (unlikely but possible during content freeze)
  - API failure for community plans
- **Performance concerns**:
  - Fetching full identity + EGO lists just to slice - consider if spec lists are cached
  - Multiple Suspense boundaries may cause waterfall
- **Backward compatibility**: N/A - new page
- **Security considerations**: N/A - public page, read-only data

## Testing Guidelines

### Manual UI Testing

1. Navigate to `/` (home page)
2. Verify tagline and "Create Plan" button are visible at top
3. Click "Create Plan" button
4. Verify navigation to `/planner/md/new`
5. Navigate back to `/`
6. Verify "Recently Released" section shows mixed Identity/EGO cards
7. Verify cards are grouped by date with date headers
8. Verify [Identity →] link navigates to `/identity`
9. Verify [E.G.O →] link navigates to `/ego`
10. Click an Identity card in Recently Released
11. Verify navigation to `/identity/{id}`
12. Navigate back to `/`
13. Click an EGO card in Recently Released
14. Verify navigation to `/ego/{id}`
15. Navigate back to `/`
16. Verify "Community Plans" section shows plan cards
17. Verify [Latest] tab is active by default
18. Click [Recommended] tab
19. Verify plan cards update to show recommended plans
20. Click [Browse All →] link
21. Verify navigation to `/planner/md/gesellschaft`
22. Navigate back to `/`
23. Resize browser to tablet width (~800px)
24. Verify two columns still display side-by-side
25. Resize browser to mobile width (~375px)
26. Verify sections stack vertically (Recently Released above Community)

### Automated Functional Verification

- [ ] Route `/` renders HomePage component
- [ ] Tagline displays translated text from i18n
- [ ] Create Plan button links to `/planner/md/new`
- [ ] Recently Released shows Identity and EGO cards mixed
- [ ] Cards are sorted by updateDate descending
- [ ] Cards are grouped by date with visible headers
- [ ] Identity cards link to `/identity/$id`
- [ ] EGO cards link to `/ego/$id`
- [ ] Community Plans section has tab switcher
- [ ] Latest tab fetches published plans sorted by newest
- [ ] Recommended tab fetches recommended plans
- [ ] Tab state changes content without page reload
- [ ] Browse All link navigates to Gesellschaft page
- [ ] Responsive layout: side-by-side on desktop, stacked on mobile

### Edge Cases

- [ ] No community plans: Shows empty state message (e.g., "Be the first to share a plan")
- [ ] API error for community plans: Shows error state, doesn't crash page
- [ ] Language switch: All text updates, card names update (deferred pattern)
- [ ] Very long plan titles: Truncated appropriately in PlannerCard
- [ ] Loading state: Each section shows skeleton/spinner independently

### Integration Points

- [ ] Suspense boundaries: Each section loads independently
- [ ] Query cache: Uses existing query keys for identity/EGO data
- [ ] Theme: Respects dark/light mode
- [ ] i18n: All strings translated in all supported languages (EN, KR, JP, CN)
