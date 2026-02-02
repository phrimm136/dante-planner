---
paths:
  - "frontend/src/schemas/**/*.ts"
  - "frontend/src/hooks/**/*.ts"
---

# Data Schema & Validation Patterns

## Mandatory Rules

- **Always validate JSON** - Use Zod schemas with `.strict()`
- **Type-safe query keys** - Use factory functions
- **Separate spec from i18n** - Different staleTime, different keys

## Forbidden Pattern

| Forbidden | Use Instead |
|-----------|-------------|
| Raw `fetch` without validation | `validateData(data, Schema)` |
| Schema missing `.strict()` | Add `.strict()` to reject extras |

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

## Validation Usage

```typescript
import { validateData } from '@/lib/validation'

const data = await fetch('/data/entity.json').then(r => r.json())
const validated = validateData(data, EntityDataSchema)  // Throws if invalid
```

**Reference:** `@/lib/validation`, `IdentitySchemas.ts`, `EGOGiftSchemas.ts`
