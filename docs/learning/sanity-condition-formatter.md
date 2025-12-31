# Sanity Condition Formatter - Educational Reference

This document explains the design decisions behind the sanity condition formatting feature.

---

## Problem Statement

Game data encodes sanity conditions as function-like strings with embedded arguments:
```
"OnKillEnemyAsLevelRatioMultiply10"
"OnWinDuelAsParryingCountMultiply10AndPlus20Percent"
```

These need to be transformed into human-readable i18n descriptions:
```
"Increase by 10 after this unit defeats an enemy..."
"Increases after winning a Clash (Base Value is 10, raised by 20%...)"
```

---

## Architecture Decision: Pure Function + Hook Split

### Why Separate Parsing from Data Loading?

| Layer | Responsibility | File |
|-------|---------------|------|
| **Pure Utility** | Parse, substitute, format | `lib/formatSanityCondition.ts` |
| **Data Hook** | Load i18n JSON, validate | `hooks/useSanityConditionData.ts` |
| **Formatter Hook** | Combine data + formatting | `lib/sanityConditionFormatter.ts` |

**Benefits:**
- Pure functions are testable without React/mocking
- Data loading is reusable independently
- Follows Single Responsibility Principle

---

## Parsing Algorithm

### Step 1: Extract Numbers
```typescript
const numberMatches = encodedName.match(/\d+/g)
// "OnWin10And20" → ["10", "20"]
```

### Step 2: Remove Numbers for Base Name
```typescript
const baseName = encodedName.replace(/\d+/g, '')
// "OnWin10And20" → "OnWinAnd"
```

### Step 3: Substitute into Template
```typescript
// Template: "Base is {0}, modifier {1}%"
// Args: [10, 20]
// Result: "Base is 10, modifier 20%"
```

---

## Why Object.hasOwn() Instead of `in` Operator?

```typescript
// Risky - doesn't narrow type properly
if (baseName in i18n) { ... }

// Safe - proper runtime check
if (!Object.hasOwn(i18n, baseName)) { return fallback }
```

**Reason:** TypeScript's `Record<string, T>` type implies all keys return `T`, but runtime access to missing keys returns `undefined`. `Object.hasOwn()` provides explicit runtime safety.

---

## Why Enum for 'inc' | 'dec'?

```typescript
// Before - magic strings scattered
formatSanityCondition(name, i18n, 'inc')

// After - centralized, type-safe
import { SANITY_CONDITION_TYPE } from '@/lib/constants'
formatSanityCondition(name, i18n, SANITY_CONDITION_TYPE.INCREMENT)
```

**Benefits:**
- Single source of truth
- IDE autocomplete
- Refactoring safety

---

## Tag Stripping: Why Not Parse to React Nodes?

The i18n templates contain markup tags:
```
"<size=95%>(Base Value is {0})</size>"
"<color=red>At {2} or higher SP,</color>"
```

### Decision: Strip Tags, Return Plain Text

**Reasons:**
1. **Simplicity** - Plain text is easier to render anywhere
2. **Consistency** - Game UI styling may not match web styling
3. **Future-proof** - Can add React node rendering later if needed

```typescript
export function stripSanityTags(text: string): string {
  let result = text.replace(/<size=[^>]*>([\s\S]*?)<\/size>/g, '$1')
  result = result.replace(/<color=[^>]*>([\s\S]*?)<\/color>/g, '$1')
  return result
}
```

---

## Performance: replaceAll vs RegExp in Loop

```typescript
// Before - creates new RegExp on each iteration
result = result.replace(new RegExp(`\\{${i}\\}`, 'g'), String(args[i]))

// After - simpler, no regex compilation
result = result.replaceAll(`{${i}}`, String(args[i]))
```

**Note:** For typical 1-3 arguments, performance difference is negligible. The change is for code clarity.

---

## Error Handling Strategy: Forgiving with Visibility

```typescript
if (!Object.hasOwn(i18n, baseName)) {
  console.warn(`[SanityCondition] Missing i18n for: ${baseName}`)
  return encodedName  // Show raw name, don't crash
}
```

**Philosophy:**
- **Don't crash** - Missing translation shouldn't break the page
- **Be visible** - Raw function name signals issue to developers
- **Log for debugging** - Console warning helps track missing entries

**Future:** Replace `console.warn` with Sentry (see docs/TODO.md ERR-001)

---

## i18n Data Structure

```json
{
  "OnKillEnemyAsLevelRatioMultiply": {
    "inc": "Increase by {0} after this unit defeats an enemy...",
    "dec": "Decrease by {0} after this unit defeats an enemy..."
  }
}
```

**Validation:** Zod schema with `.strict()` ensures no extra properties:
```typescript
export const SanityConditionEntrySchema = z.object({
  inc: z.string(),
  dec: z.string(),
}).strict()
```

---

## File Structure

```
frontend/src/
├── lib/
│   ├── formatSanityCondition.ts   # Pure parsing/formatting functions
│   ├── sanityConditionFormatter.ts # useSanityConditionFormatter hook
│   └── constants.ts                # SANITY_CONDITION_TYPE enum
├── hooks/
│   └── useSanityConditionData.ts   # Data loading hook
├── schemas/
│   └── SanityConditionSchemas.ts   # Zod validation
└── routes/
    └── IdentityDetailPage.tsx      # Consumer
```

---

## Testing Considerations

Key edge cases to test:
- Empty string input
- No numbers in function name: `"MentalConditionUnknown"`
- Multiple numbers: `"OnWin10And20And30"`
- Missing i18n entry (should fallback)
- Mismatched arg count (2 args, 3 placeholders)

---

## Related Files

- `lib/parseStyleTags.tsx` - Similar tag parsing pattern for skill descriptions
- `lib/egoGiftEncoding.ts` - Similar pure function pattern for encoding/decoding
- `hooks/useStartBuffData.ts` - Reference for i18n data loading pattern
