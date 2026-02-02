---
paths:
  - "frontend/src/components/**/*.tsx"
  - "frontend/src/components/**/*.ts"
---

# Component Styling Patterns

## Rules

- **No hardcoded colors** - Use `constants.ts` or CSS variables
- **No opacity for dimming** - Let theme colors cascade
- **Use `lg:` (1024px)** - Primary mobile/desktop breakpoint

## Forbidden Patterns

| Forbidden | Use Instead |
|-----------|-------------|
| `ring-[#fcba03]` | `.selectable` CSS class |
| `hidden md:block` | `hidden lg:block` |
| `opacity-70` for text dimming | Remove color class (inherit theme) |
| `text-[#ff0000]` | `constants.ts` color constant |

## Responsive Pattern

```typescript
<div className="flex flex-col lg:flex-row gap-4">
<div className="hidden lg:block">  // Desktop only
<div className="lg:hidden">        // Mobile only
```

## Constants Usage

```typescript
import { COLORS } from '@/lib/constants'

<div className={cn("border", COLORS.border.default)}>
```

**Reference:** `@/lib/constants` (`SECTION_STYLES`, `COLORS`)
