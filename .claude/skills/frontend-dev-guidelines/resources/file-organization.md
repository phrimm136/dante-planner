# File Organization

Directory structure and file organization for LimbusPlanner frontend.

---

## Directory Structure

```
frontend/src/
├── components/          # UI components organized by feature
│   ├── common/         # Shared components (LoadingState, ErrorState, etc.)
│   ├── identity/       # Identity-related components
│   ├── ego/            # EGO-related components
│   ├── egoGift/        # EGO Gift components
│   ├── startGift/      # Start Gift components
│   ├── deckBuilder/    # Deck builder components
│   └── ui/             # shadcn/ui primitives
│
├── hooks/              # Custom React hooks
│   ├── useEntityListData.ts
│   ├── useEntityDetailData.ts
│   ├── useStartBuffData.ts
│   └── ...
│
├── types/              # TypeScript type definitions
│   ├── IdentityTypes.ts
│   ├── EGOTypes.ts
│   ├── EGOGiftTypes.ts
│   └── ...
│
├── schemas/            # Zod validation schemas
│   ├── IdentitySchemas.ts
│   ├── EGOSchemas.ts
│   ├── EGOGiftSchemas.ts
│   ├── index.ts        # Re-exports all schemas
│   └── ...
│
├── routes/             # TanStack Router pages
│   ├── __root.tsx
│   ├── HomePage.tsx
│   ├── IdentityPage.tsx
│   ├── IdentityDetailPage.tsx
│   └── ...
│
├── lib/                # Utility functions
│   ├── utils.ts        # cn() and general utilities
│   ├── assetPaths.ts   # Image/asset path helpers
│   ├── validation.ts   # Zod validation utilities
│   ├── constants.ts    # App constants
│   └── i18n.ts         # Internationalization setup
│
├── contexts/           # React Context providers
│   ├── ThemeContext.tsx
│   └── AuthContext.tsx
│
├── styles/             # Global CSS
│   └── globals.css
│
└── main.tsx            # App entry point
```

---

## Static Data Structure

```
static/
├── data/                       # Game data (specs)
│   ├── identitySpecList.json   # All identity specs
│   ├── identity/               # Individual identity data
│   │   ├── 10101.json
│   │   └── ...
│   ├── egoSpecList.json
│   ├── ego/
│   ├── egoGiftSpecList.json
│   └── egoGift/
│
├── i18n/                       # Translations
│   ├── EN/
│   │   ├── identityNameList.json
│   │   ├── identity/
│   │   │   ├── 10101.json
│   │   │   └── ...
│   │   └── common.json
│   ├── JP/
│   ├── KR/
│   └── CN/
│
└── config/
    └── queryConfig.json        # Query staleTime config
```

---

## Import Aliases

| Alias | Resolves To | Example |
|-------|-------------|---------|
| `@/` | `src/` | `import { cn } from '@/lib/utils'` |
| `@static/` | `static/` | `import data from '@static/data/identitySpecList.json'` |

Defined in: `vite.config.ts`

---

## File Naming Conventions

### Components

**Pattern**: PascalCase with `.tsx` extension

```
IdentityCard.tsx
EGOGiftList.tsx
DetailPageLayout.tsx
```

### Hooks

**Pattern**: camelCase with `use` prefix, `.ts` extension

```
useEntityListData.ts
useStartBuffData.ts
useColorCodes.ts
```

### Types

**Pattern**: PascalCase with `Types` suffix, `.ts` extension

```
IdentityTypes.ts
EGOGiftTypes.ts
StartGiftTypes.ts
```

### Schemas

**Pattern**: PascalCase with `Schemas` suffix, `.ts` extension

```
IdentitySchemas.ts
EGOGiftSchemas.ts
ColorCodeSchemas.ts
```

### Utilities

**Pattern**: camelCase, `.ts` extension

```
assetPaths.ts
validation.ts
constants.ts
```

---

## Component Organization

### Feature-Based Grouping

Components are organized by feature/domain:

```
components/
├── identity/
│   ├── IdentityCard.tsx
│   ├── IdentityList.tsx
│   └── IdentityFilters.tsx
│
├── egoGift/
│   ├── EGOGiftCard.tsx
│   ├── EGOGiftList.tsx
│   └── EgoGiftMiniCard.tsx
│
├── common/
│   ├── LoadingState.tsx
│   ├── ErrorState.tsx
│   ├── DetailPageLayout.tsx
│   └── SearchBar.tsx
│
└── ui/                 # shadcn/ui components
    ├── button.tsx
    ├── card.tsx
    ├── dialog.tsx
    └── ...
```

### When to Create Subdirectory

| Create subdirectory when | Keep flat when |
|-------------------------|----------------|
| 3+ related components | 1-2 components |
| Complex feature domain | Simple utility components |
| Components share state/logic | Independent components |

---

## Types and Schemas Organization

### Types Pattern

```typescript
// types/IdentityTypes.ts

// List item (for grids/lists)
export interface Identity {
  id: string
  name: string
  rank: number
  unitKeywordList: string[]
}

// Detail data (full entity)
export interface IdentityData {
  sinner: string
  grade: number
  HP: number
  skills: Skill[]
  // ...
}

// i18n data
export interface IdentityI18n {
  name: string
  skills: SkillsI18nData
  passive: PassiveI18n[]
}
```

### Schemas Pattern

```typescript
// schemas/IdentitySchemas.ts
import { z } from 'zod'

export const IdentityDataSchema = z.object({
  sinner: z.string(),
  grade: z.number(),
  HP: z.number(),
  // ...
}).strict()

export const IdentityI18nSchema = z.object({
  name: z.string(),
  skills: SkillsI18nDataSchema,
  // ...
}).strict()

// Re-export from index.ts
export * from './IdentitySchemas'
```

---

## Hooks Organization

### Naming Convention

| Hook Type | Naming | Example |
|-----------|--------|---------|
| Data fetching | `use{Entity}Data` | `useEntityListData`, `useEntityDetailData` |
| UI state | `use{Feature}` | `useStartGiftPools` |
| Derived data | `use{Computed}` | `useColorCodes`, `useEgoGiftDescription` |

### Hook Structure

```typescript
// hooks/useEntityListData.ts

// Query key factory
export const entityListQueryKeys = {
  all: (type: EntityType) => [type, 'list'] as const,
  spec: (type: EntityType) => [type, 'list', 'spec'] as const,
  i18n: (type: EntityType, language: string) =>
    [type, 'list', 'i18n', language] as const,
}

// Hook implementation
export function useEntityListData<TListItem>(type: EntityType) {
  // ...
}
```

---

## Route Pages Organization

### File Naming

```
routes/
├── __root.tsx              # Root layout
├── HomePage.tsx            # Home page
├── IdentityPage.tsx        # List page
├── IdentityDetailPage.tsx  # Detail page
├── EGOPage.tsx
├── EGODetailPage.tsx
├── EGOGiftPage.tsx
├── EGOGiftDetailPage.tsx
└── PlannerMDNewPage.tsx
```

### Page Pattern

```typescript
// routes/IdentityDetailPage.tsx
import { Suspense } from 'react'
import { useParams } from '@tanstack/react-router'
import { LoadingState } from '@/components/common/LoadingState'
import { ErrorState } from '@/components/common/ErrorState'

export function IdentityDetailPage() {
  const { id } = useParams({ strict: false })

  if (!id) return <ErrorState message="No ID provided" />

  return (
    <Suspense fallback={<LoadingState />}>
      <IdentityDetailContent id={id} />
    </Suspense>
  )
}
```

---

## Import Order

```typescript
// 1. React
import { useState, Suspense } from 'react'

// 2. Third-party libraries
import { useSuspenseQuery } from '@tanstack/react-query'
import { useParams, Link } from '@tanstack/react-router'
import { useTranslation } from 'react-i18next'

// 3. shadcn/ui components
import { Card, CardContent } from '@/components/ui/card'
import { Button } from '@/components/ui/button'

// 4. Project utilities
import { cn } from '@/lib/utils'
import { getIdentityImagePath } from '@/lib/assetPaths'
import { validateData } from '@/lib/validation'

// 5. Project components
import { LoadingState } from '@/components/common/LoadingState'
import { IdentityCard } from '@/components/identity/IdentityCard'

// 6. Types (type-only imports)
import type { Identity } from '@/types/IdentityTypes'

// 7. Schemas
import { IdentityDataSchema } from '@/schemas/IdentitySchemas'

// 8. Relative imports
import { SubComponent } from './SubComponent'
```

---

## Summary

| Category | Location | Naming |
|----------|----------|--------|
| Components | `components/{feature}/` | `PascalCase.tsx` |
| Hooks | `hooks/` | `use{Name}.ts` |
| Types | `types/` | `{Entity}Types.ts` |
| Schemas | `schemas/` | `{Entity}Schemas.ts` |
| Utilities | `lib/` | `camelCase.ts` |
| Pages | `routes/` | `{Name}Page.tsx` |
| Static data | `static/data/` | JSON files |
| Translations | `static/i18n/{lang}/` | JSON files |

**See Also:**
- [component-patterns.md](component-patterns.md) - Component structure
- [schemas-and-validation.md](schemas-and-validation.md) - Zod patterns
- [data-fetching.md](data-fetching.md) - Query patterns
