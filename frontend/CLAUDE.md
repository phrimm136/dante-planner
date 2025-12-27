# FRONTEND DEVELOPMENT GUIDELINES

**Tech Stack:** React 19 + TypeScript + TanStack Query + Zod + shadcn/ui + Tailwind CSS + React Compiler

**CRITICAL: Use Skill tool `frontend-dev-guidelines` BEFORE writing any frontend code.**

---

## Frontend Resource Map (MUST READ before coding)

**Before writing ANY frontend code, read the relevant resource:**

| Task Type | MUST Read | Purpose |
|-----------|-----------|---------|
| **Data Hook** | `data-fetching.md`, `schemas-and-validation.md` | useSuspenseQueries patterns, Zod validation |
| **Component** | `component-patterns.md`, `styling-guide.md` | React Compiler rules, shadcn/ui usage |
| **Type/Schema** | `schemas-and-validation.md` | JSON → Types → Schemas pipeline |
| **Page/Route** | `routing-guide.md`, `component-patterns.md` | TanStack Router patterns |
| **Styling** | `styling-guide.md` | Tailwind + shadcn, color constants |
| **Loading/Error** | `loading-and-error-states.md` | Suspense boundaries, error handling |

**Location**: `.claude/skills/frontend-dev-guidelines/resources/`

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
   - No manual `memo`/`useCallback` unless proven necessary

**Apply These Patterns:**
- **Container/Presenter**: Separate data hooks from presentational components
- **Custom Hook**: Encapsulate reusable logic (data fetching, state management)
- **Render Props / Children as Function**: Flexible component composition
- **Provider Pattern**: Global state via Context (only for theme, auth, i18n - NOT business state)

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
| Manual `memo`, `useCallback` | React Compiler handles it | Remove manual optimization |
| Missing Error Boundary | Unhandled errors crash app | Wrap routes in `<ErrorBoundary>` |
| Unsanitized user input in HTML | XSS vulnerability | Use DOMPurify or escape text |
| `localStorage`/`sessionStorage` direct use | Breaks SSR, not available on server | Use cookie-based state or check `typeof window !== 'undefined'` |

---

## Constants Workflow (MANDATORY)

Before using ANY hardcoded value (colors, numbers, strings):

1. Read `frontend/src/lib/constants.ts` to check if constant exists
2. If YES: Import and use the constant
3. If NO: Add to `constants.ts` FIRST, then import and use
4. State "**Constants Check:** Using [CONSTANT_NAME] from constants.ts" or "**Constants Check:** Added [NEW_CONSTANT] to constants.ts"

---

## Import Order (Enforced)

1. React core
2. TanStack (Query, Router)
3. Third-party libraries
4. shadcn/ui components
5. Project utilities (@/lib)
6. Project types & schemas (use `import type` for type-only imports)
7. Project components (@/components)

**See `frontend-dev-guidelines` skill for detailed examples.**

---

## Quick Reference: Where to Find Patterns

| Need Pattern For | Check These Files |
|------------------|-------------------|
| Data fetching hook | `hooks/useEntityListData.ts`, `hooks/useEntityDetailData.ts` |
| Card component | `components/identity/IdentityCard.tsx`, `components/egoGift/EGOGiftCard.tsx` |
| Detail page | `routes/IdentityDetailPage.tsx`, `routes/EGODetailPage.tsx` |
| List page | `routes/IdentityPage.tsx`, `routes/EGOGiftPage.tsx` |
| Type definitions | `types/IdentityTypes.ts`, `types/EGOGiftTypes.ts` |
| Zod schemas | `schemas/IdentitySchemas.ts`, `schemas/EGOGiftSchemas.ts` |
| Asset paths | `lib/assetPaths.ts` (ALL helper functions) |
| Constants | `lib/constants.ts` (MAX_LEVEL, SINNERS, AFFINITIES, etc.) |
| Styling utilities | `lib/utils.ts` (cn function) |
| Validation utilities | `lib/validation.ts` (validateData) |

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

- **CRITICAL: Load skill with Skill tool FIRST** (frontend-dev-guidelines)
- **CRITICAL: Read relevant resource docs BEFORE writing code**
- **CRITICAL: State intent BEFORE every Write/Edit - explain WHAT, WHY, and HOW**
- **CRITICAL: Check existing patterns BEFORE writing new code**
- **CRITICAL: Review code IMMEDIATELY after writing - NEVER batch reviews**
- **CRITICAL: Verify SKILL COMPLIANCE in every review (first item)**
- **CRITICAL: Check FORBIDDEN PATTERNS in every review (second item)**
- **CRITICAL: Use existing constants/colors - NEVER hardcode values**
- **CRITICAL: Wrap routes in ErrorBoundary - NEVER let errors crash the app**
- **CRITICAL: Every useSuspenseQuery needs Suspense ancestor - NEVER forget Suspense**
