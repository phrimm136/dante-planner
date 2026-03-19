---
paths:
  - "frontend/src/**/*.ts"
  - "frontend/src/**/*.tsx"
---

# Comment Standards

## Good Comments

A comment is justified only if it serves one of these purposes:

| Category | Purpose | Example |
|----------|---------|---------|
| Why | Intent, decision, or constraint | `// Parse style tags before keywords to preserve boundaries` |
| Warning | Dangerous or surprising behavior | `// CRITICAL: Prevents 1000ms delay per test` |
| Workaround | Intentional hack that shouldn't be "fixed" away | `// matchAll to avoid global regex state issues` |
| Cross-ref | Related code, type definition, or external doc | `// @see StartBuffTypes.ts for StartBuff definition` |
| TODO | Actionable item with tracker link | `// TODO: #142 — Cloudflare adapter` |
| Clarification | Non-obvious nullability, type coercion, or invariant | `// Returns null when puzzle is complete` |

## Forbidden Comments

| Pattern | Example | Why |
|---------|---------|-----|
| Restates code | `// Filter the list` above `.filter(...)` | Code is self-documenting |
| Change history | `(moved to right column)`, `(added for mobile)` | Git tracks changes |
| Tombstone | `// Old component removed` | Git tracks deletions |
| Commented-out code | `// <OldComponent />` | Delete it; git has it |
| Redundant negation | `(NEVER use npm)` when `Yarn` is already stated | Positive choice is sufficient |
| Parenthetical restatement | `usePlannerStore (manages planner state)` | LLM hedging noise; the name says it |

## JSDoc

### When to write

| Target | Required | Skip |
|--------|----------|------|
| Exported function with non-obvious behavior | Yes | Name + types make it obvious |
| Interface/type with domain meaning | Yes | Wrapper types, single-field |
| Module file header | Yes, if encoding/algorithm | Simple re-export barrels |
| Hook | Yes, if custom logic | Thin wrappers around `useSuspenseQuery` |

### Format

**Module header** — describe purpose, encoding pattern, or algorithm:

```typescript
/**
 * Sanity Condition Formatter
 *
 * Parses encoded sanity condition function names and formats them
 * into human-readable i18n descriptions.
 *
 * Encoding pattern:
 * - Raw: "OnKillEnemyAsLevelRatioMultiply10"
 * - Parsed: { baseName: "OnKillEnemyAsLevelRatioMultiply", args: [10] }
 */
```

**Function** — description, `@param`, `@returns`, `@example`:

```typescript
/**
 * Parses an encoded sanity condition function name into base name and arguments.
 *
 * @param encodedName - Raw function name like "OnKillEnemyAsLevelRatioMultiply10"
 * @returns Parsed result with baseName and args array
 *
 * @example
 * parseSanityCondition("OnKillEnemyAsLevelRatioMultiply10")
 * // => { baseName: "OnKillEnemyAsLevelRatioMultiply", args: [10] }
 */
```

**Interface field** — single-line `/** */` for domain meaning:

```typescript
export interface ParsedSanityCondition {
  /** Base function name without numeric arguments */
  baseName: string
  /** Extracted numeric arguments in order */
  args: number[]
}
```

### Rules

- Description: what it does and why
- `@param` / `@returns`: only when type signature alone is ambiguous
- `@example`: for non-trivial transformations, parsing, or encoding
- No `@author`, `@version`, `@since` — git tracks these
- No empty tag descriptions (`@param x` with no text)

## LLM Anti-Pattern: Compulsive Parentheticals

LLMs add parenthetical micro-explanations to every noun:

```typescript
// BAD — parenthetical restates what the code/name already says
const data = useIdentityListData() // (fetches identity list from API)
<Suspense fallback={<Loading />}> // (shows loading while fetching)

// BAD — parenthetical adds changelog
// Sanity content (moved to right column, also used in mobile tabs)

// GOOD — standalone comment explains WHY, not WHAT
// Also rendered in mobile tabs — keep in sync
const sanityContent = ...
```

If you catch yourself writing `noun (explanation)` in a comment, delete the parenthetical. If the explanation is important enough to keep, promote it to a standalone comment.
