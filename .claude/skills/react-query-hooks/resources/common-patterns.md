# Common Patterns

Reusable patterns for React Query hooks.

## Table of Contents

- [Simple Data Hook](#simple-data-hook)
- [Data with i18n](#data-with-i18n)
- [Dependent Queries](#dependent-queries)
- [With Validation](#with-validation)
- [Mutations](#mutations)

---

## Simple Data Hook

Basic hook for loading single data source:

```typescript
import { useQuery, queryOptions } from '@tanstack/react-query'

export const specQueryKeys = {
  identitySpec: () => ['spec', 'identity'] as const,
}

function createIdentitySpecQueryOptions() {
  return queryOptions({
    queryKey: specQueryKeys.identitySpec(),
    queryFn: async () => {
      const module = await import('@static/data/identitySpecList.json')
      return module.default as Record<string, IdentitySpec>
    },
    staleTime: 7 * 24 * 60 * 60 * 1000,
  })
}

export function useIdentitySpecData() {
  return useQuery(createIdentitySpecQueryOptions())
}
```

---

## Data with i18n

Load data and translations in parallel:

```typescript
export function useEntityData(id: string) {
  const { i18n } = useTranslation()

  const dataQuery = useQuery(createDataQueryOptions(id))
  const i18nQuery = useQuery(createI18nQueryOptions(id, i18n.language))

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

## Dependent Queries

Second query depends on first query success:

```typescript
export function useEntityDetailData(type: EntityType, id: string | undefined) {
  const { i18n } = useTranslation()

  // First query: Load entity data
  const dataQuery = useQuery(
    createDataQueryOptions(type, id!, !!id)
  )

  // Second query: Load i18n (dependent on data success)
  const i18nQuery = useQuery(
    createI18nQueryOptions(
      type,
      id!,
      i18n.language,
      dataQuery.isSuccess  // Only run when data is loaded
    )
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

---

## With Validation

Add Zod runtime validation:

```typescript
import { validateData, getDetailSchema } from '@/lib/validation'

function createDataQueryOptions(type: EntityType, id: string, enabled: boolean) {
  const config = ENTITY_CONFIG[type]
  return queryOptions({
    queryKey: entityQueryKeys.detail(type, id),
    queryFn: async () => {
      const module = await import(`@static/data/${config.dataPath}/${id}.json`)
      const schema = getDetailSchema(type)
      // Validate before caching
      return validateData<EntityData>(
        module.default,
        schema,
        { entityType: type, dataKind: 'detail', id }
      )
    },
    enabled,
    staleTime: config.staleTime,
  })
}
```

---

## Mutations

Pattern for mutations with cache updates:

```typescript
import { useMutation, useQueryClient } from '@tanstack/react-query'

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
      // Update cache
      queryClient.setQueryData(
        entityQueryKeys.detail('entity', variables.id),
        data
      )
      // Or invalidate
      queryClient.invalidateQueries({
        queryKey: entityQueryKeys.all('entity'),
      })
    },
  })
}
```

---

## staleTime Reference

| Data Type | Value | Constant |
|-----------|-------|----------|
| Static JSON | 7 days | `7 * 24 * 60 * 60 * 1000` |
| Semi-static | 1 day | `24 * 60 * 60 * 1000` |
| Dynamic | 5 min | `5 * 60 * 1000` |
| Real-time | 0 | `0` |

---

## Return Structure

Always return consistent shape:

```typescript
return {
  data: query.data,           // The data (or undefined)
  isPending: query.isPending, // Loading state
  isError: query.isError,     // Error state
  error: query.error,         // Error object
}
```

For multiple queries:

```typescript
return {
  data: mergedData,
  i18n: i18nQuery.data,
  isPending: dataQuery.isPending || i18nQuery.isPending,
  isError: dataQuery.isError || i18nQuery.isError,
  error: dataQuery.error || i18nQuery.error,
}
```
