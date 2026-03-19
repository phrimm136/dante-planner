---
paths:
  - "frontend/src/schemas/**/*.ts"
  - "frontend/src/hooks/**/*.ts"
---

# Data Schema & Validation Patterns

## Core Rule

Zod is the source of truth for external data. Internal data uses plain TypeScript types.

```
External (API, URL params, localStorage, user input)
  → Zod schema validates at boundary
  → z.infer<typeof Schema> produces TypeScript type
  → never duplicate with separate interface

Internal (between your own components)
  → plain TypeScript type
  → no Zod needed
```

## Mandatory Rules

- **Always validate JSON** — use Zod schemas with `.strict()`
- **Type-safe query keys** — use factory functions
- **Separate spec from i18n** — different staleTime, different keys
- **Derive schemas** — use `.omit()`, `.pick()`, `.partial()`, `.extend()` instead of duplicating
- **Define schemas at module level** — never inside functions (performance + reuse)
- **Use `safeParse()` at API boundaries** — avoid uncaught throws

## Forbidden Patterns

| Forbidden | Use Instead |
|---|---|
| Raw `fetch` without validation | `validateData(data, Schema)` |
| Schema missing `.strict()` | Add `.strict()` to reject extras |
| Parallel `interface` + Zod schema for same shape | `z.infer<typeof Schema>` only |
| `z.any()` or `z.unknown()` as workaround | Validate the actual shape |
| Schema defined inside a function | Define at module level |

## type vs interface

| Situation | Use |
|---|---|
| Component props | `type` |
| Inferred from Zod | `type` (required, `interface` can't do it) |
| Discriminated unions | `type` (interfaces can't do this) |
| Extending DOM/third-party types | `interface` |

This codebase uses `type` as default. Stay consistent.

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

## Schema Composition

```typescript
// Derive instead of duplicate
export const CreateEntitySchema = EntityDataSchema.omit({ id: true })
export const PatchEntitySchema = EntityDataSchema.partial()
export const EntitySummarySchema = EntityDataSchema.pick({ id: true, name: true })

// Generic paginated response — define once, reuse
const PaginatedSchema = <T extends z.ZodTypeAny>(itemSchema: T) =>
  z.object({
    content: z.array(itemSchema),
    totalPages: z.number(),
    totalElements: z.number(),
  })
```

## Validation Usage

```typescript
import { validateData } from '@/lib/validation'

const data = await fetch('/data/entity.json').then(r => r.json())
const validated = validateData(data, EntityDataSchema)  // Throws if invalid
```

## File Organization

```
src/schemas/IdentitySchemas.ts   # Zod schemas
src/types/IdentityTypes.ts       # z.infer<> re-exports and internal types
```

**Reference:** `@/lib/validation`, `IdentitySchemas.ts`, `EGOGiftSchemas.ts`
