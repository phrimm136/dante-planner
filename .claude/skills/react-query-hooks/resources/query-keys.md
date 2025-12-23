# Query Key Patterns

Query key factory patterns for hierarchical cache management.

## Table of Contents

- [Why Query Key Factories](#why-query-key-factories)
- [Basic Pattern](#basic-pattern)
- [Hierarchical Keys](#hierarchical-keys)
- [With Parameters](#with-parameters)
- [Cache Invalidation](#cache-invalidation)

---

## Why Query Key Factories

Query key factories provide:
- **Type safety** - `as const` ensures correct types
- **Consistency** - Single source of truth for keys
- **Cache control** - Hierarchical invalidation
- **Refactoring** - Change keys in one place

---

## Basic Pattern

```typescript
// Simple factory
export const userQueryKeys = {
  all: () => ['users'] as const,
  detail: (id: string) => ['users', id] as const,
}

// Usage
useQuery({
  queryKey: userQueryKeys.detail('123'),
  queryFn: () => fetchUser('123'),
})
```

---

## Hierarchical Keys

Build keys that enable granular cache invalidation:

```typescript
export const entityQueryKeys = {
  // Level 1: All entities of a type
  all: (type: EntityType) => [type] as const,

  // Level 2: Specific entity
  detail: (type: EntityType, id: string) =>
    [...entityQueryKeys.all(type), id] as const,

  // Level 3: Entity with language
  i18n: (type: EntityType, id: string, lang: string) =>
    [...entityQueryKeys.detail(type, id), 'i18n', lang] as const,
}
```

**Key hierarchy:**
```
['identity']                      // All identities
['identity', '10101']             // Identity 10101
['identity', '10101', 'i18n', 'EN'] // Identity 10101 EN translation
```

---

## With Parameters

```typescript
// Version-based keys
export const startBuffQueryKeys = {
  all: (version: MDVersion) =>
    ['startBuff', `md${version}`] as const,

  data: (version: MDVersion) =>
    [...startBuffQueryKeys.all(version), 'data'] as const,

  i18n: (version: MDVersion, language: string) =>
    [...startBuffQueryKeys.all(version), 'i18n', language] as const,
}

// Filter-based keys
export const searchQueryKeys = {
  all: () => ['search'] as const,

  results: (filters: SearchFilters) =>
    [...searchQueryKeys.all(), 'results', filters] as const,
}
```

---

## Cache Invalidation

Use hierarchical keys for targeted invalidation:

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

    // Invalidate specific identity
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

## Best Practices

1. **Always use `as const`** - Ensures tuple types, not arrays
2. **Build hierarchically** - Child keys extend parent keys
3. **Export factories** - Allow external cache access
4. **Use descriptive segments** - `'i18n'`, `'data'`, `'list'`
5. **Include all dependencies** - Keys should contain all variables that affect the query
