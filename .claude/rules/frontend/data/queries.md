---
paths:
  - "frontend/src/hooks/**/*.ts"
  - "frontend/src/hooks/**/*.tsx"
---

# Data Query Patterns

## Mandatory Rules

- **Prefer `useSuspenseQueries`** - Parallel loading, SSR-ready
- **Paired hooks for granular Suspense** - Spec hook (no language) + i18n hook (language in key)
- **i18n deferred hooks** - Add `placeholderData: keepPreviousData` to prevent flash

## Forbidden Patterns

| Forbidden | Use Instead |
|-----------|-------------|
| `useQuery` with `enabled` chain | `useSuspenseQueries` (parallel) |
| Manual `isPending` checks | Suspense boundary |
| Components with hooks in Suspense fallback | Empty values (`""`, `[]`, `{}`) |

## Template (Parallel Queries)

```typescript
import { useSuspenseQueries, keepPreviousData } from '@tanstack/react-query'

export function useEntityData(id: string) {
  const { language } = useLanguage()

  const [dataQuery, i18nQuery] = useSuspenseQueries({
    queries: [
      {
        queryKey: ['entity', id],
        queryFn: async () => {
          const res = await fetch(`/data/entity/${id}.json`)
          return validateData(await res.json(), EntityDataSchema)
        },
        staleTime: 5 * 60 * 1000,
      },
      {
        queryKey: ['entity', id, 'i18n', language],
        queryFn: async () => {
          const res = await fetch(`/i18n/${language}/entity/${id}.json`)
          return validateData(await res.json(), EntityI18nSchema)
        },
        staleTime: 7 * 24 * 60 * 60 * 1000,
        placeholderData: keepPreviousData,  // Prevent flash on language change
      },
    ],
  })

  return { data: dataQuery.data, i18n: i18nQuery.data }
}
```

## Query Key Factory

```typescript
export const entityQueryKeys = {
  all: (type: string) => [type] as const,
  detail: (type: string, id: string) => [type, id] as const,
  i18n: (type: string, id: string, lang: string) => [type, id, 'i18n', lang] as const,
}
```

**Reference:** `useIdentityData.ts`, `useEGOGiftData.ts`, `useIdentityDetailData.ts`
