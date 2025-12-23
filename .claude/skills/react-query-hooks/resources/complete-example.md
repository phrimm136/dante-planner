# Complete Hook Example

Full implementation of a React Query hook with i18n and validation.

## Table of Contents

- [Step 1: Ensure Types Exist](#step-1-ensure-types-exist)
- [Step 2: Create Query Key Factory](#step-2-create-query-key-factory)
- [Step 3: Create Query Options](#step-3-create-query-options)
- [Step 4: Create the Hook](#step-4-create-the-hook)
- [Complete Example](#complete-example)

---

## Step 1: Ensure Types Exist

Before creating a hook, verify types exist. If not, use `json-types-zod-pipeline` skill.

```typescript
// frontend/src/types/StartBuffTypes.ts
export interface StartBuffData {
  level: number
  baseId: number
  cost: number
  localizeId: string
  effects: BuffEffect[]
  uiConfig: BuffUIConfig
}

export type StartBuffDataList = Record<string, StartBuffData>
export type StartBuffI18n = Record<string, string>
```

---

## Step 2: Create Query Key Factory

```typescript
// Query key factory for hierarchical cache management
export const startBuffQueryKeys = {
  all: (version: MDVersion) => ['startBuff', `md${version}`] as const,
  data: (version: MDVersion) => [...startBuffQueryKeys.all(version), 'data'] as const,
  i18n: (version: MDVersion, language: string) =>
    [...startBuffQueryKeys.all(version), 'i18n', language] as const,
}
```

**Key Points:**
- Use `as const` for type safety
- Build hierarchical keys for cache invalidation
- Export for external cache access

---

## Step 3: Create Query Options

```typescript
import { queryOptions } from '@tanstack/react-query'

// Data query options
function createDataQueryOptions(version: MDVersion) {
  return queryOptions({
    queryKey: startBuffQueryKeys.data(version),
    queryFn: async () => {
      const module = await import(`@static/data/startBuffsMD${version}.json`)
      return module.default as StartBuffDataList
    },
    staleTime: 7 * 24 * 60 * 60 * 1000, // 7 days
  })
}

// I18n query options
function createI18nQueryOptions(version: MDVersion, language: string) {
  return queryOptions({
    queryKey: startBuffQueryKeys.i18n(version, language),
    queryFn: async () => {
      const module = await import(`@static/i18n/${language}/startBuffsMD${version}.json`)
      return module.default as StartBuffI18n
    },
    staleTime: 7 * 24 * 60 * 60 * 1000, // 7 days
  })
}
```

---

## Step 4: Create the Hook

```typescript
import { useQuery } from '@tanstack/react-query'
import { useTranslation } from 'react-i18next'

/**
 * Hook that loads start buff data with i18n translations
 * @param version - Mirror Dungeon version (5 or 6)
 * @returns Merged buff data with translations
 */
export function useStartBuffData(version: MDVersion) {
  const { i18n } = useTranslation()

  const dataQuery = useQuery(createDataQueryOptions(version))
  const i18nQuery = useQuery(createI18nQueryOptions(version, i18n.language))

  // Merge data with i18n
  const mergedData = dataQuery.data && i18nQuery.data
    ? Object.entries(dataQuery.data).map(([id, buff]) => ({
        id,
        baseId: buff.baseId,
        level: buff.level,
        name: i18nQuery.data[buff.localizeId] || buff.localizeId,
        cost: buff.cost,
        effects: buff.effects,
        iconSpriteId: buff.uiConfig.iconSpriteId,
      } as StartBuff))
    : undefined

  return {
    data: mergedData,
    i18n: i18nQuery.data,
    isPending: dataQuery.isPending || i18nQuery.isPending,
    isError: dataQuery.isError || i18nQuery.isError,
    error: dataQuery.error || i18nQuery.error,
  }
}
```

---

## Complete Example

```typescript
// frontend/src/hooks/useStartBuffData.ts
import { useQuery, queryOptions } from '@tanstack/react-query'
import { useTranslation } from 'react-i18next'
import type { StartBuffDataList, StartBuffI18n, StartBuff } from '@/types/StartBuffTypes'

export type MDVersion = 5 | 6

// Query key factory
export const startBuffQueryKeys = {
  all: (version: MDVersion) => ['startBuff', `md${version}`] as const,
  data: (version: MDVersion) => [...startBuffQueryKeys.all(version), 'data'] as const,
  i18n: (version: MDVersion, language: string) =>
    [...startBuffQueryKeys.all(version), 'i18n', language] as const,
}

// Query options
function createDataQueryOptions(version: MDVersion) {
  return queryOptions({
    queryKey: startBuffQueryKeys.data(version),
    queryFn: async () => {
      const module = await import(`@static/data/startBuffsMD${version}.json`)
      return module.default as StartBuffDataList
    },
    staleTime: 7 * 24 * 60 * 60 * 1000,
  })
}

function createI18nQueryOptions(version: MDVersion, language: string) {
  return queryOptions({
    queryKey: startBuffQueryKeys.i18n(version, language),
    queryFn: async () => {
      const module = await import(`@static/i18n/${language}/startBuffsMD${version}.json`)
      return module.default as StartBuffI18n
    },
    staleTime: 7 * 24 * 60 * 60 * 1000,
  })
}

/**
 * Hook that loads start buff data with i18n translations
 * @param version - Mirror Dungeon version (5 or 6)
 * @returns Merged buff data with translations
 */
export function useStartBuffData(version: MDVersion) {
  const { i18n } = useTranslation()

  const dataQuery = useQuery(createDataQueryOptions(version))
  const i18nQuery = useQuery(createI18nQueryOptions(version, i18n.language))

  const mergedData = dataQuery.data && i18nQuery.data
    ? Object.entries(dataQuery.data).map(([id, buff]) => ({
        id,
        baseId: buff.baseId,
        level: buff.level,
        name: i18nQuery.data[buff.localizeId] || buff.localizeId,
        cost: buff.cost,
        effects: buff.effects,
        iconSpriteId: buff.uiConfig.iconSpriteId,
      } as StartBuff))
    : undefined

  return {
    data: mergedData,
    i18n: i18nQuery.data,
    isPending: dataQuery.isPending || i18nQuery.isPending,
    isError: dataQuery.isError || i18nQuery.isError,
    error: dataQuery.error || i18nQuery.error,
  }
}
```

---

## With Runtime Validation

Add Zod validation for additional safety:

```typescript
import { validateData } from '@/lib/validation'
import { StartBuffDataListSchema } from '@/schemas/StartBuffSchemas'

function createDataQueryOptions(version: MDVersion) {
  return queryOptions({
    queryKey: startBuffQueryKeys.data(version),
    queryFn: async () => {
      const module = await import(`@static/data/startBuffsMD${version}.json`)
      // Validate before caching
      return validateData<StartBuffDataList>(
        module.default,
        StartBuffDataListSchema,
        { entityType: 'startBuff', dataKind: 'list' }
      )
    },
    staleTime: 7 * 24 * 60 * 60 * 1000,
  })
}
```
