# Validation Integration

How to integrate Zod schemas with data loading hooks.

## Table of Contents

- [Schema Registration](#schema-registration)
- [Validation Utilities](#validation-utilities)
- [Hook Integration](#hook-integration)
- [Error Handling](#error-handling)

---

## Schema Registration

### Add to Schema Index

Update `frontend/src/schemas/index.ts`:

```typescript
// Existing exports
export * from './SharedSchemas'
export * from './IdentitySchemas'
export * from './EGOSchemas'
export * from './EGOGiftSchemas'

// Add new entity
export * from './StartBuffSchemas'
```

### Add to Validation Utils

Update `frontend/src/lib/validation.ts`:

```typescript
import {
  // ... existing imports
  StartBuffDataSchema,
  StartBuffDataListSchema,
} from '@/schemas'

// Add to EntityType if needed
export type EntityType = 'identity' | 'ego' | 'egoGift' | 'startBuff'

// Add schema getter function
export function getStartBuffSchema(dataKind: DataKind): ZodSchema {
  const schemaMap = {
    detail: StartBuffDataSchema,
    list: StartBuffDataListSchema,
  } as const
  return schemaMap[dataKind]
}
```

---

## Validation Utilities

### Core Validation Function

```typescript
// frontend/src/lib/validation.ts
import { type ZodSchema, type ZodError } from 'zod'

export function validateData<T>(
  data: unknown,
  schema: ZodSchema,
  context: {
    entityType: EntityType
    dataKind: DataKind
    id?: string
  }
): T {
  const result = schema.safeParse(data)

  if (!result.success) {
    throw new Error(formatValidationError(result.error, context))
  }

  return result.data as T
}
```

### Error Formatting

```typescript
export function formatValidationError(
  error: ZodError<unknown>,
  context: { entityType: EntityType; dataKind: DataKind; id?: string }
): string {
  const isDev = import.meta.env.DEV
  const contextPrefix = `[${context.entityType} / ${context.dataKind}]`

  if (isDev) {
    // Detailed errors in development
    const errorDetails = error.issues
      .map(err => {
        const path = err.path.length > 0 ? err.path.join('.') : 'root'
        return `  - ${path}: ${err.message}`
      })
      .join('\n')
    return `${contextPrefix} Validation failed:\n${errorDetails}`
  }

  // Concise errors in production
  return `${contextPrefix} Invalid data: ${error.issues.length} validation errors`
}
```

---

## Hook Integration

### With useSuspenseQuery

```typescript
import { useSuspenseQuery } from '@tanstack/react-query'
import { validateData, getStartBuffSchema } from '@/lib/validation'
import type { StartBuffDataList } from '@/types/StartBuffTypes'

export function useStartBuffData() {
  return useSuspenseQuery({
    queryKey: ['startBuffs'],
    queryFn: async () => {
      const response = await fetch('/data/startBuffsMD6.json')
      const rawData = await response.json()

      // Validate at runtime
      return validateData<StartBuffDataList>(
        rawData,
        getStartBuffSchema('list'),
        { entityType: 'startBuff', dataKind: 'list' }
      )
    },
  })
}
```

### With Entity Detail Data Pattern

```typescript
// Following existing useEntityDetailData pattern
import { validateData, getDetailSchema, getI18nSchema } from '@/lib/validation'

export function useEntityDetailData(type: EntityType, id: string) {
  return useSuspenseQuery({
    queryKey: [type, 'detail', id],
    queryFn: async () => {
      // Fetch both data and i18n
      const [dataRes, i18nRes] = await Promise.all([
        fetch(`/data/${type}/${id}.json`),
        fetch(`/i18n/EN/${type}/${id}.json`),
      ])

      const [rawData, rawI18n] = await Promise.all([
        dataRes.json(),
        i18nRes.json(),
      ])

      // Validate both
      const data = validateData(rawData, getDetailSchema(type), {
        entityType: type,
        dataKind: 'detail',
        id,
      })

      const i18n = validateData(rawI18n, getI18nSchema(type), {
        entityType: type,
        dataKind: 'i18n',
        id,
      })

      return { data, i18n }
    },
  })
}
```

---

## Error Handling

### Suspense Boundary Integration

```tsx
import { ErrorBoundary } from 'react-error-boundary'
import { Suspense } from 'react'

function DataView() {
  return (
    <ErrorBoundary fallback={<ValidationErrorView />}>
      <Suspense fallback={<LoadingSpinner />}>
        <DataContent />
      </Suspense>
    </ErrorBoundary>
  )
}

function ValidationErrorView({ error }: { error: Error }) {
  // Validation errors bubble up through ErrorBoundary
  return (
    <div className="error-container">
      <h2>Data Validation Error</h2>
      <pre>{error.message}</pre>
    </div>
  )
}
```

### Optional Validation (Non-Throwing)

```typescript
// For cases where you want to handle invalid data gracefully
export function safeValidateData<T>(
  data: unknown,
  schema: ZodSchema
): { success: true; data: T } | { success: false; errors: ZodError } {
  const result = schema.safeParse(data)

  if (result.success) {
    return { success: true, data: result.data as T }
  }

  return { success: false, errors: result.error }
}
```

---

## Best Practices

1. **Validate at load time** - Catch issues early
2. **Use context in errors** - Include entity type and ID
3. **Dev vs Prod verbosity** - Detailed in dev, concise in prod
4. **ErrorBoundary integration** - Let validation errors bubble up
5. **Type assertions** - Use `as T` after validation for type safety
