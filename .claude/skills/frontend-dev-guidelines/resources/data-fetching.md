# Data Fetching Patterns

Data fetching in LimbusPlanner uses TanStack Query with static JSON files. This guide covers both current patterns and SSR-ready patterns.

---

## Data Architecture

### Static JSON Data Sources

```
static/
├── data/                    # Game data (specs)
│   ├── identitySpecList.json
│   ├── identity/{id}.json
│   ├── egoSpecList.json
│   └── egoGift/{id}.json
├── i18n/{lang}/             # Translations
│   ├── identityNameList.json
│   ├── identity/{id}.json
│   └── egoGift/{id}.json
└── config/
    └── queryConfig.json     # staleTime settings
```

---

## Two Query Patterns

This project supports two data fetching patterns:

| Pattern | When to Use |
|---------|-------------|
| `useQuery` + `enabled` | Current codebase (legacy), conditional fetching needed |
| `useSuspenseQueries` | New code, SSR-ready, parallel loading |

---

## Pattern 1: useQuery with enabled (Current, Deprecated)

Current hooks use `useQuery` with dependent queries:

```typescript
import { useQuery, queryOptions } from '@tanstack/react-query'

export function useEntityDetailData(type: EntityType, id: string | undefined) {
  const { i18n } = useTranslation()

  // First query: Load entity data
  const dataQuery = useQuery(
    createDataQueryOptions(type, id!, !!id)
  )

  // Second query: Load i18n (dependent on data success)
  const i18nQuery = useQuery(
    createI18nQueryOptions(type, id!, i18n.language, dataQuery.isSuccess)
  )

  return {
    data: dataQuery.data,
    i18n: i18nQuery.data,
    isPending: dataQuery.isPending || i18nQuery.isPending,
    isError: dataQuery.isError || i18nQuery.isError,
    error: dataQuery.error || i18nQuery.error,
  }
}
```

### queryOptions Pattern

Use `queryOptions` for type-safe, reusable query configurations:

```typescript
function createDataQueryOptions(type: EntityType, id: string, enabled: boolean) {
  const config = ENTITY_CONFIG[type]
  return queryOptions({
    queryKey: [type, id],
    queryFn: async () => {
      const response = await fetch(`/data/${config.dataPath}/${id}.json`)
      const data = await response.json()
      const schema = getDetailSchema(type)
      return validateData(data, schema, { entityType: type, dataKind: 'detail', id })
    },
    enabled,
    staleTime: config.staleTime,
  })
}
```

### Pros/Cons of useQuery Pattern

**Pros:**
- Supports conditional fetching via `enabled`
- Works with current codebase
- Familiar pattern

**Cons:**
- Creates waterfall (i18n waits for spec)
- Requires manual `isPending` handling
- Not SSR-optimized

---

## Pattern 2: useSuspenseQueries (Recommended for New Code)

For SSR-ready code, use `useSuspenseQueries` for parallel loading:

```typescript
import { useSuspenseQueries } from '@tanstack/react-query'

export function useEntityDetailData(type: EntityType, id: string) {
  const { i18n } = useTranslation()

  // Parallel loading - no waterfall!
  const [dataQuery, i18nQuery] = useSuspenseQueries({
    queries: [
      {
        queryKey: [type, id],
        queryFn: async () => {
          const response = await fetch(`/data/${type}/${id}.json`)
          const data = await response.json()
          return validateData(data, getDetailSchema(type), {
            entityType: type,
            dataKind: 'detail',
            id,
          })
        },
        staleTime: 5 * 60 * 1000,
      },
      {
        queryKey: [type, id, 'i18n', i18n.language],
        queryFn: async () => {
          const response = await fetch(`/i18n/${i18n.language}/${type}/${id}.json`)
          const data = await response.json()
          return validateData(data, getI18nSchema(type), {
            entityType: type,
            dataKind: 'i18n',
            id,
          })
        },
        staleTime: 7 * 24 * 60 * 60 * 1000, // 7 days
      },
    ],
  })

  // data is always defined (Suspense handles loading)
  return {
    data: dataQuery.data,
    i18n: i18nQuery.data,
  }
}
```

### Component Usage with Suspense

```typescript
// Page component
export function IdentityDetailPage() {
  const { id } = useParams({ strict: false })

  if (!id) return <ErrorState message="No ID provided" />

  return (
    <Suspense fallback={<LoadingState />}>
      <IdentityDetailContent id={id} />
    </Suspense>
  )
}

// Inner component - data is always defined
function IdentityDetailContent({ id }: { id: string }) {
  const { data, i18n } = useEntityDetailData('identity', id)

  return (
    <div>
      <h1>{i18n.name}</h1>
      <p>HP: {data.HP}</p>
    </div>
  )
}
```

### Pros/Cons of useSuspenseQueries Pattern

**Pros:**
- Parallel loading (no waterfall)
- SSR-ready (runs on server, streams to client)
- `data` is always defined (no undefined checks)
- Cleaner component code

**Cons:**
- Cannot use `enabled` option
- Requires Suspense boundary
- ID must be validated before calling hook

---

## Query Key Conventions

### Key Structure

```typescript
// List data
[entityType, 'list']           // e.g., ['identity', 'list']
[entityType, 'list', 'spec']   // spec list only
[entityType, 'list', 'i18n', language]  // name list

// Detail data
[entityType, id]               // e.g., ['identity', '10101']
[entityType, id, 'i18n', language]      // i18n data
```

### Query Key Factory

```typescript
export const entityQueryKeys = {
  all: (type: EntityType) => [type] as const,
  detail: (type: EntityType, id: string) => [type, id] as const,
  i18n: (type: EntityType, id: string, language: string) =>
    [type, id, 'i18n', language] as const,
}

export const entityListQueryKeys = {
  all: (type: EntityType) => [type, 'list'] as const,
  spec: (type: EntityType) => [type, 'list', 'spec'] as const,
  i18n: (type: EntityType, language: string) => [type, 'list', 'i18n', language] as const,
}
```

---

## Runtime Validation

**All JSON data MUST be validated with Zod:**

```typescript
import { validateData } from '@/lib/validation'
import { IdentityDataSchema } from '@/schemas/IdentitySchemas'

const response = await fetch(`/data/identity/${id}.json`)
const data = await response.json()
return validateData<IdentityData>(
  data,
  IdentityDataSchema,
  { entityType: 'identity', dataKind: 'detail', id }
)
```

The `validateData` function:
- Validates data against Zod schema
- Throws descriptive error on validation failure
- Returns typed, validated data

---

## staleTime Configuration

Configure staleTime based on data volatility:

```typescript
// From queryConfig.json
{
  "staleTime": {
    "identity": 300000,    // 5 minutes
    "ego": 300000,         // 5 minutes
    "egoGift": 300000      // 5 minutes
  }
}

// i18n data - rarely changes
staleTime: 7 * 24 * 60 * 60 * 1000  // 7 days
```

---

## SSR Considerations

### Hydration Safety

```typescript
// Create new QueryClient per request (prevents cache sharing between users)
const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      staleTime: 60 * 1000,  // Prevents immediate refetch on client
      gcTime: 5 * 60 * 1000,
    },
  },
})
```

### Error Boundary + Suspense

```typescript
<ErrorBoundary fallback={<ErrorState />}>
  <Suspense fallback={<LoadingState />}>
    <DataComponent />
  </Suspense>
</ErrorBoundary>
```

---

## Migration Guide: useQuery to useSuspenseQueries

### Before (waterfall)

```typescript
const specQuery = useQuery({ queryKey: [...], queryFn: ... })
const i18nQuery = useQuery({ queryKey: [...], queryFn: ..., enabled: specQuery.isSuccess })

if (specQuery.isPending || i18nQuery.isPending) return <LoadingState />
if (specQuery.isError) return <ErrorState error={specQuery.error} />
```

### After (parallel)

```typescript
// In page component
<Suspense fallback={<LoadingState />}>
  <ContentComponent id={id} />
</Suspense>

// In content component
const [specQuery, i18nQuery] = useSuspenseQueries({
  queries: [
    { queryKey: [...], queryFn: ... },
    { queryKey: [...], queryFn: ... },
  ],
})
// No loading/error checks needed - Suspense handles it
```

---

## Cache Invalidation

Use hierarchical keys for targeted cache invalidation:

```typescript
import { useQueryClient } from '@tanstack/react-query'

function useInvalidation() {
  const queryClient = useQueryClient()

  return {
    // Invalidate all identities
    invalidateAllIdentities: () =>
      queryClient.invalidateQueries({
        queryKey: entityQueryKeys.all('identity'),
      }),

    // Invalidate specific identity (including its i18n)
    invalidateIdentity: (id: string) =>
      queryClient.invalidateQueries({
        queryKey: entityQueryKeys.detail('identity', id),
      }),

    // Invalidate all i18n for an identity
    invalidateIdentityI18n: (id: string) =>
      queryClient.invalidateQueries({
        queryKey: [...entityQueryKeys.detail('identity', id), 'i18n'],
      }),
  }
}
```

---

## Mutations Pattern

For mutations with cache updates:

```typescript
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { toast } from 'sonner'

export function useUpdateEntity() {
  const queryClient = useQueryClient()

  return useMutation({
    mutationFn: async (data: UpdateEntityInput) => {
      const response = await fetch(`/api/entities/${data.id}`, {
        method: 'PUT',
        body: JSON.stringify(data),
      })
      return response.json()
    },
    onSuccess: (data, variables) => {
      // Update cache directly
      queryClient.setQueryData(
        entityQueryKeys.detail('entity', variables.id),
        data
      )
      // Or invalidate to refetch
      queryClient.invalidateQueries({
        queryKey: entityQueryKeys.all('entity'),
      })
      toast.success('Entity updated')
    },
    onError: () => {
      toast.error('Failed to update entity')
    },
  })
}
```

---

## Hook Return Structure

Always return consistent object shape from all hooks:

```typescript
// Single query
export function useMyData(id: string) {
  const query = useQuery(createQueryOptions(id))

  return {
    data: query.data,
    isPending: query.isPending,
    isError: query.isError,
    error: query.error,
  }
}

// Multiple queries (data + i18n)
export function useEntityData(id: string) {
  const dataQuery = useQuery(createDataQueryOptions(id))
  const i18nQuery = useQuery(createI18nQueryOptions(id, language))

  return {
    data: dataQuery.data,
    i18n: i18nQuery.data,
    isPending: dataQuery.isPending || i18nQuery.isPending,
    isError: dataQuery.isError || i18nQuery.isError,
    error: dataQuery.error || i18nQuery.error,
  }
}
```

---

## Summary

| Aspect | useQuery (current) | useSuspenseQueries (recommended) |
|--------|-------------------|----------------------------------|
| Loading pattern | Sequential (waterfall) | Parallel |
| SSR support | No | Yes |
| Conditional fetch | Yes (`enabled`) | No |
| Loading handling | Manual `isPending` | Suspense boundary |
| Data type | `T \| undefined` | `T` (always defined) |
| Use case | Legacy code, conditional queries | New code, SSR-ready |

**For new code, prefer `useSuspenseQueries` with Suspense boundaries.**

---

## See Also

- [schemas-and-validation.md](schemas-and-validation.md) - Complete JSON → Types → Schemas → Hook pipeline
- [loading-and-error-states.md](loading-and-error-states.md) - Loading/error UI components
