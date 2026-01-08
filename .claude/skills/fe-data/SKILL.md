---
name: fe-data
description: Data fetching with TanStack Query and Zod validation. useSuspenseQuery patterns, schemas.
---

# Frontend Data Patterns

## Rules

- **Always validate JSON** - Use Zod schemas with `.strict()`
- **Prefer `useSuspenseQueries`** - Parallel loading, SSR-ready
- **Separate spec from i18n** - Different staleTime, different keys
- **Type-safe query keys** - Use factory functions
- **Paired hooks for granular Suspense** - Spec hook (no language) + i18n hook (language in key)
- **i18n deferred hooks** - Add `placeholderData: keepPreviousData` to prevent flash on language change

## Forbidden → Use Instead

| Forbidden | Use Instead |
|-----------|-------------|
| Raw `fetch` without validation | `validateData(data, Schema)` |
| `useQuery` with `enabled` chain | `useSuspenseQueries` (parallel) |
| Manual `isPending` checks | Suspense boundary |
| Schema missing `.strict()` | Add `.strict()` to reject extras |
| Components with hooks in Suspense fallback | Empty values (empty string, `[]`, `{}`) - fallback renders outside Suspense and can trigger new suspension |

## Schema Template

```typescript
import { z } from 'zod'

export const EntityDataSchema = z.object({
  id: z.string(),
  name: z.string(),
  stats: z.object({
    hp: z.number(),
    attack: z.number(),
  }),
}).strict()

export type EntityData = z.infer<typeof EntityDataSchema>
```

## Hook Template

```typescript
import { useSuspenseQueries } from '@tanstack/react-query'

export function useEntityData(id: string) {
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

## Reference

- Pattern: `useIdentityData.ts`, `useEGOGiftData.ts`
- Granular Suspense: `useIdentityDetailData.ts` (useIdentityDetailSpec + useIdentityDetailI18n)
- Validation: `@/lib/validation`
- Why: `docs/learning/frontend-patterns.md`
