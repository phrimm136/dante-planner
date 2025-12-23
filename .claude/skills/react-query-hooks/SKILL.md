---
name: react-query-hooks
description: Standards for creating React Query hooks with TanStack Query. Use when creating data fetching hooks, useQuery, useSuspenseQuery, useMutation, query keys, queryOptions, or working with react-query patterns. Covers query key factories, queryOptions pattern, staleTime configuration, i18n data loading, and TypeScript type definitions. When creating hooks that load JSON data, also use the json-types-zod-pipeline skill for creating types and validation schemas.
---

# React Query Hook Standards

## Purpose

Enforce consistent patterns for creating data fetching hooks using TanStack Query. Ensures proper query key structure, caching configuration, type safety, and integration with TypeScript types and Zod validation.

## When to Use This Skill

- Creating new data fetching hooks
- Working with `useQuery`, `useSuspenseQuery`, or `useMutation`
- Setting up query keys and caching strategies
- Loading data with i18n translations
- Configuring staleTime and other query options

## Related Skills

**When loading JSON data, also invoke:**
- `json-types-zod-pipeline` - For creating TypeScript types and Zod schemas

---

## Required Patterns

### 1. TypeScript Types First

Before creating a hook, ensure types exist for the data:

```typescript
// Types must exist in frontend/src/types/{Entity}Types.ts
import type { StartBuffData, StartBuffI18n } from '@/types/StartBuffTypes'

// If types don't exist, use json-types-zod-pipeline skill first
```

### 2. Query Key Factory

Always create a query key factory for hierarchical cache management:

```typescript
// Query key factory - REQUIRED for all hooks
export const entityQueryKeys = {
  all: (type: EntityType) => [type] as const,
  detail: (type: EntityType, id: string) => [type, id] as const,
  i18n: (type: EntityType, id: string, language: string) =>
    [type, id, 'i18n', language] as const,
}
```

### 3. queryOptions Pattern

Use `queryOptions()` to create reusable query configurations:

```typescript
import { useQuery, queryOptions } from '@tanstack/react-query'

// Create query options as a separate function
function createDataQueryOptions(id: string) {
  return queryOptions({
    queryKey: entityQueryKeys.detail('entity', id),
    queryFn: async () => {
      const module = await import(`@static/data/${id}.json`)
      return module.default as EntityData
    },
    staleTime: 7 * 24 * 60 * 60 * 1000, // 7 days for static data
  })
}

// Use in hook
export function useEntityData(id: string) {
  return useQuery(createDataQueryOptions(id))
}
```

### 4. Runtime Validation (Recommended)

For JSON data, add Zod validation:

```typescript
import { validateData, getDetailSchema } from '@/lib/validation'

function createDataQueryOptions(type: EntityType, id: string) {
  return queryOptions({
    queryKey: entityQueryKeys.detail(type, id),
    queryFn: async () => {
      const module = await import(`@static/data/${type}/${id}.json`)
      const schema = getDetailSchema(type)
      // Validate before caching
      return validateData<EntityData>(
        module.default,
        schema,
        { entityType: type, dataKind: 'detail', id }
      )
    },
    staleTime: queryConfig.staleTime[type],
  })
}
```

### 5. Hook Return Structure

Return consistent object shape from all hooks:

```typescript
export function useMyData(id: string) {
  const query = useQuery(createQueryOptions(id))

  return {
    data: query.data,
    isPending: query.isPending,
    isError: query.isError,
    error: query.error,
  }
}
```

---

## File Structure

```
frontend/src/
├── types/
│   └── {Entity}Types.ts        # TypeScript interfaces (create first)
├── schemas/
│   └── {Entity}Schemas.ts      # Zod schemas (create second)
└── hooks/
    └── use{Entity}Data.ts      # React Query hook (create last)
```

**Order**: Types → Schemas → Hooks

---

## Quick Reference

### Imports

```typescript
import { useQuery, queryOptions } from '@tanstack/react-query'
import { useTranslation } from 'react-i18next'
import type { EntityData } from '@/types/EntityTypes'
import { validateData } from '@/lib/validation'
```

### staleTime Values

| Data Type | staleTime | Notes |
|-----------|-----------|-------|
| Static JSON | 7 days | `7 * 24 * 60 * 60 * 1000` |
| Config data | From config | `queryConfig.staleTime.entity` |
| Dynamic API | 5 minutes | `5 * 60 * 1000` |

### Query Key Structure

```typescript
// Hierarchical keys enable cache invalidation
['entity']                           // All entities
['entity', id]                       // Specific entity
['entity', id, 'i18n', 'EN']        // Entity i18n
```

---

## Patterns

See reference files for detailed examples:

- [Complete Hook Example](resources/complete-example.md) - Full hook with i18n and validation
- [Query Key Patterns](resources/query-keys.md) - Key factory patterns
- [Common Patterns](resources/common-patterns.md) - Reusable patterns

---

## Checklist: New Hook

### Prerequisites
- [ ] TypeScript types exist in `types/{Entity}Types.ts`
- [ ] Zod schemas exist in `schemas/{Entity}Schemas.ts` (if validating)
- [ ] Schemas exported from `schemas/index.ts`

### Hook Implementation
- [ ] Create query key factory with `as const`
- [ ] Use `queryOptions()` for query configuration
- [ ] Add runtime validation with Zod (recommended)
- [ ] Set appropriate `staleTime` for data type
- [ ] Return consistent object `{ data, isPending, isError, error }`
- [ ] Add JSDoc comment with `@param` and `@returns`
- [ ] Export query keys for external cache access

---

## Anti-Patterns

### DON'T: Create hook without types

```typescript
// BAD - No type safety
export function useData(id: string) {
  return useQuery({
    queryKey: ['data', id],
    queryFn: async () => {
      const res = await fetch(`/api/${id}`)
      return res.json() // Unknown type!
    },
  })
}
```

### DO: Define types first, then hook

```typescript
// GOOD - Type-safe with validation
import type { EntityData } from '@/types/EntityTypes'
import { EntityDataSchema } from '@/schemas/EntitySchemas'

export function useData(id: string) {
  return useQuery({
    queryKey: dataQueryKeys.detail(id),
    queryFn: async () => {
      const module = await import(`@static/data/${id}.json`)
      return validateData<EntityData>(module.default, EntityDataSchema, {...})
    },
  })
}
```

### DON'T: Inline query configuration

```typescript
// BAD - Hard to reuse and test
export function useData(id: string) {
  return useQuery({
    queryKey: ['data', id],
    queryFn: () => fetch(`/api/${id}`),
  })
}
```

### DO: Use queryOptions pattern

```typescript
// GOOD - Reusable, testable
function createDataQueryOptions(id: string) {
  return queryOptions({
    queryKey: dataQueryKeys.detail(id),
    queryFn: () => fetch(`/api/${id}`),
    staleTime: 5 * 60 * 1000,
  })
}

export function useData(id: string) {
  return useQuery(createDataQueryOptions(id))
}
```

---

## Related Files

**Existing Hooks:**
- `frontend/src/hooks/useEntityDetailData.ts` - Reference with validation
- `frontend/src/hooks/useStartBuffData.ts` - With i18n merging
- `frontend/src/hooks/useSpecData.ts` - Simple pattern

**Types & Schemas:**
- `frontend/src/types/` - TypeScript interfaces
- `frontend/src/schemas/` - Zod validation schemas
- `frontend/src/lib/validation.ts` - Validation utilities

**Configuration:**
- `static/config/queryConfig.json` - staleTime config

---

**Skill Status**: ACTIVE
**Line Count**: < 250 (following 500-line rule)
