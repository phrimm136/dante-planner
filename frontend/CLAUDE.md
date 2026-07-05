# FRONTEND DEVELOPMENT GUIDELINES

**Tech Stack:** React 19 + TypeScript + TanStack Query + Zustand + Zod + shadcn/ui + Tailwind CSS + React Compiler

**Package Manager:** Yarn

---

## Core Principles (Priority Order)

**Follow these principles in order of importance:**

1. **Single Responsibility Principle (SRP)**
   - One component = One responsibility
   - One hook = One data source or logic concern
   - Example: `useIdentityData` (data only), `IdentityCard` (display only)

2. **Composition over Inheritance**
   - React uses composition, not class extension
   - Build complex UIs from simple components
   - Example: `<Card><CardHeader /><CardContent /></Card>`

3. **DRY (Don't Repeat Yourself)**
   - Repeated logic → Extract to Custom Hook
   - Repeated UI → Extract to Component
   - Repeated constants → Move to `constants.ts`

4. **Separation of Concerns**
   - Data logic (Hook) ≠ UI logic (Component)
   - Example: `useEntityListData` (fetch/validate) + `EntityPage` (render)

5. **KISS (Keep It Simple, Stupid)**
   - React Compiler optimizes automatically
   - Simple code > Premature optimization
   - Manual `memo`/`useCallback`/`useMemo` ONLY with: (1) explicit performance intent, (2) user approval, (3) profiling evidence showing benefit

**Apply These Patterns:**
- **Container/Presenter**: Separate data hooks from presentational components
- **Custom Hook**: Encapsulate reusable logic (data fetching, state management)
- **Render Props / Children as Function**: Flexible component composition
- **Zustand Store**: Complex shared UI state with granular selectors

---

## State Management

| State Type | Solution |
|------------|----------|
| Server data (API) | TanStack Query |
| Complex shared UI state | Zustand with selectors |
| Theme, i18n | Context (existing) |
| Local component state | `useState` |

**Zustand selectors prevent re-renders:**
```typescript
// GOOD: Only re-renders when this slice changes
const equipment = usePlannerStore((s) => s.equipment[sinner])
```

---

## FORBIDDEN PATTERNS (Blocked by Hook)

| Pattern | Why Forbidden | Use Instead |
|---------|---------------|-------------|
| `PropTypes` | Runtime, not type-safe | Zod schemas |
| `useQuery` | Not SSR-ready | `useSuspenseQuery` |
| Hardcoded hex colors (`#ff0000`) | Not themeable, not centralized | `constants.ts` colors OR Tailwind classes |
| Magic numbers (> 10) | Not maintainable | `constants.ts` |
| `const MAX_VALUE = 100` outside constants.ts | Not centralized | Add to `constants.ts` |
| Hardcoded asset paths (`"/images/foo.webp"`) | Not maintainable | Helper in `assetPaths.ts` |
| Early return with `<LoadingSpinner />` | Breaks Suspense | Use Suspense boundary |
| Manual `memo`, `useCallback`, `useMemo` without profiling | React Compiler handles it | Let React Compiler optimize first; use manual optimization ONLY with explicit intent, user approval, and measured performance benefit |
| Missing Error Boundary | Unhandled errors crash app | Wrap routes in `<ErrorBoundary>` |
| Unsanitized user input in HTML | XSS vulnerability | Use DOMPurify or escape text |
| `localStorage`/`sessionStorage` direct use | Breaks SSR, not available on server | Use cookie-based state or check `typeof window !== 'undefined'` |

---

## Constants Workflow (MANDATORY)

Before using ANY hardcoded value (colors, numbers, strings):

1. Check the right constants module: **game vocabulary** (sinners, affinities, status effects, level caps) lives in `frontend/src/shared/gameData/constants.ts`; **app configuration** (debounce delays, stale times, UI colors, thresholds) lives in `frontend/src/lib/constants.ts`
2. If the constant exists: Import and use it
3. If NO: Add it to the correct module FIRST, then import and use
4. State "**Constants Check:** Using [CONSTANT_NAME] from [module]" or "**Constants Check:** Added [NEW_CONSTANT] to [module]"

---

## Import Order (Enforced)

1. React core
2. TanStack (Query, Router)
3. Third-party libraries
4. shadcn/ui components
5. Project utilities (@/lib)
6. Project types & schemas (use `import type` for type-only imports)
7. Project components (@/components)

---

## Quick Reference: Where to Find Patterns

**Four-layer architecture.** Every module lives in exactly one layer:
- **`pages/`** — route slices, one vertical folder per game-noun/feature (`components/hooks/lib/types/schemas` + `index.ts`)
- **`shared/`** — co-owned domain modules, each reached only through its `@/shared/<concept>` public API
- **`components/`** — domain-free React (`ui`, `layout`, `feedback`, `hooks`, vendored `tiptap-*`)
- **`lib/`** — domain-free non-React utilities

The boundary is enforced by `eslint.boundary.config.js` (deep-import ban + sink rules + frozen allowlists) — reach `shared`/`pages` modules only through their public API, never by deep import.

| Need Pattern For | Check These Files |
|------------------|-------------------|
| Data fetching hook | `pages/<slice>/hooks/use<Entity>ListData.ts`, `use<Entity>DetailData.ts` — shared factories: `lib/queryOptions.ts` (`createStaticDataQueryOptions`), `lib/queryKeys.ts` |
| Zustand store | `pages/planner/stores/usePlannerEditorStore.tsx` — SSE store: `shared/sse/stores/useSseStore.ts` |
| Card component | `pages/identity/components/IdentityCard.tsx`, `pages/egoGift/components/EGOGiftCard.tsx` |
| Detail page | `pages/identity/IdentityDetailPage.tsx`, `pages/ego/EGODetailPage.tsx` |
| List page | `pages/identity/IdentityPage.tsx`, `pages/egoGift/EGOGiftPage.tsx` |
| Type definitions | `pages/identity/types/IdentityTypes.ts`, `pages/egoGift/types/EGOGiftTypes.ts` |
| Zod schemas | `pages/identity/schemas/IdentitySchemas.ts`, `pages/egoGift/schemas/EGOGiftSchemas.ts` |
| Asset paths | `shared/assets/` (helper functions + manifest, via `@/shared/assets`) |
| Constants | game vocabulary → `shared/gameData/constants.ts` (SINNERS, AFFINITIES, MAX_LEVEL, etc.); app config → `lib/constants.ts` (debounce delays, stale times, UI colors, etc.) |
| Styling utilities | `lib/utils.ts` (cn function) |
| Validation utilities | `lib/validation.ts` (validateData) |
| Shared domain module | `shared/<concept>/` — co-owned vertical modules (skill, gameText, filter, comment, notifications, auth, sse, noteEditor, moderation, assets, gameData), each `components/hooks/lib/schemas/types` behind a `@/shared/<concept>` public API |
| Page slice (game-noun page) | `pages/<noun>/` — colocates list+detail route components, `components/hooks/lib/types/schemas`, exposes a public API via `index.ts`. Reach a slice only via `@/pages/<noun>`; cross-page reuse is allowed. |

---

## Security & Error Handling

**Security (MANDATORY):**
- **XSS Prevention**: Never use `dangerouslySetInnerHTML` without sanitization. Use DOMPurify for user-generated HTML.
- **Input Validation**: All user input MUST pass through Zod validation before use.
- **CSRF**: TanStack Query handles CSRF for same-origin requests. External APIs need CSRF tokens.

**Error Boundaries (MANDATORY):**
- Wrap ALL routes in `<ErrorBoundary>` component
- Wrap risky components (3rd party, dynamic imports) in local error boundaries
- Error boundaries catch render errors - use try/catch for async errors

**Suspense Boundaries (MANDATORY):**
- Every `useSuspenseQuery` MUST have `<Suspense>` ancestor
- Wrap route-level components in Suspense with fallback
- Never use early returns with loading spinners

---

## Critical Rules

- **CRITICAL: State intent BEFORE every Write/Edit - explain WHAT, WHY, and HOW**
- **CRITICAL: Check existing patterns BEFORE writing new code**
- **CRITICAL: Review code IMMEDIATELY after writing - NEVER batch reviews**
- **CRITICAL: Check FORBIDDEN PATTERNS in every review (first item)**
- **CRITICAL: Use existing constants/colors - NEVER hardcode values**
- **CRITICAL: Wrap routes in ErrorBoundary - NEVER let errors crash the app**
- **CRITICAL: Every useSuspenseQuery needs Suspense ancestor - NEVER forget Suspense**
